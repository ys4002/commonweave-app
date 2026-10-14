package com.commonweave.circle;

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
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long circleId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private MemberRole role;
    @Enumerated(EnumType.STRING)
    private MemberStatus status;
    private Instant joinedAt;

    public Membership(Long circleId, Long userId, MemberRole role) {
        this.circleId = circleId;
        this.userId = userId;
        this.role = role;
        this.status = MemberStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isActiveModerator() {
        return isActive() && role == MemberRole.MODERATOR;
    }
}
