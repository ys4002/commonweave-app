package com.commonweave.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizationIntegrationTest extends IntegrationTest {

    @Test
    void outsiderCannotSeeCircleListingsOrActOnThem() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Cordless drill", true);
        Actor outsider = newUser("Outsider");

        expectError(get(outsider, "/api/v1/listings/" + listing, 404), 404, "NOT_FOUND");
        expectError(get(outsider, "/api/v1/listings/" + listing + "/matches", 404), 404, "NOT_FOUND");
        expectError(post(outsider, "/api/v1/handoffs", Map.of("listingId", listing), 404), 404, "NOT_FOUND");
        expectError(post(outsider, "/api/v1/listings",
                listingBody(s.circleId(), "OFFER", "ITEM", "Sneaky", "TOOLS", false), 403), 403, "FORBIDDEN");
        expectError(get(outsider, "/api/v1/circles/" + s.circleId() + "/members", 403), 403, "FORBIDDEN");
        expectError(get(outsider, "/api/v1/circles/" + s.circleId() + "/activity", 403), 403, "FORBIDDEN");
        expectError(get(outsider, "/api/v1/circles/" + s.circleId() + "/impact", 403), 403, "FORBIDDEN");
        expectError(post(outsider, "/api/v1/reports", Map.of("circleId", s.circleId(), "targetType", "LISTING",
                "targetId", listing, "reason", "this is a spam listing"), 403), 403, "FORBIDDEN");

        JsonNode page = get(outsider, "/api/v1/listings?circleId=" + s.circleId(), 200);
        assertEquals(0, page.path("content").size());
        assertEquals(0, page.path("totalElements").asInt());
    }

    @Test
    void outsiderStillSeesPublicCircleInformation() {
        Setup s = setup();
        Actor outsider = newUser("Browser");
        JsonNode detail = get(outsider, "/api/v1/circles/" + s.circleId(), 200);
        assertEquals(3, detail.path("memberCount").asInt());
        assertEquals(true, detail.path("myRole").isNull());
        assertEquals(1, detail.path("moderators").size());
    }

    @Test
    void onlyModeratorsMayModerate() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Folding table", true);
        post(s.other(), "/api/v1/reports", Map.of("circleId", s.circleId(), "targetType", "LISTING",
                "targetId", listing, "reason", "looks like it is for sale"), 201);
        String base = "/api/v1/circles/" + s.circleId();

        for (Actor nonMod : new Actor[] {s.owner(), s.other(), newUser("Stranger")}) {
            expectError(get(nonMod, base + "/reports?status=OPEN", 403), 403, "FORBIDDEN");
            expectError(get(nonMod, base + "/moderation-log", 403), 403, "FORBIDDEN");
            expectError(post(nonMod, base + "/reports/1/resolve", Map.of("resolution", "DISMISSED"), 403), 403, "FORBIDDEN");
            expectError(post(nonMod, base + "/listings/" + listing + "/remove", Map.of("note", "x"), 403), 403, "FORBIDDEN");
            expectError(patch(nonMod, base, Map.of("description", "hijack"), 403), 403, "FORBIDDEN");
        }
        JsonNode reports = get(s.mod(), base + "/reports?status=OPEN", 200);
        assertEquals(1, reports.size());
        assertEquals("Folding table", reports.get(0).path("targetLabel").asText());
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));
    }

    @Test
    void memberManagementIsModeratorOnly() {
        Setup s = setup();
        String url = "/api/v1/circles/" + s.circleId() + "/members/" + s.other().id();
        expectError(patch(s.owner(), url, Map.of("role", "MODERATOR"), 403), 403, "FORBIDDEN");
        JsonNode updated = patch(s.mod(), url, Map.of("status", "SUSPENDED"), 200);
        assertEquals("SUSPENDED", updated.path("status").asText());
    }

    @Test
    void membersListHidesInactiveMembersFromOrdinaryMembers() {
        Setup s = setup();
        patch(s.mod(), "/api/v1/circles/" + s.circleId() + "/members/" + s.other().id(), Map.of("status", "SUSPENDED"), 200);
        assertEquals(2, get(s.owner(), "/api/v1/circles/" + s.circleId() + "/members", 200).size());
        assertEquals(3, get(s.mod(), "/api/v1/circles/" + s.circleId() + "/members", 200).size());
    }

    @Test
    void nonParticipantsGetNotFoundForHandoffs() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Pressure cooker", true);
        long handoff = propose(s.other(), listing);
        Actor bystander = newUser("Bystander");
        join(bystander, s.circleId());

        expectError(get(bystander, "/api/v1/handoffs/" + handoff, 404), 404, "NOT_FOUND");
        expectError(get(s.mod(), "/api/v1/handoffs/" + handoff, 404), 404, "NOT_FOUND");
        expectError(transition(bystander, handoff, "CANCEL", 404), 404, "NOT_FOUND");
        assertEquals(0, get(bystander, "/api/v1/handoffs", 200).path("totalElements").asInt());
        get(s.owner(), "/api/v1/handoffs/" + handoff, 200);
        get(s.other(), "/api/v1/handoffs/" + handoff, 200);
    }

    @Test
    void onlyTheOwnerMayEditOrCloseAListing() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Ladder", true);
        Map<String, Object> body = listingBody(s.circleId(), "OFFER", "ITEM", "Hijacked ladder", "TOOLS", true);
        expectError(put(s.other(), "/api/v1/listings/" + listing, body, 403), 403, "FORBIDDEN");
        expectError(delete(s.other(), "/api/v1/listings/" + listing, 403), 403, "FORBIDDEN");
        expectError(put(newUser("Out"), "/api/v1/listings/" + listing, body, 404), 404, "NOT_FOUND");
        put(s.owner(), "/api/v1/listings/" + listing, body, 200);
    }

    @Test
    void suspendedMembersCannotProposeOrSeeListings() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Mixer grinder", true);
        patch(s.mod(), "/api/v1/circles/" + s.circleId() + "/members/" + s.other().id(), Map.of("status", "SUSPENDED"), 200);
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 403), 403, "FORBIDDEN");
        expectError(get(s.other(), "/api/v1/listings/" + listing, 404), 404, "NOT_FOUND");
        expectError(post(s.other(), "/api/v1/circles/" + s.circleId() + "/join", null, 403), 403, "FORBIDDEN");
    }

    @Test
    void ownerCannotProposeOnOwnListing() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Own thing", true);
        expectError(post(s.owner(), "/api/v1/handoffs", Map.of("listingId", listing), 409), 409, "CONFLICT");
    }

    @Test
    void unknownRoutesReturnJsonNotFound() {
        Actor a = newUser("Lost");
        expectError(get(a, "/api/v1/nope", 404), 404, "NOT_FOUND");
        expectError(get(a, "/api/v1/listings/999999999", 404), 404, "NOT_FOUND");
        expectError(get(a, "/api/v1/listings/abc", 400), 400, "VALIDATION_FAILED");
    }

    @Test
    void notificationsAreOnlyVisibleToTheirOwner() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Bike pump", true);
        propose(s.other(), listing);
        JsonNode mine = get(s.owner(), "/api/v1/notifications", 200);
        long id = mine.path("content").get(0).path("id").asLong();
        expectError(post(s.other(), "/api/v1/notifications/" + id + "/read", null, 404), 404, "NOT_FOUND");
        post(s.owner(), "/api/v1/notifications/" + id + "/read", null, 204);
    }
}
