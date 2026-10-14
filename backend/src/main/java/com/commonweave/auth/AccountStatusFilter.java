package com.commonweave.auth;

import com.commonweave.common.ApiErrorWriter;
import com.commonweave.common.ErrorCode;
import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks the signed-in account on every request: disabled accounts lose their session immediately and
 * a changed platform role takes effect without a new login.
 */
@Component
public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ApiErrorWriter errors;

    public AccountStatusFilter(UserRepository users, ApiErrorWriter errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    private static void endSession(HttpServletRequest req) {
        if (req.getSession(false) != null) {
            req.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails principal)) {
            chain.doFilter(req, res);
            return;
        }
        Optional<User> user = users.findById(principal.id());
        if (user.isEmpty() || !user.get().isEnabled()) {
            endSession(req);
            errors.write(req, res, ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
            return;
        }
        if (user.get().getSessionVersion() != principal.sessionVersion()) {
            endSession(req);
            errors.write(req, res, ErrorCode.UNAUTHENTICATED, "Your session has ended, please sign in again");
            return;
        }
        if (user.get().isAdmin() != principal.admin()) {
            AppUserDetails fresh = new AppUserDetails(principal.id(), principal.getUsername(), null,
                    user.get().isAdmin(), user.get().getSessionVersion());
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null,
                    fresh.getAuthorities()));
        }
        chain.doFilter(req, res);
    }
}
