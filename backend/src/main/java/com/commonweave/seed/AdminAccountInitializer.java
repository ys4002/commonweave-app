package com.commonweave.seed;

import com.commonweave.user.PlatformRole;
import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the platform admin. Profile dev: the demo account admin/admin (documented as demo-only, bypasses the
 * 8-character rule). Any other profile: only when APP_ADMIN_EMAIL and APP_ADMIN_PASSWORD (>= 8 chars) are set.
 */
@Component
@Order(2)
public class AdminAccountInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Environment env;
    private final String configuredEmail;
    private final String configuredPassword;

    public AdminAccountInitializer(UserRepository users, PasswordEncoder encoder, Environment env,
                                   @Value("${APP_ADMIN_EMAIL:}") String configuredEmail,
                                   @Value("${APP_ADMIN_PASSWORD:}") String configuredPassword) {
        this.users = users;
        this.encoder = encoder;
        this.env = env;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(String... args) {
        boolean dev = env.acceptsProfiles(Profiles.of("dev"));
        String username = dev ? "admin" : configuredEmail.trim().toLowerCase();
        String password = dev ? "admin" : configuredPassword;
        if (username.isEmpty() || password.isEmpty() || users.existsByEmail(username)) {
            return;
        }
        if (!dev && password.length() < 8) {
            log.warn("APP_ADMIN_PASSWORD is shorter than 8 characters; admin account not created");
            return;
        }
        User admin = new User();
        admin.setEmail(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setDisplayName("Platform Admin");
        admin.setOnboarded(true);
        admin.setPlatformRole(PlatformRole.ADMIN);
        users.save(admin);
        log.info("Created platform admin account '{}'", username);
    }
}
