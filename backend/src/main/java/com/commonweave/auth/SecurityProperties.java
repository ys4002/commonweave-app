package com.commonweave.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cookie hardening and brute-force throttle settings (prefix app.security). */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("true") boolean secureCookies,
        @DefaultValue("5") int loginMaxFailures,
        @DefaultValue("15m") Duration throttleWindow,
        @DefaultValue("10") int signupMaxAttempts) {
}
