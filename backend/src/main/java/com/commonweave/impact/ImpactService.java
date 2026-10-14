package com.commonweave.impact;

import com.commonweave.circle.CircleAccess;
import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStats;
import com.commonweave.impact.ImpactCalculator.Exchange;
import com.commonweave.impact.ImpactDtos.ImpactResponse;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImpactService {
    static final int DEFAULT_DAYS = 90;
    static final int MAX_DAYS = 730;

    private final HandoffRepository handoffs;
    private final ListingRepository listings;
    private final CircleAccess access;
    private final ImpactCalculator calculator;
    private final Clock clock;

    public ImpactService(HandoffRepository handoffs, ListingRepository listings, CircleAccess access,
                         ImpactProperties props, Clock clock) {
        this.handoffs = handoffs;
        this.listings = listings;
        this.access = access;
        this.calculator = new ImpactCalculator(props);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ImpactResponse forCircle(Long userId, Long circleId, Integer days) {
        access.requireActiveMember(circleId, userId);
        int period = clamp(days);
        return summarise(handoffs.findByCircleIdAndStatusInAndUpdatedAtGreaterThanEqual(
                circleId, HandoffStats.FINISHED, since(period)), period);
    }

    @Transactional(readOnly = true)
    public ImpactResponse forUser(Long userId, Integer days) {
        int period = clamp(days);
        return summarise(handoffs.findMineSince(userId, HandoffStats.FINISHED, since(period)), period);
    }

    private ImpactResponse summarise(List<Handoff> finished, int period) {
        Map<Long, Listing> byId = listings.findAllById(finished.stream().map(Handoff::getListingId).distinct().toList())
                .stream().collect(Collectors.toMap(Listing::getId, l -> l));
        Set<Long> people = new HashSet<>();
        List<Exchange> exchanges = finished.stream().map(h -> {
            people.add(h.getProviderId());
            people.add(h.getRecipientId());
            Listing l = byId.get(h.getListingId());
            return new Exchange(l.getKind(), l.getCategory(), h.isReturnExpected(), l.getEstimatedValueInr(),
                    l.getHoursEstimate());
        }).toList();
        return calculator.summarise(exchanges, period, people.size());
    }

    private Instant since(int days) {
        return clock.instant().minus(Duration.ofDays(days));
    }

    private static int clamp(Integer days) {
        return days == null ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
    }
}
