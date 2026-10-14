package com.commonweave.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListingIntegrationTest extends IntegrationTest {

    @Test
    void createReturnsDetailWithOwnerAndDefaults() {
        Setup s = setup();
        JsonNode created = post(s.owner(), "/api/v1/listings",
                listingBody(s.circleId(), "OFFER", "ITEM", "  Bosch drill ", "TOOLS", true), 201);
        assertEquals("Bosch drill", created.path("title").asText());
        assertEquals("ACTIVE", created.path("status").asText());
        assertEquals(s.owner().id(), created.path("owner").path("id").asLong());
        assertEquals(List.of("tag1", "tag2"), List.of(created.path("tags").get(0).asText(), created.path("tags").get(1).asText()));
        assertTrue(created.path("canEdit").asBoolean());
        assertEquals(1000, created.path("estimatedValueInr").asInt());
        assertEquals(0, created.path("completedExchangesOfOwner").asInt());
        assertTrue(created.path("myHandoff").isNull());
        JsonNode other = get(s.other(), "/api/v1/listings/" + created.path("id").asLong(), 200);
        assertEquals(false, other.path("canEdit").asBoolean());
    }

    @Test
    void skillListingsNeverExpectAReturn() {
        Setup s = setup();
        Map<String, Object> body = new HashMap<>(listingBody(s.circleId(), "OFFER", "SKILL", "Hindi lessons", "TUTORING", true));
        JsonNode created = post(s.owner(), "/api/v1/listings", body, 201);
        assertEquals(false, created.path("returnExpected").asBoolean());
    }

    @Test
    void validationErrorsAreReportedPerField() {
        Setup s = setup();
        Map<String, Object> body = new HashMap<>(listingBody(s.circleId(), "OFFER", "ITEM", "ab", "TOOLS", false));
        body.put("tags", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
        body.put("estimatedValueInr", 600000);
        body.put("depositInr", -1);
        body.put("hoursEstimate", 101);
        body.put("pickupPreference", "x".repeat(201));
        JsonNode err = post(s.owner(), "/api/v1/listings", body, 400);
        expectError(err, 400, "VALIDATION_FAILED");
        List<String> fields = err.path("fieldErrors").findValuesAsText("field");
        assertTrue(fields.containsAll(List.of("title", "tags", "estimatedValueInr", "depositInr", "hoursEstimate",
                "pickupPreference")), fields.toString());
    }

    @Test
    void dateOrderAndRequiredFieldsAreEnforced() {
        Setup s = setup();
        Map<String, Object> body = new HashMap<>(listingBody(s.circleId(), "OFFER", "ITEM", "Valid title", "TOOLS", false));
        body.put("availableFrom", LocalDate.now().plusDays(5).toString());
        body.put("availableTo", LocalDate.now().toString());
        JsonNode err = post(s.owner(), "/api/v1/listings", body, 400);
        assertEquals("availableTo", err.path("fieldErrors").get(0).path("field").asText());
        body.remove("circleId");
        body.put("availableFrom", LocalDate.now().toString());
        expectError(post(s.owner(), "/api/v1/listings", body, 400), 400, "VALIDATION_FAILED");
        body.put("circleId", s.circleId());
        body.put("category", "NOT_A_CATEGORY");
        expectError(post(s.owner(), "/api/v1/listings", body, 400), 400, "VALIDATION_FAILED");
    }

    @Test
    void searchFiltersAndPaginates() {
        Setup s = setup();
        long drill = createListing(s.owner(), s.circleId(), "OFFER", "Searchable drill machine", true);
        post(s.owner(), "/api/v1/listings", listingBody(s.circleId(), "REQUEST", "SKILL", "Hindi help wanted",
                "TUTORING", false), 201);
        String base = "/api/v1/listings?circleId=" + s.circleId();
        assertEquals(2, get(s.other(), base, 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), base + "&type=OFFER", 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), base + "&category=TUTORING", 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), base + "&kind=SKILL", 200).path("totalElements").asInt());
        assertEquals(drill, get(s.other(), base + "&q=DRILL", 200).path("content").get(0).path("id").asLong());
        assertEquals(1, get(s.other(), base + "&q=tag2&type=OFFER", 200).path("totalElements").asInt());
        assertEquals(0, get(s.other(), base + "&q=100%", 200).path("totalElements").asInt());
        assertEquals(2, get(s.other(), base + "&availableOn=" + LocalDate.now(), 200).path("totalElements").asInt());
        assertEquals(0, get(s.other(), base + "&availableOn=" + LocalDate.now().plusDays(90), 200).path("totalElements").asInt());
        JsonNode page = get(s.other(), base + "&size=1&page=1", 200);
        assertEquals(1, page.path("size").asInt());
        assertEquals(1, page.path("page").asInt());
        assertEquals(2, page.path("totalPages").asInt());
        assertEquals(50, get(s.other(), base + "&size=500", 200).path("size").asInt());
        assertEquals(2, get(s.owner(), base + "&mine=true", 200).path("totalElements").asInt());
        assertEquals(0, get(s.other(), base + "&mine=true", 200).path("totalElements").asInt());
        assertEquals(2, get(s.other(), base + "&sort=soonest", 200).path("totalElements").asInt());
    }

    @Test
    void defaultListShowsActiveAndReservedOnlyAndStatusFilterOverrides() {
        Setup s = setup();
        long closed = createListing(s.owner(), s.circleId(), "OFFER", "Closed one", true);
        long active = createListing(s.owner(), s.circleId(), "OFFER", "Active one", true);
        delete(s.owner(), "/api/v1/listings/" + closed, 204);
        String base = "/api/v1/listings?circleId=" + s.circleId();
        assertEquals(1, get(s.other(), base, 200).path("totalElements").asInt());
        assertEquals(active, get(s.other(), base, 200).path("content").get(0).path("id").asLong());
        assertEquals(closed, get(s.other(), base + "&status=CLOSED", 200).path("content").get(0).path("id").asLong());
        assertEquals(2, get(s.owner(), base + "&mine=true", 200).path("totalElements").asInt());
    }

    @Test
    void updateChangesFieldsAndKeepsCircle() {
        Setup s = setup();
        long id = createListing(s.owner(), s.circleId(), "OFFER", "Before", true);
        Map<String, Object> body = new HashMap<>(listingBody(s.circleId(), "OFFER", "ITEM", "After edit", "KITCHEN", false));
        JsonNode updated = put(s.owner(), "/api/v1/listings/" + id, body, 200);
        assertEquals("After edit", updated.path("title").asText());
        assertEquals("KITCHEN", updated.path("category").asText());
        assertEquals(s.circleId(), updated.path("circleId").asLong());
    }

    @Test
    void removedListingsAreHiddenFromMembersButNotOwnerOrModerator() {
        Setup s = setup();
        long id = createListing(s.owner(), s.circleId(), "OFFER", "Will be removed", true);
        post(s.mod(), "/api/v1/circles/" + s.circleId() + "/listings/" + id + "/remove", Map.of("note", "spam"), 204);
        expectError(get(s.other(), "/api/v1/listings/" + id, 404), 404, "NOT_FOUND");
        assertEquals("REMOVED", get(s.owner(), "/api/v1/listings/" + id, 200).path("status").asText());
        assertEquals("REMOVED", get(s.mod(), "/api/v1/listings/" + id, 200).path("status").asText());
        assertEquals(0, get(s.other(), "/api/v1/listings?circleId=" + s.circleId(), 200).path("totalElements").asInt());
    }
}
