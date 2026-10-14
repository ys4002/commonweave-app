package com.commonweave.listing;

import com.commonweave.circle.CircleAccess;
import com.commonweave.common.ApiException;
import com.commonweave.common.PageResponse;
import com.commonweave.handoff.HandoffService;
import com.commonweave.listing.ListingDtos.ListingDetail;
import com.commonweave.listing.ListingDtos.ListingRequest;
import com.commonweave.listing.ListingDtos.ListingSummary;
import com.commonweave.user.PlatformGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingService {
    private static final int MAX_TAGS = 8;

    private final ListingRepository listings;
    private final ListingAccess access;
    private final CircleAccess circles;
    private final ListingMapper mapper;
    private final HandoffService handoffs;
    private final Clock clock;
    private final PlatformGuard platformGuard;

    public ListingService(ListingRepository listings, ListingAccess access, CircleAccess circles,
                          ListingMapper mapper, HandoffService handoffs, Clock clock, PlatformGuard platformGuard) {
        this.listings = listings;
        this.access = access;
        this.circles = circles;
        this.mapper = mapper;
        this.handoffs = handoffs;
        this.clock = clock;
        this.platformGuard = platformGuard;
    }

    public record SearchParams(Long circleId, ListingType type, Category category, ListingKind kind, String q,
                               LocalDate availableOn, ListingStatus status, boolean mine, String sort,
                               Integer page, Integer size) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummary> search(Long userId, SearchParams p) {
        List<ListingStatus> statuses = p.status() != null ? List.of(p.status())
                : p.mine() ? List.of(ListingStatus.values()) : List.of(ListingStatus.ACTIVE, ListingStatus.RESERVED);
        ListingSpecs.Filter filter = new ListingSpecs.Filter(userId, circles.activeCircleIds(userId),
                circles.moderatedCircleIds(userId), p.circleId(), p.type(), p.category(), p.kind(), p.q(),
                p.availableOn(), statuses, p.mine());
        Sort sort = "soonest".equals(p.sort())
                ? Sort.by(Sort.Order.asc("availableFrom"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageResponse.pageable(p.page(), p.size(), sort);
        Page<Listing> page = listings.findAll(ListingSpecs.of(filter), pageable);
        return PageResponse.of(page, mapper::summaries);
    }

    /** Platform admin view: every circle and status, no membership filter. Caller must be authorised. */
    @Transactional(readOnly = true)
    public PageResponse<ListingSummary> adminSearch(ListingStatus status, String q, Integer page, Integer size) {
        Pageable pageable = PageResponse.pageable(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(listings.findAll(ListingSpecs.admin(status, q), pageable), mapper::summaries);
    }

    @Transactional(readOnly = true)
    public ListingDetail get(Long userId, Long id) {
        return mapper.detail(access.requireVisible(id, userId), userId);
    }

    @Transactional
    public ListingDetail create(Long userId, ListingRequest req) {
        platformGuard.forbidAdmins(userId);
        if (req.circleId() == null) {
            throw ApiException.invalid("circleId", "must not be null");
        }
        circles.requireActiveMember(req.circleId(), userId);
        Listing l = new Listing();
        l.setCircleId(req.circleId());
        l.setOwnerId(userId);
        l.setStatus(ListingStatus.ACTIVE);
        Instant now = clock.instant();
        l.setCreatedAt(now);
        apply(l, req, now);
        return mapper.detail(listings.save(l), userId);
    }

    @Transactional
    public ListingDetail update(Long userId, Long id, ListingRequest req) {
        Listing l = ownedListing(userId, id);
        requireEditable(l, "edited");
        apply(l, req, clock.instant());
        return mapper.detail(listings.save(l), userId);
    }

    @Transactional
    public void close(Long userId, Long id) {
        Listing l = ownedListing(userId, id);
        requireEditable(l, "closed");
        l.setStatus(ListingStatus.CLOSED);
        l.setUpdatedAt(clock.instant());
        listings.save(l);
        handoffs.cancelOpenForListing(l, userId, false, "Listing was closed by its owner");
    }

    private Listing ownedListing(Long userId, Long id) {
        Listing l = listings.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Listing"));
        if (!access.canSee(l, userId)) {
            throw ApiException.notFound("Listing");
        }
        if (!l.getOwnerId().equals(userId)) {
            throw ApiException.forbidden("Only the owner can change this listing");
        }
        if (!circles.isActiveMember(l.getCircleId(), userId)) {
            throw ApiException.forbidden("Only active members of the circle can change its listings");
        }
        return l;
    }

    private void requireEditable(Listing l, String verb) {
        if (l.getStatus() != ListingStatus.ACTIVE) {
            throw ApiException.conflict("A listing can only be " + verb + " while it is active (status: "
                    + l.getStatus() + ")");
        }
    }

    private void apply(Listing l, ListingRequest r, Instant now) {
        if (r.availableTo().isBefore(r.availableFrom())) {
            throw ApiException.invalid("availableTo", "must not be before availableFrom");
        }
        l.setType(r.type());
        l.setKind(r.kind());
        l.setTitle(r.title().trim());
        l.setDescription(r.description() == null ? null : r.description().trim());
        l.setCategory(r.category());
        l.setTags(joinTags(r.tags()));
        l.setAvailableFrom(r.availableFrom());
        l.setAvailableTo(r.availableTo());
        l.setPickupPreference(r.pickupPreference() == null ? null : r.pickupPreference().trim());
        l.setEstimatedValueInr(orZero(r.estimatedValueInr()));
        l.setDepositInr(orZero(r.depositInr()));
        l.setHoursEstimate(orZero(r.hoursEstimate()));
        l.setReturnExpected(r.kind() == ListingKind.ITEM && Boolean.TRUE.equals(r.returnExpected()));
        l.setUpdatedAt(now);
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    static String joinTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        Set<String> clean = tags.stream().map(t -> t.replace(",", " ").trim().toLowerCase())
                .filter(t -> !t.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        return clean.stream().limit(MAX_TAGS).collect(Collectors.joining(","));
    }
}
