package com.commonweave.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String bio;
    private String locality;
    private boolean onboarded;
    @Enumerated(EnumType.STRING)
    private PlatformRole platformRole = PlatformRole.USER;
    private boolean enabled = true;
    private Instant lastLoginAt;
    /** Bumped to invalidate every existing session of this user (password reset, disable, role change). */
    private int sessionVersion;
    private Instant createdAt;

    public boolean isAdmin() {
        return platformRole == PlatformRole.ADMIN;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
