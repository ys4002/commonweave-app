package com.commonweave.matching;

import com.commonweave.listing.ListingDtos.ListingSummary;
import java.util.List;

public final class MatchDtos {
    private MatchDtos() {
    }

    public record MatchView(ListingSummary listing, int score, List<MatchScorer.Reason> reasons) {
    }

    public record SuggestedMatch(ListingSummary myListing, MatchView match) {
    }
}
