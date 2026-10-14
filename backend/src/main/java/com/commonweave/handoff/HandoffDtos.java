package com.commonweave.handoff;

import com.commonweave.listing.Category;
import com.commonweave.listing.ListingType;
import com.commonweave.user.UserRef;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class HandoffDtos {
    private HandoffDtos() {
    }

    public record ListingRef(Long id, String title, Category category, ListingType type) {
    }

    public record HandoffSummary(Long id, HandoffStatus status, ListingRef listing, HandoffRole role,
                                 UserRef counterpart, Instant scheduledAt, Instant returnBy,
                                 boolean returnExpected, Instant updatedAt) {
    }

    public record EventDto(HandoffStatus fromStatus, HandoffStatus toStatus, UserRef actor, String note,
                           Instant at) {
    }

    public record HandoffDetail(@JsonUnwrapped HandoffSummary summary, String message, String pickupNote,
                                String pickupPreference, List<EventDto> events,
                                List<TransitionAction> allowedActions) {
    }

    public record ProposeRequest(@NotNull Long listingId, @Size(max = 500) String message) {
    }

    public record TransitionRequest(@NotNull TransitionAction action, Instant scheduledAt,
                                    @Size(max = 500) String pickupNote, Instant returnBy,
                                    @Size(max = 500) String note) {
    }
}
