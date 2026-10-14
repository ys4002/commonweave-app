package com.commonweave.circle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CircleIntegrationTest extends IntegrationTest {

    @Test
    void creatorBecomesModeratorAndCountsAreReported() {
        Actor mod = newUser("Founder");
        JsonNode c = post(mod, "/api/v1/circles", Map.of("name", "Indiranagar Sharers", "city", "Bengaluru",
                "area", "Indiranagar", "description", "d", "guidelines", "g"), 201);
        assertEquals("MODERATOR", c.path("myRole").asText());
        assertEquals("ACTIVE", c.path("myStatus").asText());
        assertEquals(1, c.path("memberCount").asInt());
        assertEquals(0, c.path("activeListingCount").asInt());
        assertTrue(c.path("slug").asText().startsWith("indiranagar-sharers"));
        assertEquals(mod.id(), c.path("moderators").get(0).path("userId").asLong());

        Actor member = newUser("Joiner");
        long id = c.path("id").asLong();
        join(member, id);
        join(member, id);
        createListing(member, id, "OFFER", "Something", false);
        JsonNode detail = get(member, "/api/v1/circles/" + id, 200);
        assertEquals(2, detail.path("memberCount").asInt());
        assertEquals(1, detail.path("activeListingCount").asInt());
        assertEquals("MEMBER", detail.path("myRole").asText());
        assertEquals(1, get(member, "/api/v1/auth/me", 200).path("circles").size());
    }

    @Test
    void circleValidationAndSlugUniqueness() {
        Actor mod = newUser("Founder");
        expectError(post(mod, "/api/v1/circles", Map.of("name", "x", "city", ""), 400), 400, "VALIDATION_FAILED");
        Map<String, String> body = Map.of("name", "Same Name Circle", "city", "Pune");
        String s1 = post(mod, "/api/v1/circles", body, 201).path("slug").asText();
        String s2 = post(mod, "/api/v1/circles", body, 201).path("slug").asText();
        assertTrue(!s1.equals(s2));
    }

    @Test
    void searchByNameAndCity() {
        Actor mod = newUser("Founder");
        post(mod, "/api/v1/circles", Map.of("name", "Zebra Lane Zealots", "city", "Chennai"), 201);
        assertEquals(1, get(mod, "/api/v1/circles?q=zebra lane", 200).path("totalElements").asInt());
        assertEquals(0, get(mod, "/api/v1/circles?q=zebra&city=Mumbai", 200).path("totalElements").asInt());
        assertEquals(1, get(mod, "/api/v1/circles?q=zebra&city=chennai", 200).path("totalElements").asInt());
    }

    @Test
    void lastModeratorCannotLeaveOrBeDemoted() {
        Setup s = setup();
        String base = "/api/v1/circles/" + s.circleId();
        expectError(post(s.mod(), base + "/leave", null, 409), 409, "CONFLICT");
        expectError(patch(s.mod(), base + "/members/" + s.mod().id(), Map.of("role", "MEMBER"), 409), 409, "CONFLICT");
        expectError(patch(s.mod(), base + "/members/" + s.mod().id(), Map.of("status", "REMOVED"), 409), 409, "CONFLICT");
        patch(s.mod(), base + "/members/" + s.owner().id(), Map.of("role", "MODERATOR"), 200);
        patch(s.mod(), base + "/members/" + s.mod().id(), Map.of("role", "MEMBER"), 200);
        post(s.mod(), base + "/leave", null, 204);
    }

    @Test
    void removedMembersCannotRejoinAndLeavingClosesOwnListings() {
        Setup s = setup();
        String base = "/api/v1/circles/" + s.circleId();
        long listing = createListing(s.other(), s.circleId(), "OFFER", "Leaver item", false);
        post(s.other(), base + "/leave", null, 204);
        assertEquals("CLOSED", get(s.other(), "/api/v1/listings/" + listing + "?x=1", 200).path("status").asText());
        join(s.other(), s.circleId());
        patch(s.mod(), base + "/members/" + s.other().id(), Map.of("status", "REMOVED"), 200);
        expectError(post(s.other(), base + "/join", null, 403), 403, "FORBIDDEN");
    }

    @Test
    void leavingIsBlockedWhileAHandoffIsInProgress() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "In progress", true);
        confirm(s.owner(), propose(s.other(), listing), 200);
        expectError(post(s.other(), "/api/v1/circles/" + s.circleId() + "/leave", null, 409), 409, "CONFLICT");
        expectError(post(s.owner(), "/api/v1/circles/" + s.circleId() + "/leave", null, 409), 409, "CONFLICT");
    }

    @Test
    void moderatorCanEditGuidelinesAndActivityFeedShowsListings() {
        Setup s = setup();
        String base = "/api/v1/circles/" + s.circleId();
        JsonNode updated = patch(s.mod(), base, Map.of("guidelines", "New rules"), 200);
        assertEquals("New rules", updated.path("guidelines").asText());
        createListing(s.owner(), s.circleId(), "OFFER", "Feed item", false);
        JsonNode feed = get(s.other(), base + "/activity?size=5", 200);
        assertEquals(1, feed.size());
        assertTrue(feed.get(0).path("text").asText().contains("Feed item"));
        assertEquals("LISTING_OFFERED", feed.get(0).path("type").asText());
    }
}
