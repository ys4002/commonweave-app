package com.commonweave.admin;

import com.commonweave.circle.MemberRole;
import com.commonweave.circle.MemberStatus;
import com.commonweave.handoff.HandoffDtos.HandoffSummary;
import com.commonweave.handoff.HandoffDtos.ListingRef;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.ListingDtos.ListingSummary;
import com.commonweave.moderation.ModerationDtos.ReportView;
import com.commonweave.user.PlatformRole;
import com.commonweave.user.UserRef;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record UserCounts(long total, long disabled, long admins) {
    }

    public record Overview(UserCounts users, long circles, Map<String, Long> listings, Map<String, Long> handoffs,
                           long openReports) {
    }

    public record AdminCircleRef(Long circleId, String name, MemberRole role, MemberStatus status) {
    }

    public record AdminUser(Long id, String email, String displayName, String locality, PlatformRole platformRole,
                            boolean enabled, boolean onboarded, Instant createdAt, Instant lastLoginAt,
                            List<AdminCircleRef> circles, long listingCount, long completedExchanges) {
    }

    public record AdminUserDetail(@JsonUnwrapped AdminUser user, String bio, List<ListingSummary> recentListings,
                                  List<HandoffSummary> recentHandoffs) {
    }

    public record UpdateUserRequest(Boolean enabled, PlatformRole platformRole,
                                    @Size(min = 1, max = 80) String displayName) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record RemoveListingRequest(@Size(max = 500) String note) {
    }

    public record AdminHandoff(Long id, HandoffStatus status, ListingRef listing, Long circleId, UserRef provider,
                               UserRef recipient, Instant scheduledAt, Instant returnBy, boolean returnExpected,
                               Instant updatedAt) {
    }

    public record AdminReport(@JsonUnwrapped ReportView report, Long circleId) {
    }

    public record AuditEntry(String action, String targetType, Long targetId, String note, UserRef admin,
                             Instant createdAt) {
    }
}
