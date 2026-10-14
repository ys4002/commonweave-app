package com.commonweave.moderation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
@NoArgsConstructor
public class ModerationAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long circleId;
    private Long moderatorId;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String note;
    private Instant createdAt;

    public ModerationAction(Long circleId, Long moderatorId, String actionType, String targetType, Long targetId,
                            String note, Instant createdAt) {
        this.circleId = circleId;
        this.moderatorId = moderatorId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
