package com.commonweave.auth;

import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import com.commonweave.user.UserResponse;
import com.commonweave.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final AuthThrottle throttle;
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();

    public AuthService(UserRepository users, UserService userService, PasswordEncoder encoder,
                       AuthenticationManager authManager, AuthThrottle throttle) {
        this.users = users;
        this.userService = userService;
        this.encoder = encoder;
        this.authManager = authManager;
        this.throttle = throttle;
    }

    public UserResponse signup(AuthDtos.SignupRequest req, HttpServletRequest http, HttpServletResponse res) {
        throttle.checkSignup(http.getRemoteAddr());
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw emailTaken();
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        startSession(email, req.password(), http, res);
        return userService.toResponse(user);
    }

    public UserResponse login(AuthDtos.LoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        String email = normalize(req.email());
        String ip = http.getRemoteAddr();
        throttle.checkLogin(email, ip);
        try {
            long id = startSession(email, req.password(), http, res);
            throttle.clearLogin(email, ip);
            return userService.get(id);
        } catch (AuthenticationException e) {
            throttle.recordLoginFailure(email, ip);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid email or password");
        }
    }

    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private long startSession(String email, String password, HttpServletRequest http, HttpServletResponse res) {
        Authentication auth = authManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        long id = ((AppUserDetails) auth.getPrincipal()).id();
        recordLogin(id);
        if (http.getSession(false) != null) {
            http.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepo.saveContext(context, http, res);
        return id;
    }

    private void recordLogin(long id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isEnabled()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static ApiException emailTaken() {
        return new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
    }
}
