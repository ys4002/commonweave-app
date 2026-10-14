package com.commonweave.user;

import com.commonweave.circle.Circle;
import com.commonweave.circle.CircleRepository;
import com.commonweave.circle.MemberStatus;
import com.commonweave.circle.Membership;
import com.commonweave.circle.MembershipRepository;
import com.commonweave.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final CircleRepository circles;

    public UserService(UserRepository users, MembershipRepository memberships, CircleRepository circles) {
        this.users = users;
        this.memberships = memberships;
        this.circles = circles;
    }

    @Transactional(readOnly = true)
    public UserResponse get(long id) {
        return toResponse(users.findById(id).orElseThrow(() -> ApiException.notFound("User")));
    }

    @Transactional
    public UserResponse update(long id, UpdateProfileRequest req) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (req.displayName() != null) {
            if (req.displayName().isBlank()) {
                throw ApiException.invalid("displayName", "must not be blank");
            }
            user.setDisplayName(req.displayName().trim());
        }
        if (req.bio() != null) {
            user.setBio(req.bio().trim());
        }
        if (req.locality() != null) {
            user.setLocality(req.locality().trim());
        }
        if (req.onboarded() != null) {
            user.setOnboarded(req.onboarded());
        }
        return toResponse(users.save(user));
    }

    public UserResponse toResponse(User u) {
        List<Membership> mine = memberships.findByUserIdAndStatus(u.getId(), MemberStatus.ACTIVE);
        Map<Long, Circle> byId = circles.findAllById(mine.stream().map(Membership::getCircleId).toList())
                .stream().collect(Collectors.toMap(Circle::getId, Function.identity()));
        List<UserResponse.CircleRef> refs = mine.stream()
                .filter(m -> byId.containsKey(m.getCircleId()))
                .map(m -> new UserResponse.CircleRef(m.getCircleId(), byId.get(m.getCircleId()).getName(), m.getRole()))
                .toList();
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getBio(), u.getLocality(),
                u.isOnboarded(), u.getPlatformRole(), refs);
    }
}
