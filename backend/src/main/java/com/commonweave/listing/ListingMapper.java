package com.commonweave.listing;

import com.commonweave.circle.Circle;
import com.commonweave.circle.CircleRepository;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStats;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.ListingDtos.ListingDetail;
import com.commonweave.listing.ListingDtos.ListingSummary;
import com.commonweave.listing.ListingDtos.MyHandoff;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ListingMapper {
    private final CircleRepository circles;
    private final UserLookup users;
    private final HandoffRepository handoffs;
    private final HandoffStats stats;

    public ListingMapper(CircleRepository circles, UserLookup users, HandoffRepository handoffs, HandoffStats stats) {
        this.circles = circles;
        this.users = users;
        this.handoffs = handoffs;
        this.stats = stats;
    }

    public List<ListingSummary> summaries(List<Listing> items) {
        Map<Long, Circle> circleMap = circles.findAllById(items.stream().map(Listing::getCircleId).distinct().toList())
                .stream().collect(Collectors.toMap(Circle::getId, c -> c));
        Map<Long, User> owners = users.byIds(items.stream().map(Listing::getOwnerId).toList());
        return items.stream().map(l -> summary(l, circleMap, owners)).toList();
    }

    public ListingSummary summary(Listing l) {
        return summaries(List.of(l)).get(0);
    }

    public ListingDetail detail(Listing l, Long viewerId) {
        MyHandoff mine = handoffs
                .findFirstByListingIdAndProposerIdAndStatusNotOrderByIdDesc(l.getId(), viewerId, HandoffStatus.CANCELLED)
                .map(h -> new MyHandoff(h.getId(), h.getStatus())).orElse(null);
        boolean canEdit = l.getOwnerId().equals(viewerId) && l.getStatus() == ListingStatus.ACTIVE;
        return new ListingDetail(summary(l), l.getDescription(), l.getHoursEstimate(), l.isReturnExpected(),
                stats.completedExchanges(l.getOwnerId()), mine, canEdit);
    }

    private ListingSummary summary(Listing l, Map<Long, Circle> circleMap, Map<Long, User> owners) {
        Circle c = circleMap.get(l.getCircleId());
        return new ListingSummary(l.getId(), l.getCircleId(), c == null ? null : c.getName(), l.getType(),
                l.getKind(), l.getTitle(), l.getCategory(), l.tagList(), l.getAvailableFrom(), l.getAvailableTo(),
                l.getPickupPreference(), l.getEstimatedValueInr(), l.getDepositInr(), l.getStatus(),
                UserLookup.ref(owners, l.getOwnerId()), l.getCreatedAt());
    }
}
