package com.commonweave.listing;

import com.commonweave.circle.CircleAccess;
import com.commonweave.common.ApiException;
import org.springframework.stereotype.Component;

/** Who may see a listing. Anything not visible is reported as 404 so existence never leaks. */
@Component
public class ListingAccess {
    private final ListingRepository listings;
    private final CircleAccess circles;

    public ListingAccess(ListingRepository listings, CircleAccess circles) {
        this.listings = listings;
        this.circles = circles;
    }

    public Listing requireVisible(Long listingId, Long userId) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (!canSee(l, userId)) {
            throw ApiException.notFound("Listing");
        }
        return l;
    }

    public boolean canSee(Listing l, Long userId) {
        if (l.getOwnerId().equals(userId)) {
            return true;
        }
        if (!circles.isActiveMember(l.getCircleId(), userId)
                || !circles.isActiveMember(l.getCircleId(), l.getOwnerId())) {
            return false;
        }
        return l.getStatus() != ListingStatus.REMOVED || circles.isModerator(l.getCircleId(), userId);
    }
}
