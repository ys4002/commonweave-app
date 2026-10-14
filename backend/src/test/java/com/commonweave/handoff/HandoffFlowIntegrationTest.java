package com.commonweave.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class HandoffFlowIntegrationTest extends IntegrationTest {

    private List<String> actions(JsonNode detail) {
        List<String> out = new ArrayList<>();
        detail.path("allowedActions").forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void fullLoanLifecycleUpdatesListingAndRecordsEvents() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Cordless drill", true);

        JsonNode proposed = post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing, "message", "Saturday?"), 201);
        long id = proposed.path("id").asLong();
        assertEquals("PROPOSED", proposed.path("status").asText());
        assertEquals("RECIPIENT", proposed.path("role").asText());
        assertEquals(List.of("CANCEL"), actions(proposed));
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));

        JsonNode asOwner = get(s.owner(), "/api/v1/handoffs/" + id, 200);
        assertEquals("PROVIDER", asOwner.path("role").asText());
        assertEquals(List.of("CONFIRM", "CANCEL"), actions(asOwner));

        JsonNode scheduled = confirm(s.owner(), id, 200);
        assertEquals("SCHEDULED", scheduled.path("status").asText());
        assertEquals("Gate 2", scheduled.path("pickupNote").asText());
        assertEquals("RESERVED", listingStatus(s.owner(), listing));
        assertEquals(List.of("MARK_HANDED_OFF", "CANCEL"), actions(scheduled));

        JsonNode handedOff = transition(s.other(), id, "MARK_HANDED_OFF", 200);
        assertEquals("HANDED_OFF", handedOff.path("status").asText());
        assertEquals(List.of("MARK_RETURNED"), actions(handedOff));
        assertEquals("RESERVED", listingStatus(s.owner(), listing));

        JsonNode returned = transition(s.owner(), id, "MARK_RETURNED", 200);
        assertEquals("RETURNED", returned.path("status").asText());
        assertEquals(List.of(), actions(returned));
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));

        JsonNode events = returned.path("events");
        assertEquals(4, events.size());
        assertTrue(events.get(0).path("fromStatus").isNull());
        assertEquals("PROPOSED", events.get(0).path("toStatus").asText());
        assertEquals("RETURNED", events.get(3).path("toStatus").asText());
        assertEquals(s.owner().id(), events.get(3).path("actor").path("id").asLong());

        JsonNode members = get(s.mod(), "/api/v1/circles/" + s.circleId() + "/members", 200);
        for (JsonNode m : members) {
            long expected = m.path("userId").asLong() == s.mod().id() ? 0 : 1;
            assertEquals(expected, m.path("completedExchanges").asLong(), m.toString());
        }
    }

    @Test
    void giftLifecycleClosesTheListing() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Old books", false);
        long id = propose(s.other(), listing);
        confirm(s.owner(), id, 200);
        JsonNode handedOff = transition(s.owner(), id, "MARK_HANDED_OFF", 200);
        assertEquals(List.of("COMPLETE"), actions(handedOff));
        expectError(transition(s.other(), id, "MARK_RETURNED", 409), 409, "INVALID_TRANSITION");
        assertEquals("COMPLETED", transition(s.other(), id, "COMPLETE", 200).path("status").asText());
        assertEquals("CLOSED", listingStatus(s.owner(), listing));
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 409), 409, "LISTING_UNAVAILABLE");
    }

    @Test
    void completeIsRejectedForLoansAndInvalidOrderIsRejected() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Ladder", true);
        long id = propose(s.other(), listing);
        expectError(transition(s.owner(), id, "MARK_HANDED_OFF", 409), 409, "INVALID_TRANSITION");
        expectError(transition(s.owner(), id, "COMPLETE", 409), 409, "INVALID_TRANSITION");
        confirm(s.owner(), id, 200);
        expectError(confirm(s.owner(), id, 409), 409, "INVALID_TRANSITION");
        expectError(transition(s.owner(), id, "COMPLETE", 409), 409, "INVALID_TRANSITION");
        expectError(transition(s.owner(), id, "MARK_RETURNED", 409), 409, "INVALID_TRANSITION");
        transition(s.owner(), id, "MARK_HANDED_OFF", 200);
        expectError(transition(s.owner(), id, "COMPLETE", 409), 409, "INVALID_TRANSITION");
        expectError(transition(s.owner(), id, "CANCEL", 409), 409, "INVALID_TRANSITION");
        transition(s.owner(), id, "MARK_RETURNED", 200);
        for (String action : List.of("CONFIRM", "MARK_HANDED_OFF", "MARK_RETURNED", "COMPLETE", "CANCEL")) {
            expectError(transition(s.owner(), id, action, 409), 409, "INVALID_TRANSITION");
        }
    }

    @Test
    void confirmValidatesRoleAndSchedule() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Tent", true);
        long id = propose(s.other(), listing);
        expectError(confirm(s.other(), id, 403), 403, "FORBIDDEN");
        JsonNode missing = transition(s.owner(), id, "CONFIRM", 400);
        expectError(missing, 400, "VALIDATION_FAILED");
        assertEquals("scheduledAt", missing.path("fieldErrors").get(0).path("field").asText());
        expectError(post(s.owner(), "/api/v1/handoffs/" + id + "/transitions", Map.of("action", "CONFIRM",
                "scheduledAt", Instant.now().minus(1, ChronoUnit.DAYS).toString()), 400), 400, "VALIDATION_FAILED");
        expectError(post(s.owner(), "/api/v1/handoffs/" + id + "/transitions", Map.of("action", "BOGUS"), 400),
                400, "VALIDATION_FAILED");
        assertEquals("PROPOSED", get(s.owner(), "/api/v1/handoffs/" + id, 200).path("status").asText());
    }

    @Test
    void cancellingAScheduledHandoffReleasesTheListing() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Projector", true);
        long first = propose(s.other(), listing);
        confirm(s.owner(), first, 200);
        assertEquals("RESERVED", listingStatus(s.owner(), listing));
        assertEquals("CANCELLED", transition(s.other(), first, "CANCEL", 200).path("status").asText());
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));
        long second = propose(s.other(), listing);
        confirm(s.owner(), second, 200);
        assertEquals("RESERVED", listingStatus(s.owner(), listing));
    }

    @Test
    void cancellingAProposalLeavesListingActive() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Camera", true);
        long id = propose(s.other(), listing);
        assertEquals("CANCELLED", transition(s.owner(), id, "CANCEL", 200).path("status").asText());
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));
    }

    @Test
    void secondProposalFromSameUserIsRejectedWhileOpenButAllowedAfterCancel() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Guitar", true);
        long id = propose(s.other(), listing);
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 409), 409, "CONFLICT");
        transition(s.other(), id, "CANCEL", 200);
        propose(s.other(), listing);
    }

    @Test
    void reservedListingRejectsNewProposalsAndSecondConfirm() {
        Setup s = setup();
        Actor third = newUser("Third");
        join(third, s.circleId());
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Sewing machine", true);
        long a = propose(s.other(), listing);
        long b = propose(third, listing);
        confirm(s.owner(), a, 200);
        expectError(confirm(s.owner(), b, 409), 409, "LISTING_UNAVAILABLE");
        assertEquals("PROPOSED", get(third, "/api/v1/handoffs/" + b, 200).path("status").asText());
        Actor fourth = newUser("Fourth");
        join(fourth, s.circleId());
        expectError(post(fourth, "/api/v1/handoffs", Map.of("listingId", listing), 409), 409, "LISTING_UNAVAILABLE");
    }

    @Test
    void reservedListingCannotBeEditedOrClosed() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Printer", true);
        confirm(s.owner(), propose(s.other(), listing), 200);
        expectError(put(s.owner(), "/api/v1/listings/" + listing,
                listingBody(s.circleId(), "OFFER", "ITEM", "Printer v2", "ELECTRONICS", true), 409), 409, "CONFLICT");
        expectError(delete(s.owner(), "/api/v1/listings/" + listing, 409), 409, "CONFLICT");
    }

    @Test
    void closingAListingCancelsOpenProposals() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Blender", true);
        long id = propose(s.other(), listing);
        delete(s.owner(), "/api/v1/listings/" + listing, 204);
        assertEquals("CLOSED", listingStatus(s.owner(), listing));
        assertEquals("CANCELLED", get(s.other(), "/api/v1/handoffs/" + id, 200).path("status").asText());
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 409), 409, "LISTING_UNAVAILABLE");
    }

    @Test
    void requestListingsMakeTheVolunteerTheProvider() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "REQUEST", "Need a drill", true);
        JsonNode proposed = post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 201);
        long id = proposed.path("id").asLong();
        assertEquals("PROVIDER", proposed.path("role").asText());
        assertEquals(s.owner().id(), proposed.path("counterpart").path("id").asLong());
        expectError(confirm(s.other(), id, 403), 403, "FORBIDDEN");
        JsonNode asOwner = confirm(s.owner(), id, 200);
        assertEquals("RECIPIENT", asOwner.path("role").asText());
        transition(s.other(), id, "MARK_HANDED_OFF", 200);
        transition(s.owner(), id, "MARK_RETURNED", 200);
        assertEquals("ACTIVE", listingStatus(s.owner(), listing));
    }

    @Test
    void handoffListFiltersByStatusActiveAndRole() {
        Setup s = setup();
        long l1 = createListing(s.owner(), s.circleId(), "OFFER", "Item one", true);
        long l2 = createListing(s.owner(), s.circleId(), "OFFER", "Item two", true);
        long h1 = propose(s.other(), l1);
        long h2 = propose(s.other(), l2);
        transition(s.other(), h2, "CANCEL", 200);
        assertEquals(2, get(s.other(), "/api/v1/handoffs", 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), "/api/v1/handoffs?active=true", 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), "/api/v1/handoffs?status=CANCELLED", 200).path("totalElements").asInt());
        assertEquals(2, get(s.other(), "/api/v1/handoffs?role=RECIPIENT", 200).path("totalElements").asInt());
        assertEquals(0, get(s.other(), "/api/v1/handoffs?role=PROVIDER", 200).path("totalElements").asInt());
        assertEquals(h1, get(s.owner(), "/api/v1/handoffs?active=true", 200).path("content").get(0).path("id").asLong());
    }

    @Test
    void everyTransitionNotifiesTheOtherParty() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Fan", false);
        long id = propose(s.other(), listing);
        assertEquals(1, get(s.owner(), "/api/v1/notifications/unread-count", 200).path("count").asInt());
        confirm(s.owner(), id, 200);
        assertEquals(1, get(s.other(), "/api/v1/notifications?unread=true", 200).path("totalElements").asInt());
        transition(s.other(), id, "MARK_HANDED_OFF", 200);
        transition(s.owner(), id, "COMPLETE", 200);
        JsonNode ownerNotes = get(s.owner(), "/api/v1/notifications", 200);
        assertEquals(2, ownerNotes.path("totalElements").asInt());
        assertEquals("/handoffs/" + id, ownerNotes.path("content").get(0).path("link").asText());
        post(s.owner(), "/api/v1/notifications/read-all", null, 204);
        assertEquals(0, get(s.owner(), "/api/v1/notifications/unread-count", 200).path("count").asInt());
    }

    @Test
    void databaseItselfRefusesTwoActiveHandoffsOnOneListing() {
        Setup s = setup();
        Actor third = newUser("Third");
        join(third, s.circleId());
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Backstop item", true);
        long a = propose(s.other(), listing);
        long b = propose(third, listing);
        confirm(s.owner(), a, 200);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update handoffs set status = 'SCHEDULED' where id = ?", b));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from handoffs where listing_id = ? and status in ('SCHEDULED','HANDED_OFF')",
                Integer.class, listing));
    }
}
