package com.commonweave.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.commonweave.support.IntegrationTest;
import com.commonweave.user.PlatformRole;
import com.commonweave.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** One regression test (or more) per finding of docs/security-review.md. */
class SecurityReviewFixesIntegrationTest extends IntegrationTest {
    @Value("${server.servlet.session.cookie.secure}") boolean sessionCookieSecure;

    private void suspend(Setup s, Actor member) {
        patch(s.mod(), "/api/v1/circles/" + s.circleId() + "/members/" + member.id(), Map.of("status", "SUSPENDED"), 200);
    }

    private void reinstate(Setup s, Actor member) {
        patch(s.mod(), "/api/v1/circles/" + s.circleId() + "/members/" + member.id(), Map.of("status", "ACTIVE"), 200);
    }

    // ---- M1 ---------------------------------------------------------------------------------------------------

    @Test
    void m1_formerMembersCannotReadMatchesThroughTheirOldListings() {
        Setup s = setup();
        long mine = createListing(s.owner(), s.circleId(), "OFFER", "Old drill", true);
        createListing(s.other(), s.circleId(), "REQUEST", "Old drill wanted", true);
        assertEquals(1, get(s.owner(), "/api/v1/listings/" + mine + "/matches", 200).size());

        suspend(s, s.owner());
        expectError(get(s.owner(), "/api/v1/listings/" + mine + "/matches", 404), 404, "NOT_FOUND");
        reinstate(s, s.owner());
        assertEquals(1, get(s.owner(), "/api/v1/listings/" + mine + "/matches", 200).size());

        post(s.owner(), "/api/v1/circles/" + s.circleId() + "/leave", null, 204);
        expectError(get(s.owner(), "/api/v1/listings/" + mine + "/matches", 404), 404, "NOT_FOUND");
    }

    // ---- M2 ---------------------------------------------------------------------------------------------------

    @Test
    void m2_suspendedMembersCannotEditOrCloseAndTheirListingsDisappearUntilReinstated() {
        Setup s = setup();
        long offer = createListing(s.owner(), s.circleId(), "OFFER", "Hidden when suspended", true);
        long wanted = createListing(s.other(), s.circleId(), "REQUEST", "Somebody wants this", true);
        long otherListing = createListing(s.other(), s.circleId(), "OFFER", "Other thing", true);
        String list = "/api/v1/listings?circleId=" + s.circleId();
        assertEquals(3, get(s.other(), list, 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), "/api/v1/listings/" + wanted + "/matches", 200).size());

        suspend(s, s.owner());

        Map<String, Object> body = listingBody(s.circleId(), "OFFER", "ITEM", "Repost abuse", "TOOLS", true);
        expectError(put(s.owner(), "/api/v1/listings/" + offer, body, 403), 403, "FORBIDDEN");
        expectError(delete(s.owner(), "/api/v1/listings/" + offer, 403), 403, "FORBIDDEN");
        expectError(get(s.other(), "/api/v1/listings/" + offer, 404), 404, "NOT_FOUND");
        assertEquals(2, get(s.other(), list, 200).path("totalElements").asInt());
        assertFalse(get(s.other(), list, 200).toString().contains("\"id\":" + offer + ","));
        assertEquals(0, get(s.other(), "/api/v1/listings/" + wanted + "/matches", 200).size());
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", offer), 404), 404, "NOT_FOUND");
        assertEquals(2, get(s.other(), "/api/v1/circles/" + s.circleId(), 200).path("activeListingCount").asInt());
        assertTrue(get(s.other(), "/api/v1/circles/" + s.circleId() + "/activity", 200).toString().contains("Other thing"));
        assertFalse(get(s.other(), "/api/v1/circles/" + s.circleId() + "/activity", 200).toString().contains("Hidden when"));

