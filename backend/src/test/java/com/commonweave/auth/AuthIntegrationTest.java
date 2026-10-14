package com.commonweave.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonweave.support.IntegrationTest;
import com.commonweave.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AuthIntegrationTest extends IntegrationTest {

    private String email() {
        return "signup-" + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
    }

    private Map<String, String> signupBody(String email) {
        return Map.of("email", email, "password", "longenough1", "displayName", "Test Person");
    }

    @Test
    void signupCreatesAccountStartsSessionAndNeverExposesHash() throws Exception {
        String email = email().toUpperCase();
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(signupBody(email)))).andExpect(status().isCreated()).andReturn();
        String body = r.getResponse().getContentAsString();
        JsonNode user = json.readTree(body);
        assertEquals(email.toLowerCase(), user.path("email").asText());
        assertEquals("USER", user.path("platformRole").asText());
        assertFalse(user.path("onboarded").asBoolean());
        assertTrue(user.path("circles").isArray());
        assertFalse(body.contains("password"), body);
        assertFalse(body.contains("$2"), body);
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        assertNotNull(session);
        JsonNode me = get(new Actor(user.path("id").asLong(), email, session), "/api/v1/auth/me", 200);
        assertEquals(user.path("id").asLong(), me.path("id").asLong());
        User stored = users.findByEmail(email.toLowerCase()).orElseThrow();
        assertTrue(encoder.matches("longenough1", stored.getPasswordHash()));
        assertNotNull(stored.getLastLoginAt());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() {
        String email = email();
        post(null, "/api/v1/auth/signup", signupBody(email), 201);
        JsonNode err = post(null, "/api/v1/auth/signup", signupBody(email.toUpperCase()), 409);
        expectError(err, 409, "EMAIL_TAKEN");
    }

    @Test
    void signupValidationReportsFieldErrors() {
        JsonNode err = post(null, "/api/v1/auth/signup",
                Map.of("email", "not-an-email", "password", "short", "displayName", " "), 400);
        expectError(err, 400, "VALIDATION_FAILED");
        List<String> fields = err.path("fieldErrors").findValuesAsText("field");
        assertTrue(fields.containsAll(List.of("email", "password", "displayName")), fields.toString());
        assertEquals("/api/v1/auth/signup", err.path("path").asText());
        assertFalse(err.path("timestamp").asText().isBlank());
    }

    @Test
    void malformedJsonIsAValidationError() throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{not json")).andExpect(status().isBadRequest()).andReturn();
        assertEquals("VALIDATION_FAILED", json.readTree(r.getResponse().getContentAsString()).path("code").asText());
    }

    @Test
    void loginWorksCaseInsensitivelyAndWrongPasswordIs401() {
        Actor a = newUser("Login");
        post(null, "/api/v1/auth/login", Map.of("email", a.email().toUpperCase(), "password", PASSWORD), 200);
        JsonNode err = post(null, "/api/v1/auth/login", Map.of("email", a.email(), "password", "wrong-password"), 401);
        expectError(err, 401, "UNAUTHENTICATED");
        expectError(post(null, "/api/v1/auth/login", Map.of("email", "nobody@test.example", "password", "x"), 401),
                401, "UNAUTHENTICATED");
    }

    @Test
    void loginAcceptsAPlainUsername() {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setEmail(username);
        u.setPasswordHash(encoder.encode("pw"));
        u.setDisplayName("Plain Username");
        users.saveAndFlush(u);
        JsonNode me = post(null, "/api/v1/auth/login", Map.of("email", username, "password", "pw"), 200);
        assertEquals(username, me.path("email").asText());
    }

    @Test
    void logoutInvalidatesTheSession() {
        Actor a = newUser("Logout");
        get(a, "/api/v1/auth/me", 200);
        post(a, "/api/v1/auth/logout", null, 204);
        expectError(get(a, "/api/v1/auth/me", 401), 401, "UNAUTHENTICATED");
    }

    @Test
    void anonymousIsRejectedOnProtectedEndpointsButMetaIsPublic() {
        expectError(get(null, "/api/v1/listings", 401), 401, "UNAUTHENTICATED");
        expectError(get(null, "/api/v1/dashboard", 401), 401, "UNAUTHENTICATED");
        JsonNode meta = get(null, "/api/v1/meta", 200);
        assertEquals("INR", meta.path("currency").asText());
        assertEquals("Asia/Kolkata", meta.path("timezone").asText());
        assertEquals(12, meta.path("categories").size());
    }

    @Test
    void disabledAccountCannotLogInAndLosesExistingSession() {
        Actor a = newUser("Disabled");
        get(a, "/api/v1/auth/me", 200);
        User u = users.findById(a.id()).orElseThrow();
        u.setEnabled(false);
        users.saveAndFlush(u);
        expectError(get(a, "/api/v1/auth/me", 403), 403, "ACCOUNT_DISABLED");
        expectError(post(null, "/api/v1/auth/login", Map.of("email", a.email(), "password", PASSWORD), 403),
                403, "ACCOUNT_DISABLED");
        expectError(post(null, "/api/v1/auth/login", Map.of("email", a.email(), "password", "wrong-one"), 401),
                401, "UNAUTHENTICATED");
    }

    @Test
    void profileCanBeUpdated() {
        Actor a = newUser("Profile");
        JsonNode u = patch(a, "/api/v1/users/me", Map.of("displayName", "New Name", "bio", "Hello",
                "locality", "Indiranagar", "onboarded", true), 200);
        assertEquals("New Name", u.path("displayName").asText());
        assertEquals("Indiranagar", u.path("locality").asText());
        expectError(patch(a, "/api/v1/users/me", Map.of("displayName", "  "), 400), 400, "VALIDATION_FAILED");
    }
}
