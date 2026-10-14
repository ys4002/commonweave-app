package com.commonweave.user;

import com.commonweave.circle.MemberRole;
import java.util.List;

public record UserResponse(Long id, String email, String displayName, String bio, String locality,
                           boolean onboarded, PlatformRole platformRole, List<CircleRef> circles) {
    public record CircleRef(Long circleId, String name, MemberRole role) {
    }
}
