package com.commonweave.matching;

import com.commonweave.circle.CircleAccess;
import com.commonweave.common.ApiException;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingAccess;
import com.commonweave.listing.ListingMapper;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.listing.ListingType;
import com.commonweave.matching.MatchDtos.MatchView;
import com.commonweave.matching.MatchDtos.SuggestedMatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingService {
    public static final int PER_LISTING_LIMIT = 5;
    public static final int SUGGESTION_LIMIT = 6;

    record Scored(Listing candidate, MatchScorer.Result result) {
    }

    private final ListingRepository listings;
    private final ListingAccess access;
    private final CircleAccess circles;
    private final ListingMapper mapper;

    public MatchingService(ListingRepository listings, ListingAccess access, CircleAccess circles,
                           ListingMapper mapper) {
        this.listings = listings;
        this.access = access;
        this.circles = circles;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MatchView> forListing(Long userId, Long listingId) {
        Listing source = access.requireVisible(listingId, userId);
        if (!circles.isActiveMember(source.getCircleId(), userId)) {
            throw ApiException.notFound("Listing");
        }
        List<Scored> top = rank(source, candidates(source), PER_LISTING_LIMIT);
        return toViews(top);
    }

    @Transactional(readOnly = true)
    public List<SuggestedMatch> suggested(Long userId, int limit) {
        List<Listing> mine = listings.findByOwnerIdAndStatusAndCircleIdIn(userId, ListingStatus.ACTIVE,
                circles.activeCircleIds(userId));
        Map<Long, Listing> sourceByCandidate = new HashMap<>();
        Map<Long, Scored> bestByCandidate = new LinkedHashMap<>();
        for (Listing source : mine) {
            for (Scored s : rank(source, candidates(source), PER_LISTING_LIMIT)) {
                Scored current = bestByCandidate.get(s.candidate().getId());
                if (current == null || s.result().score() > current.result().score()) {
                    bestByCandidate.put(s.candidate().getId(), s);
                    sourceByCandidate.put(s.candidate().getId(), source);
                }
            }
        }
        List<Scored> ranked = bestByCandidate.values().stream().sorted(order()).limit(limit).toList();
        List<MatchView> views = toViews(ranked);
        List<SuggestedMatch> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            out.add(new SuggestedMatch(mapper.summary(sourceByCandidate.get(ranked.get(i).candidate().getId())),
                    views.get(i)));
        }
        return out;
    }

    private List<Listing> candidates(Listing source) {
        ListingType opposite = source.getType() == ListingType.OFFER ? ListingType.REQUEST : ListingType.OFFER;
        return listings.findMatchCandidates(source.getCircleId(), opposite,
                ListingStatus.ACTIVE, source.getOwnerId());
    }

    static List<Scored> rank(Listing source, List<Listing> candidates, int limit) {
        return candidates.stream()
                .map(c -> new Scored(c, MatchScorer.score(source, c)))
                .filter(s -> s.result().score() >= MatchScorer.MIN_SCORE)
                .sorted(order()).limit(limit).toList();
    }

    private static Comparator<Scored> order() {
        return Comparator.comparingInt((Scored s) -> s.result().score()).reversed()
                .thenComparing(Comparator.comparingLong((Scored s) -> s.candidate().getId()).reversed());
    }

    private List<MatchView> toViews(List<Scored> scored) {
        var summaries = mapper.summaries(scored.stream().map(Scored::candidate).toList());
        List<MatchView> out = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            out.add(new MatchView(summaries.get(i), scored.get(i).result().score(), scored.get(i).result().reasons()));
        }
        return out;
    }
}
