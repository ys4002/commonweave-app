package com.commonweave.moderation;

import com.commonweave.user.UserRef;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ModerationDtos {
    private ModerationDtos() {
    }

    public enum Resolution { RESOLVED, DISMISSED }

    public record CreateReportRequest(@NotNull Long circleId, @NotNull ReportTargetType targetType,
                                      @NotNull Long targetId,
                                      @NotNull @Size(min = 5, max = 500) String reason) {
    }

    public record ReportView(Long id, ReportTargetType targetType, Long targetId, String targetLabel,
                             UserRef reporter, ReportStatus status, Instant createdAt, String reason) {
    }

    public record ResolveRequest(@NotNull Resolution resolution, @Size(max = 500) String note,
                                 Boolean removeListing) {
    }

    public record RemoveListingRequest(@Size(max = 500) String note) {
    }

    public record LogEntry(String actionType, String targetType, Long targetId, String note, UserRef moderator,
                           Instant createdAt) {
    }
}
