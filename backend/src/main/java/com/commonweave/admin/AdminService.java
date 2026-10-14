package com.commonweave.admin;

import com.commonweave.admin.AdminDtos.AdminCircleRef;
import com.commonweave.admin.AdminDtos.AdminHandoff;
import com.commonweave.admin.AdminDtos.AdminReport;
import com.commonweave.admin.AdminDtos.AdminUser;
import com.commonweave.admin.AdminDtos.AdminUserDetail;
import com.commonweave.admin.AdminDtos.AuditEntry;
import com.commonweave.admin.AdminDtos.Overview;
import com.commonweave.admin.AdminDtos.UpdateUserRequest;
import com.commonweave.admin.AdminDtos.UserCounts;
import com.commonweave.circle.Circle;
import com.commonweave.circle.CircleRepository;
import com.commonweave.circle.CircleService;
import com.commonweave.circle.Membership;
import com.commonweave.circle.MembershipRepository;
import com.commonweave.common.ApiException;
import com.commonweave.common.PageResponse;
import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffDtos.ListingRef;
import com.commonweave.handoff.HandoffMapper;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStats;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingMapper;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.moderation.ModerationService;
import com.commonweave.moderation.Report;
import com.commonweave.moderation.ReportRepository;
import com.commonweave.moderation.ReportStatus;
import com.commonweave.user.PlatformRole;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import com.commonweave.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform administration. Every write records an admin_actions row; password hashes are never returned. */
@Service
public class AdminService {
    private static final int RECENT = 10;

