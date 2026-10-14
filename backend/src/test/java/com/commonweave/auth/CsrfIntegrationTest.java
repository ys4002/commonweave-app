package com.commonweave.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Exercises the real cookie-based CSRF flow. The csrf() test helper replaces the token repository on the shared
 * filter, so this class deliberately never uses it and gets its own application context via the property below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "cw.test.real-csrf=true")
class CsrfIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private String email() {
        User u = new User();
        u.setEmail("csrf-" + UUID.randomUUID().toString().substring(0, 8) + "@test.example");
        u.setPasswordHash(encoder.encode("Sup3rSecret!"));
        u.setDisplayName("Csrf User");
        users.saveAndFlush(u);
        return u.getEmail();
    }

    private Cookie fetchToken() throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized()).andReturn();
        Cookie xsrf = r.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "any GET, even a 401, must set the XSRF-TOKEN cookie");
        assertTrue(!xsrf.isHttpOnly(), "the SPA must be able to read the token");
        assertEquals("/", xsrf.getPath());
        assertTrue(xsrf.getSecure(), "XSRF cookie must be Secure outside profile dev");
        return xsrf;
    }

    private String loginBody(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", "Sup3rSecret!"));
    }

    @Test
    void postWithoutTokenIsForbiddenWithJsonBody() throws Exception {
        String email = email();
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isForbidden()).andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertEquals("FORBIDDEN", body.path("code").asText());
        assertEquals(403, body.path("status").asInt());
    }

    @Test
    void cookieAndMatchingHeaderAllowLoginButForgedHeaderDoesNot() throws Exception {
        String email = email();
        Cookie xsrf = fetchToken();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").cookie(xsrf)
                .header("X-XSRF-TOKEN", "forged-token").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email))).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email))).andExpect(status().isOk());
    }

    @Test
    void stateChangingCallsInASessionNeedTheTokenAndGetsDoNot() throws Exception {
        String email = email();
        Cookie xsrf = fetchToken();
        MvcResult login = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email))).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        String patchBody = json.writeValueAsString(Map.of("bio", "hello"));

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.patch("/api/v1/users/me").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(patchBody)).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.patch("/api/v1/users/me").session(session).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(patchBody)).andExpect(status().isOk());
    }
}
