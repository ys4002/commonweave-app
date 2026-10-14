package com.commonweave.circle;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CircleDtos {
    private CircleDtos() {
    }

    public record CircleSummary(Long id, String slug, String name, String city, String area, String description,
                                long memberCount, long activeListingCount, MemberRole myRole) {
    }

    public record ModeratorRef(Long userId, String displayName) {
    }

    public record CircleDetail(@JsonUnwrapped CircleSummary summary, String guidelines,
                               List<ModeratorRef> moderators, MemberStatus myStatus) {
    }

    public record CreateCircleRequest(
            @NotBlank @Size(min = 3, max = 120) String name,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 120) String area,
            @Size(max = 1000) String description,
            @Size(max = 2000) String guidelines) {
    }

    public record UpdateCircleRequest(@Size(max = 1000) String description, @Size(max = 2000) String guidelines) {
    }

    public record MemberResponse(Long userId, String displayName, MemberRole role, MemberStatus status,
                                 Instant joinedAt, long completedExchanges) {
    }

    public record UpdateMemberRequest(MemberRole role, MemberStatus status) {
    }

    public record ActivityItem(String type, String text, Instant at) {
    }
}
