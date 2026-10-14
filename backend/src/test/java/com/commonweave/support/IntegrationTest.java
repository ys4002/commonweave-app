package com.commonweave.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonweave.user.PlatformRole;
import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real MySQL (commonweave_test), real security filter chain, real sessions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    public static final String PASSWORD = "Sup3rSecret!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JdbcTemplate jdbc;

    public record Actor(long id, String email, MockHttpSession session) {
    }

    // ---- actors ----------------------------------------------------------------------------------------------

    protected Actor newUser(String name) {
        return newUser(name, PlatformRole.USER);
    }

    protected Actor newAdmin(String name) {
        return newUser(name, PlatformRole.ADMIN);
    }

    private Actor newUser(String name, PlatformRole role) {
        User u = new User();
        u.setEmail(name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.example");
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setDisplayName(name);
        u.setOnboarded(true);
        u.setPlatformRole(role);
        users.saveAndFlush(u);
        return login(u.getId(), u.getEmail(), PASSWORD);
    }

    protected Actor login(long id, String username, String password) {
        try {
            MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", username, "password", password))))
                    .andExpect(status().isOk()).andReturn();
            return new Actor(id, username, (MockHttpSession) r.getRequest().getSession(false));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- http helpers ----------------------------------------------------------------------------------------

    protected JsonNode get(Actor a, String url, int expected) {
        return run(MockMvcRequestBuilders.get(url), a, null, expected);
    }

    protected JsonNode post(Actor a, String url, Object body, int expected) {
        return run(MockMvcRequestBuilders.post(url), a, body, expected);
    }

    protected JsonNode put(Actor a, String url, Object body, int expected) {
        return run(MockMvcRequestBuilders.put(url), a, body, expected);
    }

    protected JsonNode patch(Actor a, String url, Object body, int expected) {
        return run(MockMvcRequestBuilders.patch(url), a, body, expected);
    }

    protected JsonNode delete(Actor a, String url, int expected) {
        return run(MockMvcRequestBuilders.delete(url), a, null, expected);
    }

    protected JsonNode run(MockHttpServletRequestBuilder b, Actor a, Object body, int expected) {
        try {
            if (a != null) {
                b.session(a.session());
            }
            b.with(csrf()).accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
            }
            MvcResult r = mvc.perform(b).andExpect(status().is(expected)).andReturn();
            String text = r.getResponse().getContentAsString();
            return text.isBlank() ? json.createObjectNode() : json.readTree(text);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected void expectError(JsonNode body, int status, String code) {
        org.junit.jupiter.api.Assertions.assertEquals(status, body.path("status").asInt(), body.toString());
        org.junit.jupiter.api.Assertions.assertEquals(code, body.path("code").asText(), body.toString());
    }

    // ---- domain helpers --------------------------------------------------------------------------------------

    protected long createCircle(Actor moderator) {
        String name = "Circle " + UUID.randomUUID().toString().substring(0, 8);
        return post(moderator, "/api/v1/circles", Map.of("name", name, "city", "Bengaluru", "area", "Koramangala",
                "description", "Test circle", "guidelines", "Be kind"), 201).path("id").asLong();
    }

    protected void join(Actor a, long circleId) {
        post(a, "/api/v1/circles/" + circleId + "/join", null, 200);
    }

    protected Map<String, Object> listingBody(long circleId, String type, String kind, String title, String category,
                                              boolean returnExpected) {
        LocalDate today = LocalDate.now();
        return Map.ofEntries(Map.entry("circleId", circleId), Map.entry("type", type), Map.entry("kind", kind),
                Map.entry("title", title), Map.entry("description", "desc"), Map.entry("category", category),
                Map.entry("tags", List.of("tag1", "tag2")), Map.entry("availableFrom", today.minusDays(1).toString()),
                Map.entry("availableTo", today.plusDays(30).toString()), Map.entry("pickupPreference", "evenings"),
                Map.entry("estimatedValueInr", 1000), Map.entry("depositInr", 100), Map.entry("hoursEstimate", 2),
                Map.entry("returnExpected", returnExpected));
    }

    protected long createListing(Actor owner, long circleId, String type, String title, boolean returnExpected) {
        return post(owner, "/api/v1/listings", listingBody(circleId, type, "ITEM", title, "TOOLS", returnExpected), 201)
                .path("id").asLong();
    }

    protected long propose(Actor proposer, long listingId) {
        return post(proposer, "/api/v1/handoffs", Map.of("listingId", listingId, "message", "hello"), 201)
                .path("id").asLong();
    }

    protected JsonNode transition(Actor a, long handoffId, String action, int expected) {
        return post(a, "/api/v1/handoffs/" + handoffId + "/transitions", Map.of("action", action), expected);
    }

    protected Map<String, Object> confirmBody() {
        return Map.of("action", "CONFIRM", "scheduledAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                "pickupNote", "Gate 2");
    }

    protected JsonNode confirm(Actor owner, long handoffId, int expected) {
        return post(owner, "/api/v1/handoffs/" + handoffId + "/transitions", confirmBody(), expected);
    }

    protected String listingStatus(Actor viewer, long listingId) {
        return get(viewer, "/api/v1/listings/" + listingId, 200).path("status").asText();
    }

    /** Circle with a moderator and two extra members; returns [mod, owner, other]. */
    protected record Setup(Actor mod, Actor owner, Actor other, long circleId) {
    }

    protected Setup setup() {
        Actor mod = newUser("Mod");
        long circle = createCircle(mod);
        Actor owner = newUser("Owner");
        Actor other = newUser("Other");
        join(owner, circle);
        join(other, circle);
        return new Setup(mod, owner, other, circle);
    }
}
