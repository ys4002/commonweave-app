package com.commonweave.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.common.ApiException;
import com.commonweave.common.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthThrottleTest {
    /** Mutable clock so the window logic is tested without sleeping. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-14T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final AuthThrottle throttle = new AuthThrottle(
            new SecurityProperties(true, 3, Duration.ofMinutes(15), 2), clock);

    @Test
    void blocksAfterMaxFailuresAndReportsRetryAfter() {
        for (int i = 0; i < 3; i++) {
            throttle.checkLogin("A@x.com", "1.1.1.1");
            throttle.recordLoginFailure("a@x.com", "1.1.1.1");
        }
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        ApiException e = assertThrows(ApiException.class, () -> throttle.checkLogin("a@x.com", "1.1.1.1"));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, e.code());
        assertEquals(600L, e.retryAfterSeconds());
    }

    @Test
    void windowSlidesAndKeysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            throttle.recordLoginFailure("a@x.com", "1.1.1.1");
        }
        assertDoesNotThrow(() -> throttle.checkLogin("a@x.com", "2.2.2.2"));
        assertDoesNotThrow(() -> throttle.checkLogin("b@x.com", "1.1.1.1"));
        clock.now = clock.now.plus(Duration.ofMinutes(15)).plusSeconds(1);
        assertDoesNotThrow(() -> throttle.checkLogin("a@x.com", "1.1.1.1"));
    }

    @Test
    void successClearsFailures() {
        for (int i = 0; i < 3; i++) {
            throttle.recordLoginFailure("a@x.com", "1.1.1.1");
        }
        throttle.clearLogin("a@x.com", "1.1.1.1");
        assertDoesNotThrow(() -> throttle.checkLogin("a@x.com", "1.1.1.1"));
    }

    @Test
    void signupBudgetIsPerIp() {
        throttle.checkSignup("1.1.1.1");
        throttle.checkSignup("1.1.1.1");
        ApiException e = assertThrows(ApiException.class, () -> throttle.checkSignup("1.1.1.1"));
        assertTrue(e.retryAfterSeconds() > 0);
        assertDoesNotThrow(() -> throttle.checkSignup("3.3.3.3"));
    }
}
