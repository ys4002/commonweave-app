package com.commonweave.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class TimeConfig {
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(DISPLAY_ZONE));
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
