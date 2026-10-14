package com.commonweave.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchingIntegrationTest extends IntegrationTest {

    @Test
    void matchesAreOppositeTypeSameCircleAndExplained() {
        Setup s = setup();
        long offer = createListing(s.owner(), s.circleId(), "OFFER", "Power drill", true);
        long request = createListing(s.other(), s.circleId(), "REQUEST", "Power drill needed", true);
        createListing(s.other(), s.circleId(), "OFFER", "Another offer", true);

        long elsewhereCircle = createCircle(s.mod());
        join(s.other(), elsewhereCircle);
        createListing(s.other(), elsewhereCircle, "REQUEST", "Power drill needed", true);

        JsonNode matches = get(s.owner(), "/api/v1/listings/" + offer + "/matches", 200);
        assertEquals(1, matches.size());
        JsonNode m = matches.get(0);
        assertEquals(request, m.path("listing").path("id").asLong());
        assertTrue(m.path("score").asInt() >= 30);
        int sum = 0;
        for (JsonNode r : m.path("reasons")) {
            sum += r.path("points").asInt();
            assertTrue(r.path("points").asInt() <= r.path("max").asInt());
            assertTrue(!r.path("explanation").asText().isBlank());
        }
        assertEquals(m.path("score").asInt(), sum);
        assertEquals(4, m.path("reasons").size());
        assertEquals(m, get(s.owner(), "/api/v1/listings/" + offer + "/matches", 200).get(0));
        assertEquals(1, get(s.other(), "/api/v1/listings/" + offer + "/matches", 200).size());
    }

    @Test
    void suggestedMatchesCoverMyActiveListingsOnly() {
        Setup s = setup();
        long offer = createListing(s.owner(), s.circleId(), "OFFER", "Folding table", true);
        createListing(s.other(), s.circleId(), "REQUEST", "Folding table wanted", true);
        JsonNode suggested = get(s.owner(), "/api/v1/matches/suggested", 200);
        assertEquals(1, suggested.size());
        assertEquals(offer, suggested.get(0).path("myListing").path("id").asLong());
        assertTrue(suggested.get(0).path("match").path("score").asInt() >= 30);
        assertEquals(0, get(newUser("Nobody"), "/api/v1/matches/suggested", 200).size());
    }

    @Test
    void reservedListingsAreNotMatched() {
        Setup s = setup();
        long offer = createListing(s.owner(), s.circleId(), "OFFER", "Reserved lamp", true);
        long request = createListing(s.other(), s.circleId(), "REQUEST", "Reserved lamp wanted", true);
        Actor volunteer = newUser("Volunteer");
        join(volunteer, s.circleId());
        long h = post(volunteer, "/api/v1/handoffs", Map.of("listingId", request), 201).path("id").asLong();
        confirm(s.other(), h, 200);
        assertEquals(0, get(s.owner(), "/api/v1/listings/" + offer + "/matches", 200).size());
    }
}
