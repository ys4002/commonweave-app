package com.commonweave.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModerationIntegrationTest extends IntegrationTest {

    private long report(Actor reporter, long circle, long listing) {
        post(reporter, "/api/v1/reports", Map.of("circleId", circle, "targetType", "LISTING", "targetId", listing,
                "reason", "This is not a neighbourly share"), 201);
        return 0;
    }

    @Test
    void reportLifecycleResolveWithRemovalCancelsProposalsAndLogs() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Dodgy item", true);
        long proposal = propose(s.other(), listing);
        report(s.other(), s.circleId(), listing);
        String base = "/api/v1/circles/" + s.circleId();

        JsonNode open = get(s.mod(), base + "/reports?status=OPEN", 200);
        assertEquals(1, open.size());
        assertEquals("OPEN", open.get(0).path("status").asText());
        assertEquals(s.other().id(), open.get(0).path("reporter").path("id").asLong());
        long reportId = open.get(0).path("id").asLong();

        post(s.mod(), base + "/reports/" + reportId + "/resolve",
                Map.of("resolution", "RESOLVED", "note", "removed", "removeListing", true), 204);

        assertEquals("REMOVED", listingStatus(s.owner(), listing));
        assertEquals("CANCELLED", get(s.other(), "/api/v1/handoffs/" + proposal, 200).path("status").asText());
        assertEquals(0, get(s.mod(), base + "/reports?status=OPEN", 200).size());
        assertEquals(1, get(s.mod(), base + "/reports?status=RESOLVED", 200).size());
        JsonNode log = get(s.mod(), base + "/moderation-log", 200);
        assertTrue(log.size() >= 1);
        assertEquals(s.mod().id(), log.get(0).path("moderator").path("id").asLong());
        expectError(post(s.mod(), base + "/reports/" + reportId + "/resolve", Map.of("resolution", "DISMISSED"), 409),
                409, "CONFLICT");
        assertEquals(1, get(s.owner(), "/api/v1/notifications?unread=true", 200).path("content").findValuesAsText("type")
                .stream().filter("LISTING_REMOVED"::equals).count());
    }

    @Test
    void dismissingKeepsTheListing() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Fine item", true);
        report(s.other(), s.circleId(), listing);
        String base = "/api/v1/circles/" + s.circleId();
        long reportId = get(s.mod(), base + "/reports", 200).get(0).path("id").asLong();
        post(s.mod(), base + "/reports/" + reportId + "/resolve", Map.of("resolution", "DISMISSED", "note", "ok"), 204);
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));
        assertEquals("DISMISSED", get(s.mod(), base + "/reports", 200).get(0).path("status").asText());
        expectError(post(s.mod(), base + "/reports/" + reportId + "/resolve",
                Map.of("resolution", "DISMISSED", "removeListing", true), 409), 409, "CONFLICT");
    }

    @Test
    void removalIsRefusedWhileAnItemIsHandedOver() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Out on loan", true);
        long id = propose(s.other(), listing);
        confirm(s.owner(), id, 200);
        transition(s.owner(), id, "MARK_HANDED_OFF", 200);
        expectError(post(s.mod(), "/api/v1/circles/" + s.circleId() + "/listings/" + listing + "/remove",
                Map.of("note", "x"), 409), 409, "CONFLICT");
        assertEquals("RESERVED", listingStatus(s.owner(), listing));
    }

    @Test
    void removingAScheduledListingCancelsTheHandoffWithoutReactivatingIt() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Scheduled item", true);
        long id = propose(s.other(), listing);
        confirm(s.owner(), id, 200);
        post(s.mod(), "/api/v1/circles/" + s.circleId() + "/listings/" + listing + "/remove", Map.of("note", "x"), 204);
        assertEquals("CANCELLED", get(s.other(), "/api/v1/handoffs/" + id, 200).path("status").asText());
        assertEquals("REMOVED", listingStatus(s.owner(), listing));
    }

    @Test
    void reportValidation() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Item", true);
        expectError(post(s.other(), "/api/v1/reports", Map.of("circleId", s.circleId(), "targetType", "LISTING",
                "targetId", listing, "reason", "bad"), 400), 400, "VALIDATION_FAILED");
        expectError(post(s.other(), "/api/v1/reports", Map.of("circleId", s.circleId(), "targetType", "LISTING",
                "targetId", 999999999L, "reason", "does not exist anywhere"), 404), 404, "NOT_FOUND");
        post(s.other(), "/api/v1/reports", Map.of("circleId", s.circleId(), "targetType", "MEMBER",
                "targetId", s.owner().id(), "reason", "rude in messages"), 201);
        JsonNode reports = get(s.mod(), "/api/v1/circles/" + s.circleId() + "/reports?status=OPEN", 200);
        assertEquals(s.owner().id() == reports.get(0).path("targetId").asLong(), true);
        assertTrue(reports.get(0).path("targetLabel").asText().startsWith("Owner"));
    }
}