        reinstate(s, s.owner());
        assertEquals(3, get(s.other(), list, 200).path("totalElements").asInt());
        assertEquals(1, get(s.other(), "/api/v1/listings/" + wanted + "/matches", 200).size());
        put(s.owner(), "/api/v1/listings/" + offer, body, 200);
        delete(s.owner(), "/api/v1/listings/" + offer, 204);
    }

    // ---- M3 ---------------------------------------------------------------------------------------------------

    private MvcResult login(String ip, String email, String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf())
                .with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    }

    @Test
    void m3_loginIsThrottledPerIdentifierAndIpWithRetryAfter() throws Exception {
        Actor a = newUser("Throttled");
        String ip = "10.20.30." + (int) (Math.random() * 200 + 1);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(ip, a.email(), "wrong-password-" + i).getResponse().getStatus());
        }
        MvcResult blocked = login(ip, a.email(), "wrong-password-again");
        assertEquals(429, blocked.getResponse().getStatus());
        expectError(json.readTree(blocked.getResponse().getContentAsString()), 429, "TOO_MANY_REQUESTS");
        long retry = Long.parseLong(blocked.getResponse().getHeader("Retry-After"));
        assertTrue(retry > 0 && retry <= 900, "retry-after " + retry);
        assertFalse(blocked.getResponse().getContentAsString().contains("wrong-password"));
        // even the correct password is refused while blocked
        assertEquals(429, login(ip, a.email(), PASSWORD).getResponse().getStatus());
        // other client address and other account are unaffected
        assertEquals(200, login("10.99.99.99", a.email(), PASSWORD).getResponse().getStatus());
        assertEquals(401, login(ip, "someone-else@test.example", "x").getResponse().getStatus());
    }

    @Test
    void m3_successfulLoginResetsTheFailureCounter() throws Exception {
        Actor a = newUser("Resets");
        String ip = "10.50.60." + (int) (Math.random() * 200 + 1);
        for (int i = 0; i < 4; i++) {
            assertEquals(401, login(ip, a.email(), "nope" + i).getResponse().getStatus());
        }
        assertEquals(200, login(ip, a.email(), PASSWORD).getResponse().getStatus());
        for (int i = 0; i < 4; i++) {
            assertEquals(401, login(ip, a.email(), "nope" + i).getResponse().getStatus());
        }
    }

    @Test
    void m3_signupIsThrottledPerIp() throws Exception {
        String ip = "10.70.80." + (int) (Math.random() * 200 + 1);
        for (int i = 0; i < 10; i++) {
            MvcResult r = signup(ip);
            assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        }
        MvcResult blocked = signup(ip);
        assertEquals(429, blocked.getResponse().getStatus());
        assertTrue(Long.parseLong(blocked.getResponse().getHeader("Retry-After")) > 0);
        assertEquals(201, signup("10.71.81.91").getResponse().getStatus());
    }

    private MvcResult signup(String ip) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/signup").with(csrf())
                .with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "t-" + UUID.randomUUID() + "@test.example",
                        "password", "longenough1", "displayName", "Throttle Test")))).andReturn();
    }

    // ---- L1 ---------------------------------------------------------------------------------------------------

    @Test
    void l1_cookiesAreSecureByDefaultAndOnlyDevTurnsItOff() {
        assertTrue(sessionCookieSecure, "session cookie must be Secure unless profile dev");
        var dev = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        dev.setResources(new org.springframework.core.io.ClassPathResource("application-dev.yml"));
        assertEquals("false", dev.getObject().getProperty("app.security.secure-cookies"));
        var base = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        base.setResources(new org.springframework.core.io.ClassPathResource("application.yml"));
        assertEquals("${APP_COOKIE_SECURE:true}", base.getObject().getProperty("app.security.secure-cookies"));
        assertEquals("${app.security.secure-cookies}", base.getObject().getProperty("server.servlet.session.cookie.secure"));
    }

    // ---- L2 ---------------------------------------------------------------------------------------------------

    @Test
    void l2_resetDisableAndRoleChangeEndTheTargetsExistingSessions() {
        Actor admin = newAdmin("Boss");
        Actor victim = newUser("Victim");
        get(victim, "/api/v1/auth/me", 200);
        post(admin, "/api/v1/admin/users/" + victim.id() + "/reset-password", Map.of("newPassword", "another-pass-1"), 204);
        expectError(get(victim, "/api/v1/auth/me", 401), 401, "UNAUTHENTICATED");

        Actor second = login(victim.id(), victim.email(), "another-pass-1");
        get(second, "/api/v1/auth/me", 200);
        patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("enabled", false), 200);
        patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("enabled", true), 200);
        expectError(get(second, "/api/v1/auth/me", 401), 401, "UNAUTHENTICATED"); // re-enable does not revive it

        Actor third = login(victim.id(), victim.email(), "another-pass-1");
        patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("platformRole", "ADMIN"), 200);
        expectError(get(third, "/api/v1/auth/me", 401), 401, "UNAUTHENTICATED");
        // renaming is not a security change and keeps the session
        Actor fourth = login(victim.id(), victim.email(), "another-pass-1");
        patch(admin, "/api/v1/admin/users/" + victim.id(), Map.of("displayName", "Renamed"), 200);
        get(fourth, "/api/v1/auth/me", 200);
    }

    // ---- L3 ---------------------------------------------------------------------------------------------------

    @Test
    void l3_concurrentMutualDemotionNeverLeavesZeroAdmins() throws Exception {
        List<Long> others = jdbc.queryForList("select id from commonweave_users where platform_role = 'ADMIN' and enabled = true", Long.class);
        try {
            others.forEach(id -> jdbc.update("update commonweave_users set enabled = false where id = ?", id));
            for (int round = 0; round < 3; round++) {
                Actor a = newAdmin("RaceA" + round);
                Actor b = newAdmin("RaceB" + round);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<Integer>> results = new ArrayList<>();
                for (Actor[] pair : new Actor[][] {{a, b}, {b, a}}) {
                    results.add(pool.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return mvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/users/" + pair[1].id())
                                .session(pair[0].session()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("platformRole", "USER"))))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> f : results) {
                    statuses.add(f.get(30, TimeUnit.SECONDS));
                }
                pool.shutdownNow();
                assertTrue(statuses.stream().filter(s -> s == 200).count() <= 1, "both demotions succeeded: " + statuses);
                int admins = jdbc.queryForObject("select count(*) from commonweave_users where id in (?, ?) and platform_role = 'ADMIN' and enabled = true",
                        Integer.class, a.id(), b.id());
                assertEquals(1, admins, "exactly one admin must remain, statuses=" + statuses);
                jdbc.update("update commonweave_users set enabled = false where id in (?, ?)", a.id(), b.id());
            }
        } finally {
            others.forEach(id -> jdbc.update("update commonweave_users set enabled = true where id = ?", id));
        }
    }

    // ---- L4 ---------------------------------------------------------------------------------------------------

    @Test
    void l4_adminsCannotJoinCreateOrTakePartInHandoffs() {
        Setup s = setup();
        Actor admin = newAdmin("Boss");
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Not for admins", true);
        expectError(post(admin, "/api/v1/circles/" + s.circleId() + "/join", null, 403), 403, "FORBIDDEN");
        expectError(post(admin, "/api/v1/circles", Map.of("name", "Admin circle", "city", "Delhi"), 403), 403, "FORBIDDEN");
        expectError(post(admin, "/api/v1/handoffs", Map.of("listingId", listing), 403), 403, "FORBIDDEN");
        expectError(post(admin, "/api/v1/listings",
                listingBody(s.circleId(), "OFFER", "ITEM", "Admin listing", "TOOLS", false), 403), 403, "FORBIDDEN");
        assertEquals(0, jdbc.queryForObject("select count(*) from memberships where user_id = ?", Integer.class, admin.id()));
    }

    @Test
    void l4_aMemberPromotedToAdminLosesTheAbilityToPropose() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Promoted proposer", true);
        User u = users.findById(s.other().id()).orElseThrow();
        u.setPlatformRole(PlatformRole.ADMIN);
        users.saveAndFlush(u);
        expectError(post(s.other(), "/api/v1/handoffs", Map.of("listingId", listing), 403), 403, "FORBIDDEN");
    }
}
