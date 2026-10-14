package com.commonweave.auth;

import com.commonweave.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window throttle: failed logins per (identifier, IP) and signup attempts per IP.
 * State is per instance and lost on restart, which is acceptable for a single-node deployment.
 * Logs only the client IP, never identifiers or passwords.
 */
@Component
public class AuthThrottle {
    private static final Logger log = LoggerFactory.getLogger(AuthThrottle.class);
    private static final int PURGE_ABOVE = 10_000;

    private final SecurityProperties props;
    private final Clock clock;
    private final Map<String, Deque<Instant>> loginFailures = new HashMap<>();
    private final Map<String, Deque<Instant>> signups = new HashMap<>();

    public AuthThrottle(SecurityProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Throws 429 while the (identifier, IP) pair has used up its failed-login budget. */
    public synchronized void checkLogin(String identifier, String ip) {
        Deque<Instant> window = recent(loginFailures, loginKey(identifier, ip));
        if (window.size() >= props.loginMaxFailures()) {
            throw reject("Too many failed sign-in attempts. Please try again later.", window, ip, "login");
        }
    }

    public synchronized void recordLoginFailure(String identifier, String ip) {
        recent(loginFailures, loginKey(identifier, ip)).addLast(clock.instant());
    }

    public synchronized void clearLogin(String identifier, String ip) {
        loginFailures.remove(loginKey(identifier, ip));
    }

    /** Counts a signup attempt for this IP and throws 429 once the budget is exceeded. */
    public synchronized void checkSignup(String ip) {
        Deque<Instant> window = recent(signups, ip);
        if (window.size() >= props.signupMaxAttempts()) {
            throw reject("Too many sign-up attempts. Please try again later.", window, ip, "signup");
        }
        window.addLast(clock.instant());
    }

    private ApiException reject(String message, Deque<Instant> window, String ip, String kind) {
        Instant now = clock.instant();
        long retry = Math.max(1, Duration.between(now, window.peekFirst().plus(props.throttleWindow())).toSeconds());
        log.warn("Throttled {} from {}", kind, ip);
        return ApiException.tooManyRequests(message, retry);
    }

    private Deque<Instant> recent(Map<String, Deque<Instant>> store, String key) {
        Instant cutoff = clock.instant().minus(props.throttleWindow());
        if (store.size() > PURGE_ABOVE) {
            store.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(cutoff));
        }
        Deque<Instant> window = store.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.pollFirst();
        }
        return window;
    }

    private static String loginKey(String identifier, String ip) {
        return identifier.trim().toLowerCase() + "|" + ip;
    }
}
