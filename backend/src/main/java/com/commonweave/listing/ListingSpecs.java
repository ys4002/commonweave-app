package com.commonweave.listing;

import com.commonweave.circle.MemberStatus;
import com.commonweave.circle.Membership;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** Builds the listing search query; visibility rules live here so no caller can forget them. */
final class ListingSpecs {
    private ListingSpecs() {
    }

    record Filter(Long viewerId, Set<Long> activeCircles, Set<Long> moderatedCircles, Long circleId,
                  ListingType type, Category category, ListingKind kind, String q, LocalDate availableOn,
                  List<ListingStatus> statuses, boolean mine) {
    }

    static Specification<Listing> of(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(visible(f, root, query, cb));
            if (f.circleId() != null) {
                all.add(cb.equal(root.get("circleId"), f.circleId()));
            }
            if (f.type() != null) {
                all.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.category() != null) {
                all.add(cb.equal(root.get("category"), f.category()));
            }
            if (f.kind() != null) {
                all.add(cb.equal(root.get("kind"), f.kind()));
            }
            if (f.mine()) {
                all.add(cb.equal(root.get("ownerId"), f.viewerId()));
            }
            if (f.availableOn() != null) {
                all.add(cb.lessThanOrEqualTo(root.get("availableFrom"), f.availableOn()));
                all.add(cb.greaterThanOrEqualTo(root.get("availableTo"), f.availableOn()));
            }
            all.add(root.get("status").in(f.statuses()));
            if (f.q() != null && !f.q().isBlank()) {
                all.add(textMatch(f.q().trim().toLowerCase(), root, cb));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    static Specification<Listing> admin(ListingStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                all.add(textMatch(q.trim().toLowerCase(), root, cb));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static Predicate visible(Filter f, jakarta.persistence.criteria.Root<Listing> root,
                                     jakarta.persistence.criteria.CriteriaQuery<?> query,
                                     jakarta.persistence.criteria.CriteriaBuilder cb) {
        Predicate own = cb.equal(root.get("ownerId"), f.viewerId());
        if (f.activeCircles().isEmpty()) {
            return own;
        }
        Predicate inCircle = root.get("circleId").in(f.activeCircles());
        Predicate notRemoved = cb.notEqual(root.get("status"), ListingStatus.REMOVED);
        Predicate removedButMod = f.moderatedCircles().isEmpty()
                ? cb.disjunction() : root.get("circleId").in(f.moderatedCircles());
        return cb.or(own, cb.and(inCircle, ownerIsActiveMember(root, query, cb), cb.or(notRemoved, removedButMod)));
    }

    /** Listings of suspended, removed or departed members are hidden from everyone else in the circle. */
    private static Predicate ownerIsActiveMember(jakarta.persistence.criteria.Root<Listing> root,
                                                 jakarta.persistence.criteria.CriteriaQuery<?> query,
                                                 jakarta.persistence.criteria.CriteriaBuilder cb) {
        jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
        jakarta.persistence.criteria.Root<Membership> m = sub.from(Membership.class);
        sub.select(m.<Long>get("id")).where(cb.equal(m.get("circleId"), root.get("circleId")),
                cb.equal(m.get("userId"), root.get("ownerId")), cb.equal(m.get("status"), MemberStatus.ACTIVE));
        return cb.exists(sub);
    }

    private static Predicate textMatch(String q, jakarta.persistence.criteria.Root<Listing> root,
                                       jakarta.persistence.criteria.CriteriaBuilder cb) {
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(root.<String>get("tags"), "")), pattern, '\\'));
    }
}
