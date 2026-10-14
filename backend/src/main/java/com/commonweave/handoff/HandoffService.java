package com.commonweave.handoff;

import com.commonweave.circle.MembershipRepository;
import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import com.commonweave.common.PageResponse;
import com.commonweave.common.TimeConfig;
import com.commonweave.handoff.HandoffDtos.HandoffDetail;
import com.commonweave.handoff.HandoffDtos.HandoffSummary;
import com.commonweave.handoff.HandoffDtos.ProposeRequest;
import com.commonweave.handoff.HandoffDtos.TransitionRequest;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.listing.ListingType;
import com.commonweave.notification.NotificationService;
import com.commonweave.user.PlatformGuard;
import com.commonweave.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoffService {
    private static final List<HandoffStatus> OPEN = List.of(HandoffStatus.PROPOSED, HandoffStatus.SCHEDULED,
            HandoffStatus.HANDED_OFF);
    private static final List<HandoffStatus> RESERVING = List.of(HandoffStatus.SCHEDULED, HandoffStatus.HANDED_OFF);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(TimeConfig.DISPLAY_ZONE);

    private final HandoffRepository handoffs;
    private final HandoffEventRepository events;
    private final ListingRepository listings;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final HandoffMapper mapper;
    private final NotificationService notifications;
    private final Clock clock;
    private final PlatformGuard platformGuard;

    public HandoffService(HandoffRepository handoffs, HandoffEventRepository events, ListingRepository listings,
                          MembershipRepository memberships, UserRepository users, HandoffMapper mapper,
                          NotificationService notifications, Clock clock, PlatformGuard platformGuard) {
        this.handoffs = handoffs;
        this.events = events;
        this.listings = listings;
        this.memberships = memberships;
        this.users = users;
        this.mapper = mapper;
        this.notifications = notifications;
        this.clock = clock;
        this.platformGuard = platformGuard;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HandoffDetail propose(Long userId, ProposeRequest req) {
        platformGuard.forbidAdmins(userId);
        Listing l = listings.findByIdForUpdate(req.listingId()).orElseThrow(() -> ApiException.notFound("Listing"));
        requireActiveMemberForAction(l.getCircleId(), userId);
        if (l.getOwnerId().equals(userId)) {
            throw ApiException.conflict("You cannot propose a handoff on your own listing");
        }
        if (!memberships.findByCircleIdAndUserId(l.getCircleId(), l.getOwnerId()).filter(m -> m.isActive()).isPresent()) {
            throw ApiException.notFound("Listing");
        }
        if (l.getStatus() != ListingStatus.ACTIVE) {
            throw new ApiException(ErrorCode.LISTING_UNAVAILABLE, "This listing is not available");
        }
        if (handoffs.existsByListingIdAndProposerIdAndStatusIn(l.getId(), userId, OPEN)) {
            throw ApiException.conflict("You already have an open proposal for this listing");
        }
        Instant now = clock.instant();
        Handoff h = newHandoff(l, userId, req.message(), now);
        handoffs.save(h);
        events.save(new HandoffEvent(h.getId(), userId, null, HandoffStatus.PROPOSED, req.message(), now));
        notifications.notify(l.getOwnerId(), "HANDOFF_PROPOSED", "New proposal: " + l.getTitle(),
                nameOf(userId) + " would like to arrange a handoff.", link(h));
        return mapper.detail(h, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<HandoffSummary> list(Long userId, HandoffStatus status, boolean activeOnly,
                                             HandoffRole role, Integer page, Integer size) {
        Page<Handoff> result = handoffs.findAll(HandoffSpecs.mine(userId, status, activeOnly, role),
                PageResponse.pageable(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return PageResponse.of(result, items -> mapper.summaries(items, userId));
    }

    @Transactional(readOnly = true)
    public HandoffDetail get(Long userId, Long id) {
        Handoff h = handoffs.findById(id).filter(x -> x.involves(userId))
                .orElseThrow(() -> ApiException.notFound("Handoff"));
        return mapper.detail(h, userId);
    }

    /**
     * Lock order is always handoff row, then listing row, so concurrent confirms for the same listing queue up
     * on the listing lock and the loser sees RESERVED. READ_COMMITTED keeps every read current after the wait.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HandoffDetail transition(Long userId, Long id, TransitionRequest req) {
        Handoff h = handoffs.findByIdForUpdate(id).filter(x -> x.involves(userId))
                .orElseThrow(() -> ApiException.notFound("Handoff"));
        HandoffStatus from = h.getStatus();
        HandoffStatus to = HandoffStateMachine.next(from, req.action(), h.isReturnExpected());
        if (req.action() == TransitionAction.CONFIRM) {
            requireCanConfirm(h, userId, req);
        }
        Instant now = clock.instant();
        Listing listing = touchesListing(req.action(), from)
                ? listings.findByIdForUpdate(h.getListingId()).orElseThrow(() -> ApiException.notFound("Listing"))
                : null;
        if (req.action() == TransitionAction.CONFIRM) {
            ensureAvailable(listing);
            h.setScheduledAt(req.scheduledAt());
            h.setPickupNote(req.pickupNote());
            h.setReturnBy(req.returnBy());
        }
        h.setStatus(to);
        h.setUpdatedAt(now);
        applyListingEffect(listing, req.action(), now);
        flushOrUnavailable(h);
        events.save(new HandoffEvent(h.getId(), userId, from, to, req.note(), now));
        notifyCounterpart(h, userId, to, titleOf(h));
        return mapper.detail(h, userId);
    }

    /** Cancels open proposals (and scheduled handoffs when asked) on a listing that is going away. */
    @Transactional
    public void cancelOpenForListing(Listing listing, Long actorId, boolean includeScheduled, String note) {
        List<HandoffStatus> targets = includeScheduled
                ? List.of(HandoffStatus.PROPOSED, HandoffStatus.SCHEDULED) : List.of(HandoffStatus.PROPOSED);
        Instant now = clock.instant();
        for (Handoff h : handoffs.findByListingIdAndStatusIn(listing.getId(), targets)) {
            HandoffStatus from = h.getStatus();
            h.setStatus(HandoffStatus.CANCELLED);
            h.setUpdatedAt(now);
            events.save(new HandoffEvent(h.getId(), actorId, from, HandoffStatus.CANCELLED, note, now));
            for (Long party : List.of(h.getProviderId(), h.getRecipientId())) {
                if (!party.equals(actorId)) {
                    notifications.notify(party, "HANDOFF_CANCELLED", "Handoff cancelled: " + listing.getTitle(),
                            note, link(h));
                }
            }
        }
    }

    public boolean hasHandedOff(Long listingId) {
        return handoffs.existsByListingIdAndStatusIn(listingId, List.of(HandoffStatus.HANDED_OFF));
    }

    private Handoff newHandoff(Listing l, Long proposerId, String message, Instant now) {
        boolean offer = l.getType() == ListingType.OFFER;
        Handoff h = new Handoff();
        h.setListingId(l.getId());
        h.setCircleId(l.getCircleId());
        h.setProviderId(offer ? l.getOwnerId() : proposerId);
        h.setRecipientId(offer ? proposerId : l.getOwnerId());
        h.setProposerId(proposerId);
        h.setStatus(HandoffStatus.PROPOSED);
        h.setMessage(message == null || message.isBlank() ? null : message.trim());
        h.setReturnExpected(l.isReturnExpected());
        h.setCreatedAt(now);
        h.setUpdatedAt(now);
        return h;
    }

    private void requireActiveMemberForAction(Long circleId, Long userId) {
        var membership = memberships.findByCircleIdAndUserId(circleId, userId)
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (!membership.isActive()) {
            throw ApiException.forbidden("Your membership in this circle is not active");
        }
    }

    private void requireCanConfirm(Handoff h, Long userId, TransitionRequest req) {
        if (!h.ownerId().equals(userId)) {
            throw ApiException.forbidden("Only the listing owner can confirm a handoff");
        }
        requireActiveMemberForAction(h.getCircleId(), userId);
        if (req.scheduledAt() == null) {
            throw ApiException.invalid("scheduledAt", "is required to confirm a handoff");
        }
        if (!req.scheduledAt().isAfter(clock.instant())) {
            throw ApiException.invalid("scheduledAt", "must be in the future");
        }
        if (req.returnBy() != null && !req.returnBy().isAfter(req.scheduledAt())) {
            throw ApiException.invalid("returnBy", "must be after scheduledAt");
        }
    }

    private void ensureAvailable(Listing listing) {
        if (listing.getStatus() != ListingStatus.ACTIVE
                || handoffs.existsByListingIdAndStatusIn(listing.getId(), RESERVING)) {
            throw new ApiException(ErrorCode.LISTING_UNAVAILABLE, "This listing is no longer available");
        }
    }

    private static boolean touchesListing(TransitionAction action, HandoffStatus from) {
        return switch (action) {
            case CONFIRM, MARK_RETURNED, COMPLETE -> true;
            case CANCEL -> from == HandoffStatus.SCHEDULED;
            case MARK_HANDED_OFF -> false;
        };
    }

    private void applyListingEffect(Listing listing, TransitionAction action, Instant now) {
        if (listing == null) {
            return;
        }
        switch (action) {
            case CONFIRM -> listing.setStatus(ListingStatus.RESERVED);
            case COMPLETE -> listing.setStatus(ListingStatus.CLOSED);
            default -> {
                if (listing.getStatus() == ListingStatus.RESERVED) {
                    listing.setStatus(ListingStatus.ACTIVE);
                }
            }
        }
        listing.setUpdatedAt(now);
    }

    private void flushOrUnavailable(Handoff h) {
        try {
            handoffs.saveAndFlush(h);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.LISTING_UNAVAILABLE, "This listing is no longer available");
        }
    }

    private void notifyCounterpart(Handoff h, Long actorId, HandoffStatus to, String listingTitle) {
        String actor = nameOf(actorId);
        String when = h.getScheduledAt() == null ? "" : " for " + WHEN.format(h.getScheduledAt());
        String body = switch (to) {
            case SCHEDULED -> actor + " confirmed the handoff" + when + ".";
            case HANDED_OFF -> actor + " marked the item as handed over.";
            case RETURNED -> actor + " marked the item as returned.";
            case COMPLETED -> actor + " marked the exchange as complete.";
            case CANCELLED -> actor + " cancelled the handoff.";
            default -> actor + " updated the handoff.";
        };
        notifications.notify(h.counterpartOf(actorId), "HANDOFF_" + to.name(), humanTitle(to) + ": " + listingTitle,
                body, link(h));
    }

    private static String humanTitle(HandoffStatus to) {
        return switch (to) {
            case SCHEDULED -> "Handoff scheduled";
            case HANDED_OFF -> "Handed over";
            case RETURNED -> "Returned";
            case COMPLETED -> "Exchange completed";
            case CANCELLED -> "Handoff cancelled";
            default -> "Handoff update";
        };
    }

    private String nameOf(Long userId) {
        return users.findById(userId).map(u -> u.getDisplayName()).orElse("Someone");
    }

    private String titleOf(Handoff h) {
        return listings.findById(h.getListingId()).map(Listing::getTitle).orElse("your listing");
    }

    private static String link(Handoff h) {
        return "/handoffs/" + h.getId();
    }
}
