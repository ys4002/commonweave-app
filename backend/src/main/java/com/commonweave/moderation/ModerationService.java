package com.commonweave.moderation;

import com.commonweave.circle.CircleAccess;
import com.commonweave.circle.MembershipRepository;
import com.commonweave.common.ApiException;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffService;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingAccess;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.moderation.ModerationDtos.CreateReportRequest;
import com.commonweave.moderation.ModerationDtos.LogEntry;
import com.commonweave.moderation.ModerationDtos.Resolution;
import com.commonweave.moderation.ModerationDtos.ReportView;
import com.commonweave.moderation.ModerationDtos.ResolveRequest;
import com.commonweave.notification.NotificationService;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {
    private static final int LOG_LIMIT = 200;

    private final ReportRepository reports;
    private final ModerationActionRepository actions;
    private final ListingRepository listings;
    private final ListingAccess listingAccess;
    private final MembershipRepository memberships;
    private final HandoffRepository handoffs;
    private final HandoffService handoffService;
    private final CircleAccess access;
    private final UserLookup users;
    private final NotificationService notifications;
    private final Clock clock;

    public ModerationService(ReportRepository reports, ModerationActionRepository actions, ListingRepository listings,
                             ListingAccess listingAccess, MembershipRepository memberships,
                             HandoffRepository handoffs, HandoffService handoffService, CircleAccess access,
                             UserLookup users, NotificationService notifications, Clock clock) {
        this.reports = reports;
        this.actions = actions;
        this.listings = listings;
        this.listingAccess = listingAccess;
        this.memberships = memberships;
        this.handoffs = handoffs;
        this.handoffService = handoffService;
        this.access = access;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public void createReport(Long userId, CreateReportRequest req) {
        access.requireActiveMember(req.circleId(), userId);
        validateTarget(userId, req);
        Report r = new Report();
        r.setCircleId(req.circleId());
        r.setReporterId(userId);
        r.setTargetType(req.targetType());
        r.setTargetId(req.targetId());
        r.setReason(req.reason().trim());
        r.setStatus(ReportStatus.OPEN);
        r.setCreatedAt(clock.instant());
        reports.save(r);
    }

    @Transactional(readOnly = true)
    public List<ReportView> list(Long userId, Long circleId, ReportStatus status) {
        access.requireModerator(circleId, userId);
        List<Report> found = status == null ? reports.findByCircleIdOrderByIdDesc(circleId)
                : reports.findByCircleIdAndStatusOrderByIdDesc(circleId, status);
        return toViews(found);
    }

    @Transactional
    public void resolve(Long modId, Long circleId, Long reportId, ResolveRequest req) {
        access.requireModerator(circleId, modId);
        Report r = reports.findById(reportId).filter(x -> x.getCircleId().equals(circleId))
                .orElseThrow(() -> ApiException.notFound("Report"));
        if (r.getStatus() != ReportStatus.OPEN) {
            throw ApiException.conflict("This report has already been closed");
        }
        boolean dismiss = req.resolution() == Resolution.DISMISSED;
        if (Boolean.TRUE.equals(req.removeListing())) {
            if (dismiss || r.getTargetType() != ReportTargetType.LISTING) {
                throw ApiException.invalid("removeListing", "only valid when resolving a listing report");
            }
            removeListing(modId, circleId, r.getTargetId(), req.note());
        }
        Instant now = clock.instant();
        r.setStatus(dismiss ? ReportStatus.DISMISSED : ReportStatus.RESOLVED);
        r.setResolutionNote(req.note());
        r.setResolvedBy(modId);
        r.setResolvedAt(now);
        log(circleId, modId, dismiss ? "REPORT_DISMISSED" : "REPORT_RESOLVED", r.getTargetType().name(),
                r.getTargetId(), req.note());
    }

    /** Shared by moderators and platform admins; callers authorise before calling. */
    @Transactional
    public void removeListing(Long actorId, Long circleId, Long listingId, String note) {
        Listing l = listings.findByIdForUpdate(listingId).filter(x -> x.getCircleId().equals(circleId))
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (l.getStatus() == ListingStatus.REMOVED) {
            throw ApiException.conflict("This listing has already been removed");
        }
        if (handoffs.existsByListingIdAndStatusIn(l.getId(), List.of(HandoffStatus.HANDED_OFF))) {
            throw ApiException.conflict("This listing has an item currently handed over; wait until it is returned");
        }
        l.setStatus(ListingStatus.REMOVED);
        l.setUpdatedAt(clock.instant());
        handoffService.cancelOpenForListing(l, actorId, true, "Listing removed by a moderator");
        notifications.notify(l.getOwnerId(), "LISTING_REMOVED", "Listing removed: " + l.getTitle(),
                note == null || note.isBlank() ? "A moderator removed your listing." : note, "/listings/" + l.getId());
    }

    @Transactional
    public void removeListingAsAdmin(Long adminId, Long listingId, String note) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        removeListing(adminId, l.getCircleId(), listingId, note);
        log(l.getCircleId(), adminId, "LISTING_REMOVED", "LISTING", listingId, "Platform admin: " + note);
    }

    @Transactional
    public void removeListingAsModerator(Long modId, Long circleId, Long listingId, String note) {
        access.requireModerator(circleId, modId);
        removeListing(modId, circleId, listingId, note);
        log(circleId, modId, "LISTING_REMOVED", "LISTING", listingId, note);
    }

    @Transactional(readOnly = true)
    public List<LogEntry> log(Long userId, Long circleId) {
        access.requireModerator(circleId, userId);
        List<ModerationAction> found = actions.findByCircleIdOrderByIdDesc(circleId, PageRequest.of(0, LOG_LIMIT));
        Map<Long, User> people = users.byIds(found.stream().map(ModerationAction::getModeratorId).toList());
        return found.stream().map(a -> new LogEntry(a.getActionType(), a.getTargetType(), a.getTargetId(),
                a.getNote(), UserLookup.ref(people, a.getModeratorId()), a.getCreatedAt())).toList();
    }

    public List<ReportView> toViews(List<Report> found) {
        Map<Long, User> people = users.byIds(found.stream().map(Report::getReporterId).toList());
        List<ReportView> out = new ArrayList<>();
        for (Report r : found) {
            out.add(new ReportView(r.getId(), r.getTargetType(), r.getTargetId(), label(r),
                    UserLookup.ref(people, r.getReporterId()), r.getStatus(), r.getCreatedAt(), r.getReason()));
        }
        return out;
    }

    private void log(Long circleId, Long modId, String type, String targetType, Long targetId, String note) {
        actions.save(new ModerationAction(circleId, modId, type, targetType, targetId, note, clock.instant()));
    }

    private void validateTarget(Long userId, CreateReportRequest req) {
        if (req.targetType() == ReportTargetType.LISTING) {
            Listing l = listingAccess.requireVisible(req.targetId(), userId);
            if (!l.getCircleId().equals(req.circleId())) {
                throw ApiException.notFound("Listing");
            }
        } else if (memberships.findByCircleIdAndUserId(req.circleId(), req.targetId()).isEmpty()) {
            throw ApiException.notFound("Member");
        }
    }

    private String label(Report r) {
        if (r.getTargetType() == ReportTargetType.LISTING) {
            return listings.findById(r.getTargetId()).map(Listing::getTitle).orElse("Deleted listing");
        }
        return users.byIds(List.of(r.getTargetId())).values().stream().findFirst()
                .map(User::getDisplayName).orElse("Former member");
    }
}
