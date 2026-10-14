package com.commonweave.listing;

import com.commonweave.handoff.HandoffStatus;
import com.commonweave.user.UserRef;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ListingDtos {
    private ListingDtos() {
    }

    public record ListingSummary(Long id, Long circleId, String circleName, ListingType type, ListingKind kind,
                                 String title, Category category, List<String> tags, LocalDate availableFrom,
                                 LocalDate availableTo, String pickupPreference, int estimatedValueInr,
                                 int depositInr, ListingStatus status, UserRef owner, Instant createdAt) {
    }

    public record MyHandoff(Long id, HandoffStatus status) {
    }

    public record ListingDetail(@JsonUnwrapped ListingSummary summary, String description, int hoursEstimate,
                                boolean returnExpected, long completedExchangesOfOwner, MyHandoff myHandoff,
                                boolean canEdit) {
    }

    public record ListingRequest(
            Long circleId,
            @NotNull ListingType type,
            @NotNull ListingKind kind,
            @NotBlank @Size(min = 3, max = 120) String title,
            @Size(max = 2000) String description,
            @NotNull Category category,
            @Size(max = 8) List<@Size(max = 30) String> tags,
            @NotNull LocalDate availableFrom,
            @NotNull LocalDate availableTo,
            @Size(max = 200) String pickupPreference,
            @Min(0) @Max(500000) Integer estimatedValueInr,
            @Min(0) @Max(100000) Integer depositInr,
            @Min(0) @Max(100) Integer hoursEstimate,
            Boolean returnExpected) {
    }
}