    private final UserRepository users;
    private final CircleRepository circles;
    private final MembershipRepository memberships;
    private final ListingRepository listings;
    private final HandoffRepository handoffs;
    private final ReportRepository reports;
    private final AdminActionRepository audit;
    private final HandoffStats stats;
    private final ListingMapper listingMapper;
    private final HandoffMapper handoffMapper;
    private final ModerationService moderation;
    private final CircleService circleService;
    private final UserLookup lookup;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AdminService(UserRepository users, CircleRepository circles, MembershipRepository memberships,
                        ListingRepository listings, HandoffRepository handoffs, ReportRepository reports,
                        AdminActionRepository audit, HandoffStats stats, ListingMapper listingMapper,
                        HandoffMapper handoffMapper, ModerationService moderation, CircleService circleService, UserLookup lookup,
                        PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.circles = circles;
        this.memberships = memberships;
        this.listings = listings;
        this.handoffs = handoffs;
        this.reports = reports;
        this.audit = audit;
        this.stats = stats;
        this.listingMapper = listingMapper;
        this.handoffMapper = handoffMapper;
        this.moderation = moderation;
        this.circleService = circleService;
        this.lookup = lookup;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Map<String, Long> listingCounts = new LinkedHashMap<>();
        Arrays.stream(ListingStatus.values()).forEach(s -> listingCounts.put(s.name(), listings.countByStatus(s)));
        Map<String, Long> handoffCounts = new LinkedHashMap<>();
        Arrays.stream(HandoffStatus.values()).forEach(s -> handoffCounts.put(s.name(), handoffs.countByStatus(s)));
        return new Overview(new UserCounts(users.count(), users.countByEnabled(false),
                users.countByPlatformRole(PlatformRole.ADMIN)), circles.count(), listingCounts, handoffCounts,
                reports.countByStatus(ReportStatus.OPEN));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUser> listUsers(String q, Boolean enabled, Integer page, Integer size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String p = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("email")), p, '\\'),
                        cb.like(cb.lower(root.get("displayName")), p, '\\')));
            }
            if (enabled != null) {
                all.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        var result = users.findAll(spec, PageResponse.pageable(page, size,
                Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("id"))));
        return PageResponse.of(result, this::toAdminUsers);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail userDetail(Long id) {
        User u = find(id);
        List<Listing> recent = listings.findByOwnerIdOrderByCreatedAtDesc(id, PageRequest.of(0, RECENT));
        List<Handoff> recentHandoffs = handoffs.findByProviderIdOrRecipientIdOrderByUpdatedAtDesc(
                id, id, PageRequest.of(0, RECENT));
        return new AdminUserDetail(toAdminUsers(List.of(u)).get(0), u.getBio(), listingMapper.summaries(recent),
                handoffMapper.summaries(recentHandoffs, id));
    }

    @Transactional
    public AdminUser updateUser(Long adminId, Long id, UpdateUserRequest req) {
        boolean mayRemoveAdmin = Boolean.FALSE.equals(req.enabled()) || req.platformRole() == PlatformRole.USER;
        // Lock all enabled admin rows before counting so two concurrent demotions cannot both succeed.
        List<User> admins = mayRemoveAdmin ? users.lockEnabledByRole(PlatformRole.ADMIN) : List.of();
        User u = find(id);
        boolean self = adminId.equals(id);
        boolean loseAdmin = u.isAdmin() && u.isEnabled()
                && (Boolean.FALSE.equals(req.enabled()) || req.platformRole() == PlatformRole.USER);
        if (loseAdmin && self) {
            throw ApiException.conflict("You cannot disable or demote your own admin account");
        }
        if (loseAdmin && admins.size() <= 1) {
            throw ApiException.conflict("The last administrator cannot be disabled or demoted");
        }
        if (self && Boolean.FALSE.equals(req.enabled())) {
            throw ApiException.conflict("You cannot disable your own account");
        }
        if (req.enabled() != null && req.enabled() != u.isEnabled()) {
            u.setEnabled(req.enabled());
            u.setSessionVersion(u.getSessionVersion() + 1);
            record(adminId, req.enabled() ? "USER_ENABLED" : "USER_DISABLED", id, null);
        }
        if (req.platformRole() != null && req.platformRole() != u.getPlatformRole()) {
            u.setPlatformRole(req.platformRole());
            u.setSessionVersion(u.getSessionVersion() + 1);
            record(adminId, "USER_ROLE_CHANGED", id, "Platform role set to " + req.platformRole());
        }
        if (req.displayName() != null && !req.displayName().isBlank()
                && !req.displayName().trim().equals(u.getDisplayName())) {
            u.setDisplayName(req.displayName().trim());
            record(adminId, "USER_RENAMED", id, null);
        }
        users.save(u);
        return toAdminUsers(List.of(u)).get(0);
    }

    @Transactional
    public void resetPassword(Long adminId, Long id, String newPassword) {
        User u = find(id);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setSessionVersion(u.getSessionVersion() + 1);
        users.save(u);
        record(adminId, "PASSWORD_RESET", id, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<com.commonweave.circle.CircleDtos.CircleSummary> listCircles(Long adminId, String q,
                                                                                    Integer page, Integer size) {
        return circleService.adminList(adminId, q, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminHandoff> listHandoffs(HandoffStatus status, Integer page, Integer size) {
        Specification<Handoff> spec = (root, query, cb) -> status == null ? cb.conjunction()
                : cb.equal(root.get("status"), status);
        var result = handoffs.findAll(spec, PageResponse.pageable(page, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result, this::toAdminHandoffs);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReport> listReports(ReportStatus status, Integer page, Integer size) {
        Specification<Report> spec = (root, query, cb) -> status == null ? cb.conjunction()
                : cb.equal(root.get("status"), status);
        var result = reports.findAll(spec, PageResponse.pageable(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result, items -> {
            var views = moderation.toViews(items);
            List<AdminReport> out = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                out.add(new AdminReport(views.get(i), items.get(i).getCircleId()));
            }
            return out;
        });
    }

    @Transactional
    public void removeListing(Long adminId, Long listingId, String note) {
        moderation.removeListingAsAdmin(adminId, listingId, note == null ? "" : note);
        record(adminId, "LISTING_REMOVED", "LISTING", listingId, note);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> auditLog(Integer page, Integer size) {
        var result = audit.findAll(PageResponse.pageable(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result, items -> {
            Map<Long, User> admins = lookup.byIds(items.stream().map(AdminAction::getAdminId).toList());
            return items.stream().map(a -> new AuditEntry(a.getAction(), a.getTargetType(), a.getTargetId(),
                    a.getNote(), UserLookup.ref(admins, a.getAdminId()), a.getCreatedAt())).toList();
        });
    }

    private List<AdminUser> toAdminUsers(List<User> items) {
        List<Long> ids = items.stream().map(User::getId).toList();
        List<Membership> allMemberships = ids.isEmpty() ? List.of() : memberships.findByUserIdIn(ids);
        Map<Long, Circle> circleMap = circles.findAllById(
                allMemberships.stream().map(Membership::getCircleId).distinct().toList())
                .stream().collect(Collectors.toMap(Circle::getId, c -> c));
        Map<Long, Long> listingCounts = listings.countByOwners(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Long> exchanges = stats.completedExchanges(ids);
        return items.stream().map(u -> new AdminUser(u.getId(), u.getEmail(), u.getDisplayName(), u.getLocality(),
                u.getPlatformRole(), u.isEnabled(), u.isOnboarded(), u.getCreatedAt(), u.getLastLoginAt(),
                allMemberships.stream().filter(m -> m.getUserId().equals(u.getId()) && circleMap.containsKey(m.getCircleId()))
                        .map(m -> new AdminCircleRef(m.getCircleId(), circleMap.get(m.getCircleId()).getName(),
                                m.getRole(), m.getStatus())).toList(),
                listingCounts.getOrDefault(u.getId(), 0L), exchanges.getOrDefault(u.getId(), 0L))).toList();
    }

    private List<AdminHandoff> toAdminHandoffs(List<Handoff> items) {
        Map<Long, Listing> listingMap = listings.findAllById(items.stream().map(Handoff::getListingId).distinct().toList())
                .stream().collect(Collectors.toMap(Listing::getId, l -> l));
        Collection<Long> ids = items.stream().flatMap(h -> java.util.stream.Stream.of(h.getProviderId(),
                h.getRecipientId())).toList();
        Map<Long, User> people = lookup.byIds(ids);
        return items.stream().map(h -> {
            Listing l = listingMap.get(h.getListingId());
            return new AdminHandoff(h.getId(), h.getStatus(),
                    new ListingRef(l.getId(), l.getTitle(), l.getCategory(), l.getType()), h.getCircleId(),
                    UserLookup.ref(people, h.getProviderId()), UserLookup.ref(people, h.getRecipientId()),
                    h.getScheduledAt(), h.getReturnBy(), h.isReturnExpected(), h.getUpdatedAt());
        }).toList();
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private void record(Long adminId, String action, Long targetId, String note) {
        record(adminId, action, "USER", targetId, note);
    }

    private void record(Long adminId, String action, String targetType, Long targetId, String note) {
        audit.save(new AdminAction(adminId, action, targetType, targetId, note, clock.instant()));
    }
}
