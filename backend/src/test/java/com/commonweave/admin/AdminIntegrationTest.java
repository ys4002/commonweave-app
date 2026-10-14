package com.commonweave.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.commonweave.user.PlatformRole;
import com.commonweave.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminIntegrationTest extends IntegrationTest {

    private static final List<String> ADMIN_GETS = List.of("/overview", "/users", "/users/1", "/circles", "/listings",
            "/handoffs", "/reports", "/audit-log");

    @Test
    void everyAdminEndpointIsForbiddenForNonAdminsAndUnauthorizedForAnonymous() {
        Actor user = newUser("Regular");
        for (String path : ADMIN_GETS) {
            expectError(get(user, "/api/v1/admin" + path, 403), 403, "FORBIDDEN");
            expectError(get(null, "/api/v1/admin" + path, 401), 401, "UNAUTHENTICATED");
        }
        expectError(patch(user, "/api/v1/admin/users/" + user.id(), Map.of("platformRole", "ADMIN"), 403), 403, "FORBIDDEN");
        expectError(post(user, "/api/v1/admin/users/" + user.id() + "/reset-password", Map.of("newPassword", "newpassword1"), 403),
                403, "FORBIDDEN");
        expectError(post(user, "/api/v1/admin/listings/1/remove", Map.of("note", "x"), 403), 403, "FORBIDDEN");
        assertEquals("USER", users.findById(user.id()).orElseThrow().getPlatformRole().name());
    }

    @Test
    void circleModeratorIsNotAPlatformAdmin() {
        Setup s = setup();
        expectError(get(s.mod(), "/api/v1/admin/overview", 403), 403, "FORBIDDEN");
    }

    @Test
    void adminSeesOverviewAndListsWithoutPasswordHashes() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Admin visible", true);
        propose(s.other(), listing);
        Actor admin = newAdmin("Boss");

        JsonNode overview = get(admin, "/api/v1/admin/overview", 200);
        assertTrue(overview.path("users").path("total").asLong() >= 4);
        assertTrue(overview.path("circles").asLong() >= 1);
        for (String k : List.of("ACTIVE", "RESERVED", "CLOSED", "REMOVED")) {
            assertTrue(overview.path("listings").has(k));
        }
        for (String k : List.of("PROPOSED", "SCHEDULED", "HANDED_OFF", "RETURNED", "COMPLETED", "CANCELLED")) {
            assertTrue(overview.path("handoffs").has(k));
        }
        assertTrue(overview.has("openReports"));

        for (String path : List.of("/users?size=50", "/users/" + s.owner().id(), "/circles", "/listings",
                "/handoffs", "/reports", "/audit-log")) {
            String body = get(admin, "/api/v1/admin" + path, 200).toString();
            assertFalse(body.contains("$2a$") || body.contains("$2b$") || body.toLowerCase().contains("passwordhash")
                    || body.toLowerCase().contains("password_hash"), path);
        }
        JsonNode found = get(admin, "/api/v1/admin/users?q=" + s.owner().email().substring(0, 14), 200);
        assertEquals(s.owner().id(), found.path("content").get(0).path("id").asLong());
        JsonNode detail = get(admin, "/api/v1/admin/users/" + s.owner().id(), 200);
        assertEquals(1, detail.path("recentListings").size());
        assertEquals(1, detail.path("recentHandoffs").size());
        assertEquals(1, detail.path("listingCount").asLong());
        assertEquals(s.circleId(), detail.path("circles").get(0).path("circleId").asLong());
        assertTrue(get(admin, "/api/v1/admin/handoffs?status=PROPOSED&size=50", 200).path("totalElements").asLong() >= 1);
        assertEquals(listing, get(admin, "/api/v1/admin/listings?q=admin visible", 200).path("content").get(0).path("id").asLong());
        expectError(get(admin, "/api/v1/admin/users/999999999", 404), 404, "NOT_FOUND");
    }

    @Test
    void disablingAUserBlocksLoginAndKillsTheSessionAndIsAudited() {
        Actor admin = newAdmin("Boss");
        Actor victim = newUser("Victim");
        get(victim, "/api/v1/auth/me", 200);
        JsonNode updated = patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("enabled", false), 200);
        assertEquals(false, updated.path("enabled").asBoolean());
        expectError(get(victim, "/api/v1/auth/me", 403), 403, "ACCOUNT_DISABLED");
        expectError(post(null, "/api/v1/auth/login", Map.of("email", victim.email(), "password", PASSWORD), 403),
                403, "ACCOUNT_DISABLED");
        assertEquals(1, get(admin, "/api/v1/admin/users?enabled=false&q=" + victim.email(), 200).path("totalElements").asInt());

        patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("enabled", true), 200);
        post(null, "/api/v1/auth/login", Map.of("email", victim.email(), "password", PASSWORD), 200);

        JsonNode audit = get(admin, "/api/v1/admin/audit-log?size=10", 200);
        List<String> actions = audit.path("content").findValuesAsText("action");
        assertTrue(actions.contains("USER_DISABLED") && actions.contains("USER_ENABLED"), actions.toString());
        assertEquals(admin.id(), audit.path("content").get(0).path("admin").path("id").asLong());
        assertEquals("USER", audit.path("content").get(0).path("targetType").asText());
    }

    @Test
    void adminCannotDisableOrDemoteSelfAndLastAdminIsProtected() {
        List<Long> others = jdbc.queryForList("select id from commonweave_users where platform_role = 'ADMIN' and enabled = true",
                Long.class);
        Actor only = newAdmin("OnlyAdmin");
        try {
            others.forEach(id -> jdbc.update("update commonweave_users set enabled = false where id = ?", id));
            String self = "/api/v1/admin/users/" + only.id();
            expectError(patch(only, self, Map.of("enabled", false), 409), 409, "CONFLICT");
            expectError(patch(only, self, Map.of("platformRole", "USER"), 409), 409, "CONFLICT");
            assertEquals("ADMIN", get(only, self, 200).path("platformRole").asText());

            Actor second = newAdmin("SecondAdmin");
            patch(second, "/api/v1/admin/users/" + only.id(), Map.of("platformRole", "USER"), 200);
            expectError(get(only, "/api/v1/admin/overview", 401), 401, "UNAUTHENTICATED");
            expectError(patch(second, "/api/v1/admin/users/" + second.id(), Map.of("platformRole", "USER"), 409), 409, "CONFLICT");
            jdbc.update("update commonweave_users set enabled = false where id = ?", second.id());
        } finally {
            others.forEach(id -> jdbc.update("update commonweave_users set enabled = true where id = ?", id));
        }
    }

    @Test
    void promotionTakesEffectOnTheExistingSession() {
        Actor admin = newAdmin("Boss");
        Actor user = newUser("Rising");
        expectError(get(user, "/api/v1/admin/overview", 403), 403, "FORBIDDEN");
        patch(admin, "/api/v1/admin/users/" + user.id(), Map.of("platformRole", "ADMIN"), 200);
        expectError(get(user, "/api/v1/admin/overview", 401), 401, "UNAUTHENTICATED"); // old session ended
        Actor again = login(user.id(), user.email(), PASSWORD);
        get(again, "/api/v1/admin/overview", 200);
        assertEquals("ADMIN", get(again, "/api/v1/auth/me", 200).path("platformRole").asText());
    }

    @Test
    void resetPasswordReplacesTheCredentialAndNeverEchoesIt() {
        Actor admin = newAdmin("Boss");
        Actor user = newUser("Forgetful");
        expectError(post(admin, "/api/v1/admin/users/" + user.id() + "/reset-password", Map.of("newPassword", "short"), 400),
                400, "VALIDATION_FAILED");
        post(admin, "/api/v1/admin/users/" + user.id() + "/reset-password", Map.of("newPassword", "brand-new-pass"), 204);
        expectError(post(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", PASSWORD), 401), 401, "UNAUTHENTICATED");
        post(null, "/api/v1/auth/login", Map.of("email", user.email(), "password", "brand-new-pass"), 200);
        User stored = users.findById(user.id()).orElseThrow();
        assertTrue(encoder.matches("brand-new-pass", stored.getPasswordHash()));
        String audit = get(admin, "/api/v1/admin/audit-log?size=5", 200).toString();
        assertTrue(audit.contains("PASSWORD_RESET"));
        assertFalse(audit.contains("brand-new-pass"));
    }

    @Test
    void adminCanRenameAndRemoveListingsAndIsNotAHandoffParticipant() {
        Setup s = setup();
        Actor admin = newAdmin("Boss");
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Admin removal target", true);
        long proposal = propose(s.other(), listing);
        JsonNode renamed = patch(admin, "/api/v1/admin/users/" + s.owner().id(), Map.of("displayName", "Renamed Owner"), 200);
        assertEquals("Renamed Owner", renamed.path("displayName").asText());

        expectError(post(admin, "/api/v1/handoffs", Map.of("listingId", listing), 403), 403, "FORBIDDEN");
        expectError(get(admin, "/api/v1/handoffs/" + proposal, 404), 404, "NOT_FOUND");

        post(admin, "/api/v1/admin/listings/" + listing + "/remove", Map.of("note", "policy"), 204);
        assertEquals("REMOVED", listingStatus(s.owner(), listing));
        assertEquals("CANCELLED", get(s.other(), "/api/v1/handoffs/" + proposal, 200).path("status").asText());
        assertTrue(get(admin, "/api/v1/admin/audit-log?size=20", 200).path("content").findValuesAsText("action")
                .contains("LISTING_REMOVED"));
        assertTrue(get(s.mod(), "/api/v1/circles/" + s.circleId() + "/moderation-log", 200).size() >= 1);
        assertEquals(PlatformRole.USER, users.findById(s.owner().id()).orElseThrow().getPlatformRole());
    }

    @Test
    void adminPatchValidatesInput() {
        Actor admin = newAdmin("Boss");
        Actor user = newUser("Target");
        expectError(patch(admin, "/api/v1/admin/users/" + user.id(), Map.of("platformRole", "SUPERUSER"), 400), 400, "VALIDATION_FAILED");
        expectError(patch(admin, "/api/v1/admin/users/" + user.id(), Map.of("displayName", "x".repeat(81)), 400), 400, "VALIDATION_FAILED");
    }
}
