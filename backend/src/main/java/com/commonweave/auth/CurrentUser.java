package com.commonweave.auth;

import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Optional<Long> idIfPresent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserDetails u) {
            return Optional.of(u.id());
        }
        return Optional.empty();
    }

    public static long id() {
        return idIfPresent().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in required"));
    }
}
