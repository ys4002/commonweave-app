package com.commonweave.handoff;

import com.commonweave.handoff.HandoffDtos.EventDto;
import com.commonweave.handoff.HandoffDtos.HandoffDetail;
import com.commonweave.handoff.HandoffDtos.HandoffSummary;
import com.commonweave.handoff.HandoffDtos.ListingRef;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import com.commonweave.user.User;
import com.commonweave.user.UserLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HandoffMapper {
    private final ListingRepository listings;
    private final UserLookup users;
    private final HandoffEventRepository events;

    public HandoffMapper(ListingRepository listings, UserLookup users, HandoffEventRepository events) {
        this.listings = listings;
        this.users = users;
        this.events = events;
    }

    public List<HandoffSummary> summaries(List<Handoff> items, Long viewerId) {
        Map<Long, Listing> listingMap = listings.findAllById(items.stream().map(Handoff::getListingId).distinct().toList())
                .stream().collect(Collectors.toMap(Listing::getId, l -> l));
        Map<Long, User> people = users.byIds(items.stream().map(h -> h.counterpartOf(viewerId)).toList());
        return items.stream().map(h -> summary(h, viewerId, listingMap.get(h.getListingId()), people)).toList();
    }

    public HandoffDetail detail(Handoff h, Long viewerId) {
        Listing listing = listings.findById(h.getListingId()).orElseThrow();
        List<HandoffEvent> evs = events.findByHandoffIdOrderByIdAsc(h.getId());
        List<Long> ids = new ArrayList<>(evs.stream().map(HandoffEvent::getActorId).toList());
        ids.add(h.counterpartOf(viewerId));
        Map<Long, User> people = users.byIds(ids);
        List<EventDto> eventDtos = evs.stream().map(e -> new EventDto(e.getFromStatus(), e.getToStatus(),
                UserLookup.ref(people, e.getActorId()), e.getNote(), e.getCreatedAt())).toList();
        boolean isOwner = h.ownerId().equals(viewerId);
        return new HandoffDetail(summary(h, viewerId, listing, people), h.getMessage(), h.getPickupNote(),
                listing.getPickupPreference(), eventDtos,
                HandoffStateMachine.allowed(h.getStatus(), h.isReturnExpected(), isOwner));
    }

    private HandoffSummary summary(Handoff h, Long viewerId, Listing l, Map<Long, User> people) {
        ListingRef ref = new ListingRef(l.getId(), l.getTitle(), l.getCategory(), l.getType());
        return new HandoffSummary(h.getId(), h.getStatus(), ref, h.roleOf(viewerId),
                UserLookup.ref(people, h.counterpartOf(viewerId)), h.getScheduledAt(), h.getReturnBy(),
                h.isReturnExpected(), h.getUpdatedAt());
    }
}
