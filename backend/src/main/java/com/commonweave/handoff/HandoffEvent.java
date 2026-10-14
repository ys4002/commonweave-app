package com.commonweave.handoff;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "handoff_events")
@Getter
@Setter
@NoArgsConstructor
public class HandoffEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long handoffId;
    private Long actorId;
    @Enumerated(EnumType.STRING)
    private HandoffStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private HandoffStatus toStatus;
    private String note;
    private Instant createdAt;

    public HandoffEvent(Long handoffId, Long actorId, HandoffStatus from, HandoffStatus to, String note,
                        Instant at) {
        this.handoffId = handoffId;
        this.actorId = actorId;
        this.fromStatus = from;
        this.toStatus = to;
        this.note = note;
        this.createdAt = at;
    }
}
