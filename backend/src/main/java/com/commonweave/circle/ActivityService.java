package com.commonweave.circle;

import com.commonweave.circle.CircleDtos.ActivityItem;
import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStats;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.listing.ListingType;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the human-readable activity feed from listings and finished handoffs. */
@Service
public class ActivityService {
    public record Entry(Long circleId, ActivityItem item) {
    }

    private final ListingRepository listings;
    private final HandoffRepository handoffs;
    private final UserLookup users;

    public ActivityService(ListingRepository listings, HandoffRepository handoffs, UserLookup users) {
        this.listings = listings;
        this.handoffs = handoffs;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Entry> recent(Collection<Long> circleIds, int limit) {
        if (circleIds.isEmpty()) {
            return List.of();
        }
        PageRequest page = PageRequest.of(0, limit);
        List<Listing> newListings =
                listings.findVisibleRecent(circleIds, ListingStatus.REMOVED, page);
        List<Handoff> finished = handoffs.findByCircleIdInAndStatusInOrderByUpdatedAtDesc(
                circleIds, HandoffStats.FINISHED, page);
        Map<Long, Listing> finishedListings = new java.util.HashMap<>();
        listings.findAllById(finished.stream().map(Handoff::getListingId).toList())
                .forEach(l -> finishedListings.put(l.getId(), l));
        List<Long> ids = new ArrayList<>(newListings.stream().map(Listing::getOwnerId).toList());
        finished.forEach(h -> {
            ids.add(h.getProviderId());
            ids.add(h.getRecipientId());
        });
        Map<Long, User> people = users.byIds(ids);
        List<Entry> all = new ArrayList<>();
        newListings.forEach(l -> all.add(new Entry(l.getCircleId(), new ActivityItem(
                l.getType() == ListingType.OFFER ? "LISTING_OFFERED" : "LISTING_REQUESTED",
                UserLookup.ref(people, l.getOwnerId()).displayName()
                        + (l.getType() == ListingType.OFFER ? " offered " : " asked for ") + l.getTitle(),
                l.getCreatedAt()))));
        finished.forEach(h -> {
            Listing l = finishedListings.get(h.getListingId());
            all.add(new Entry(h.getCircleId(), new ActivityItem("EXCHANGE_COMPLETED",
                    UserLookup.ref(people, h.getProviderId()).displayName() + " and "
                            + UserLookup.ref(people, h.getRecipientId()).displayName() + " shared "
                            + (l == null ? "an item" : l.getTitle()), h.getUpdatedAt())));
        });
        all.sort(Comparator.comparing((Entry e) -> e.item().at(), Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .reversed());
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
