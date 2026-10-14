package com.commonweave.handoff;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "handoffs")
@Getter
@Setter
@NoArgsConstructor
public class Handoff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long listingId;
    private Long circleId;
    private Long providerId;
    private Long recipientId;
    private Long proposerId;
    @Enumerated(EnumType.STRING)
    private HandoffStatus status;
    private String message;
    private Instant scheduledAt;
    private String pickupNote;
    private Instant returnBy;
    private boolean returnExpected;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public boolean involves(Long userId) {
        return providerId.equals(userId) || recipientId.equals(userId);
    }

    /** The listing owner is whichever participant did not make the proposal. */
    public Long ownerId() {
        return proposerId.equals(providerId) ? recipientId : providerId;
    }

    public Long counterpartOf(Long userId) {
        return providerId.equals(userId) ? recipientId : providerId;
    }

    public HandoffRole roleOf(Long userId) {
        return providerId.equals(userId) ? HandoffRole.PROVIDER : HandoffRole.RECIPIENT;
    }
}
