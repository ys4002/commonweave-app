package com.commonweave.impact;

import com.commonweave.listing.Category;
import java.util.List;

public final class ImpactDtos {
    private ImpactDtos() {
    }

    public record CategoryImpact(Category category, int count, long rupeesSaved) {
    }

    public record Methodology(String label, String formula, String assumption) {
    }

    public record ImpactResponse(int periodDays, int completedExchanges, int activeMembers,
                                 long rupeesSavedEstimate, double co2KgAvoidedEstimate, int hoursShared,
                                 List<CategoryImpact> byCategory, List<Methodology> methodology,
                                 String disclaimer) {
    }
}
