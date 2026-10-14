package com.commonweave.handoff;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class HandoffSpecs {
    static final List<HandoffStatus> ACTIVE = List.of(HandoffStatus.PROPOSED, HandoffStatus.SCHEDULED,
            HandoffStatus.HANDED_OFF);

    private HandoffSpecs() {
    }

    static Specification<Handoff> mine(Long userId, HandoffStatus status, boolean activeOnly, HandoffRole role) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (role == HandoffRole.PROVIDER) {
                all.add(cb.equal(root.get("providerId"), userId));
            } else if (role == HandoffRole.RECIPIENT) {
                all.add(cb.equal(root.get("recipientId"), userId));
            } else {
                all.add(cb.or(cb.equal(root.get("providerId"), userId), cb.equal(root.get("recipientId"), userId)));
            }
            if (status != null) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (activeOnly) {
                all.add(root.get("status").in(ACTIVE));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
