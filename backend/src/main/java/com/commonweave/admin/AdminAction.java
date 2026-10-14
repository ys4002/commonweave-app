package com.commonweave.admin;

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
@Table(name = "admin_actions")
@Getter
@Setter
@NoArgsConstructor
public class AdminAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String note;
    private Instant createdAt;

    public AdminAction(Long adminId, String action, String targetType, Long targetId, String note, Instant at) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.note = note;
        this.createdAt = at;
    }
}
