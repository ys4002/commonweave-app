package com.commonweave.circle;

import com.commonweave.common.ApiException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Central membership/role checks so every controller authorises the same way. */
@Component
public class CircleAccess {
    private final MembershipRepository memberships;
    private final CircleRepository circles;

    public CircleAccess(MembershipRepository memberships, CircleRepository circles) {
        this.memberships = memberships;
        this.circles = circles;
    }

    public boolean isActiveMember(Long circleId, Long userId) {
        return memberships.findByCircleIdAndUserId(circleId, userId).filter(Membership::isActive).isPresent();
    }

    public boolean isModerator(Long circleId, Long userId) {
        return memberships.findByCircleIdAndUserId(circleId, userId).filter(Membership::isActiveModerator).isPresent();
    }

    public Circle requireCircle(Long circleId) {
        return circles.findById(circleId).orElseThrow(() -> ApiException.notFound("Circle"));
    }

    public Membership requireActiveMember(Long circleId, Long userId) {
        requireCircle(circleId);
        return memberships.findByCircleIdAndUserId(circleId, userId).filter(Membership::isActive)
                .orElseThrow(() -> ApiException.forbidden("Only members of this circle can do this"));
    }

    public Membership requireModerator(Long circleId, Long userId) {
        requireCircle(circleId);
        return memberships.findByCircleIdAndUserId(circleId, userId).filter(Membership::isActiveModerator)
                .orElseThrow(() -> ApiException.forbidden("Only circle moderators can do this"));
    }

    public Set<Long> activeCircleIds(Long userId) {
        return memberships.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).stream()
                .map(Membership::getCircleId).collect(Collectors.toSet());
    }

    public Set<Long> moderatedCircleIds(Long userId) {
        return memberships.findByUserIdAndStatusAndRole(userId, MemberStatus.ACTIVE, MemberRole.MODERATOR).stream()
                .map(Membership::getCircleId).collect(Collectors.toSet());
    }
}
