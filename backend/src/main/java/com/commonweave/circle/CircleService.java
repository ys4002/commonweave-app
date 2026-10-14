package com.commonweave.circle;

import com.commonweave.circle.CircleDtos.ActivityItem;
import com.commonweave.circle.CircleDtos.CircleDetail;
import com.commonweave.circle.CircleDtos.CircleSummary;
import com.commonweave.circle.CircleDtos.CreateCircleRequest;
import com.commonweave.circle.CircleDtos.MemberResponse;
import com.commonweave.circle.CircleDtos.ModeratorRef;
import com.commonweave.circle.CircleDtos.UpdateCircleRequest;
import com.commonweave.circle.CircleDtos.UpdateMemberRequest;
import com.commonweave.common.ApiException;
import com.commonweave.common.PageResponse;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStats;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.moderation.ModerationAction;
import com.commonweave.moderation.ModerationActionRepository;
import com.commonweave.user.PlatformGuard;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CircleService {
    private final CircleRepository circles;
    private final MembershipRepository memberships;
    private final ListingRepository listings;
    private final HandoffRepository handoffs;
    private final HandoffStats stats;
    private final CircleAccess access;
    private final UserLookup users;
    private final ActivityService activity;
    private final ModerationActionRepository modActions;
    private final Clock clock;
    private final PlatformGuard platformGuard;

    public CircleService(CircleRepository circles, MembershipRepository memberships, ListingRepository listings,
                         HandoffRepository handoffs, HandoffStats stats, CircleAccess access, UserLookup users,
                         ActivityService activity, ModerationActionRepository modActions, Clock clock,
                         PlatformGuard platformGuard) {
        this.circles = circles;
        this.memberships = memberships;
        this.listings = listings;
        this.handoffs = handoffs;
        this.stats = stats;
        this.access = access;
        this.users = users;
        this.activity = activity;
        this.modActions = modActions;
        this.clock = clock;
        this.platformGuard = platformGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<CircleSummary> list(Long userId, String q, String city, Integer page, Integer size) {
        Page<Circle> result = circles.findAll(search(q, city),
                PageResponse.pageable(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return PageResponse.of(result, items -> summaries(items, userId));
    }

    /** Platform admin view: all circles with member and listing counts (myRole is null for non-members). */
    @Transactional(readOnly = true)
    public PageResponse<CircleSummary> adminList(Long adminId, String q, Integer page, Integer size) {
        return list(adminId, q, null, page, size);
    }

    @Transactional(readOnly = true)
    public CircleDetail get(Long userId, Long id) {
        return detail(access.requireCircle(id), userId);
    }

    @Transactional
    public CircleDetail create(Long userId, CreateCircleRequest req) {
        platformGuard.forbidAdmins(userId);
        Circle c = new Circle();
        c.setName(req.name().trim());
        c.setSlug(uniqueSlug(req.name()));
        c.setCity(req.city().trim());
        c.setArea(req.area() == null ? "" : req.area().trim());
        c.setDescription(req.description());
        c.setGuidelines(req.guidelines());
        c.setCreatedAt(clock.instant());
        circles.save(c);
        memberships.save(new Membership(c.getId(), userId, MemberRole.MODERATOR));
        return detail(c, userId);
    }

    @Transactional
    public CircleDetail join(Long userId, Long id) {
        platformGuard.forbidAdmins(userId);
        Circle c = access.requireCircle(id);
        Optional<Membership> existing = memberships.findByCircleIdAndUserId(id, userId);
        if (existing.isPresent() && !existing.get().isActive()) {
            throw ApiException.forbidden("You cannot join this circle");
        }
        if (existing.isEmpty()) {
            Membership m = new Membership(id, userId, MemberRole.MEMBER);
            m.setJoinedAt(clock.instant());
            memberships.save(m);
        }
        return detail(c, userId);
    }

    @Transactional
    public void leave(Long userId, Long id) {
        Membership m = memberships.findByCircleIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Membership"));
        if (!m.isActive()) {
            throw ApiException.forbidden("Your membership is not active");
        }
        if (m.isActiveModerator()
                && memberships.countByCircleIdAndRoleAndStatus(id, MemberRole.MODERATOR, MemberStatus.ACTIVE) <= 1) {
            throw ApiException.conflict("The last moderator cannot leave; promote another member first");
        }
        if (handoffs.existsMineInCircle(id, userId, List.of(HandoffStatus.SCHEDULED, HandoffStatus.HANDED_OFF))) {
            throw ApiException.conflict("Finish or cancel your scheduled handoffs before leaving this circle");
        }
        Instant now = clock.instant();
        for (Listing l : listings.findByOwnerIdAndCircleIdAndStatus(userId, id, ListingStatus.ACTIVE)) {
            l.setStatus(ListingStatus.CLOSED);
            l.setUpdatedAt(now);
        }
        memberships.delete(m);
    }

    @Transactional
    public CircleDetail update(Long userId, Long id, UpdateCircleRequest req) {
        access.requireModerator(id, userId);
        Circle c = access.requireCircle(id);
        if (req.description() != null) {
            c.setDescription(req.description());
        }
        if (req.guidelines() != null) {
            c.setGuidelines(req.guidelines());
        }
        return detail(c, userId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(Long userId, Long id) {
        boolean moderator = access.requireActiveMember(id, userId).isActiveModerator();
        List<Membership> all = moderator ? memberships.findByCircleId(id)
                : memberships.findByCircleIdAndStatus(id, MemberStatus.ACTIVE);
        Map<Long, User> people = users.byIds(all.stream().map(Membership::getUserId).toList());
        Map<Long, Long> counts = stats.completedExchanges(people.keySet());
        return all.stream().map(m -> toMember(m, people, counts))
                .sorted(Comparator.comparing(MemberResponse::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional
    public MemberResponse updateMember(Long userId, Long circleId, Long targetUserId, UpdateMemberRequest req) {
        access.requireModerator(circleId, userId);
        Membership target = memberships.findByCircleIdAndUserId(circleId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        MemberRole newRole = req.role() != null ? req.role() : target.getRole();
        MemberStatus newStatus = req.status() != null ? req.status() : target.getStatus();
        boolean losesModeration = target.isActiveModerator()
                && (newRole != MemberRole.MODERATOR || newStatus != MemberStatus.ACTIVE);
        if (losesModeration && memberships.countByCircleIdAndRoleAndStatus(
                circleId, MemberRole.MODERATOR, MemberStatus.ACTIVE) <= 1) {
            throw ApiException.conflict("The last moderator cannot be demoted or removed");
        }
        if (newRole != target.getRole()) {
            logAction(circleId, userId, "MEMBER_ROLE_CHANGED", targetUserId, "Role set to " + newRole);
        }
        if (newStatus != target.getStatus()) {
            logAction(circleId, userId, "MEMBER_STATUS_CHANGED", targetUserId, "Status set to " + newStatus);
        }
        target.setRole(newRole);
        target.setStatus(newStatus);
        Map<Long, User> people = users.byIds(List.of(targetUserId));
        return toMember(target, people, stats.completedExchanges(List.of(targetUserId)));
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> activity(Long userId, Long circleId, int size) {
        access.requireActiveMember(circleId, userId);
        return activity.recent(List.of(circleId), Math.max(1, Math.min(size, 50))).stream()
                .map(ActivityService.Entry::item).toList();
    }

    CircleDetail detail(Circle c, Long userId) {
        CircleSummary summary = summaries(List.of(c), userId).get(0);
        List<Long> modIds = memberships.findByCircleIdAndRoleAndStatus(c.getId(), MemberRole.MODERATOR,
                MemberStatus.ACTIVE).stream().map(Membership::getUserId).toList();
        Map<Long, User> people = users.byIds(modIds);
        List<ModeratorRef> mods = modIds.stream().filter(people::containsKey)
                .map(i -> new ModeratorRef(i, people.get(i).getDisplayName())).toList();
        MemberStatus myStatus = memberships.findByCircleIdAndUserId(c.getId(), userId)
                .map(Membership::getStatus).orElse(null);
        return new CircleDetail(summary, c.getGuidelines(), mods, myStatus);
    }

    private List<CircleSummary> summaries(List<Circle> items, Long userId) {
        List<Long> ids = items.stream().map(Circle::getId).toList();
        Map<Long, Long> members = counts(ids.isEmpty() ? List.of()
                : memberships.countByCircles(ids, MemberStatus.ACTIVE));
        Map<Long, Long> activeListings = counts(ids.isEmpty() ? List.of()
                : listings.countByCircles(ids, ListingStatus.ACTIVE));
        Map<Long, Membership> mine = new HashMap<>();
        memberships.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).forEach(m -> mine.put(m.getCircleId(), m));
        return items.stream().map(c -> new CircleSummary(c.getId(), c.getSlug(), c.getName(), c.getCity(),
                c.getArea(), c.getDescription(), members.getOrDefault(c.getId(), 0L),
                activeListings.getOrDefault(c.getId(), 0L),
                mine.containsKey(c.getId()) ? mine.get(c.getId()).getRole() : null)).toList();
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private MemberResponse toMember(Membership m, Map<Long, User> people, Map<Long, Long> counts) {
        User u = people.get(m.getUserId());
        return new MemberResponse(m.getUserId(), u == null ? "Former member" : u.getDisplayName(), m.getRole(),
                m.getStatus(), m.getJoinedAt(), counts.getOrDefault(m.getUserId(), 0L));
    }

    private void logAction(Long circleId, Long moderatorId, String type, Long targetUserId, String note) {
        modActions.save(new ModerationAction(circleId, moderatorId, type, "MEMBER", targetUserId, note,
                clock.instant()));
    }

    private static Specification<Circle> search(String q, String city) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String p = "%" + escape(q.trim().toLowerCase()) + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("name")), p, '\\'),
                        cb.like(cb.lower(root.get("area")), p, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), p, '\\')));
            }
            if (city != null && !city.isBlank()) {
                all.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String uniqueSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        base = base.isEmpty() ? "circle" : base.substring(0, Math.min(base.length(), 100));
        String slug = base;
        for (int i = 2; circles.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }
}
