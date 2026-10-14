package com.commonweave.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ImpactIntegrationTest extends IntegrationTest {

    @Test
    void impactUsesDocumentedFormulasAndAlwaysIncludesMethodologyAndDisclaimer() {
        Setup s = setup();
        // loan: Rs 1000 TOOLS -> 20% = 200 saved, 6 x 0.2 = 1.2 kg
        long loan = createListing(s.owner(), s.circleId(), "OFFER", "Loaned drill", true);
        long loanHandoff = propose(s.other(), loan);
        confirm(s.owner(), loanHandoff, 200);
        transition(s.owner(), loanHandoff, "MARK_HANDED_OFF", 200);
        transition(s.other(), loanHandoff, "MARK_RETURNED", 200);
        // gift: Rs 1000 TOOLS -> 60% = 600 saved, 6 x 0.6 = 3.6 kg
        long gift = createListing(s.owner(), s.circleId(), "OFFER", "Gifted tools", false);
        long giftHandoff = propose(s.other(), gift);
        confirm(s.owner(), giftHandoff, 200);
        transition(s.owner(), giftHandoff, "MARK_HANDED_OFF", 200);
        transition(s.other(), giftHandoff, "COMPLETE", 200);
        // skill: 2 hours -> Rs 500 saved, 1 x 0.1 = 0.1 kg (TUTORING has no factor, default 1)
        var skillBody = listingBody(s.circleId(), "OFFER", "SKILL", "Maths tutoring", "TUTORING", false);
        long skill = post(s.owner(), "/api/v1/listings", skillBody, 201).path("id").asLong();
        long skillHandoff = propose(s.other(), skill);
        confirm(s.owner(), skillHandoff, 200);
        transition(s.owner(), skillHandoff, "MARK_HANDED_OFF", 200);
        transition(s.other(), skillHandoff, "COMPLETE", 200);
        // an unfinished handoff must not count
        long open = createListing(s.owner(), s.circleId(), "OFFER", "Not yet", true);
        propose(s.other(), open);

        JsonNode impact = get(s.other(), "/api/v1/circles/" + s.circleId() + "/impact?days=30", 200);
        assertEquals(30, impact.path("periodDays").asInt());
        assertEquals(3, impact.path("completedExchanges").asInt());
        assertEquals(2, impact.path("activeMembers").asInt());
        assertEquals(200 + 600 + 500, impact.path("rupeesSavedEstimate").asLong());
        assertEquals(4.9, impact.path("co2KgAvoidedEstimate").asDouble(), 1e-9);
        assertEquals(2, impact.path("hoursShared").asInt());
        assertEquals(2, impact.path("byCategory").size());
        assertEquals("TOOLS", impact.path("byCategory").get(0).path("category").asText());
        assertEquals(2, impact.path("byCategory").get(0).path("count").asInt());
        assertEquals(800, impact.path("byCategory").get(0).path("rupeesSaved").asLong());
        assertTrue(impact.path("methodology").size() >= 4);
        assertTrue(impact.path("disclaimer").asText().startsWith("Estimates only"));

        JsonNode mine = get(s.owner(), "/api/v1/impact/me", 200);
        assertEquals(90, mine.path("periodDays").asInt());
        assertEquals(3, mine.path("completedExchanges").asInt());
        assertEquals(1300, mine.path("rupeesSavedEstimate").asLong());

        JsonNode mod = get(s.mod(), "/api/v1/impact/me", 200);
        assertEquals(0, mod.path("completedExchanges").asInt());
        assertTrue(mod.path("disclaimer").asText().length() > 10);
        assertFalse(mod.path("methodology").isEmpty());
    }

    @Test
    void periodFilterExcludesOlderExchanges() {
        Setup s = setup();
        long gift = createListing(s.owner(), s.circleId(), "OFFER", "Old gift", false);
        long h = propose(s.other(), gift);
        confirm(s.owner(), h, 200);
        transition(s.owner(), h, "MARK_HANDED_OFF", 200);
        transition(s.other(), h, "COMPLETE", 200);
        jdbc.update("update handoffs set updated_at = UTC_TIMESTAMP(6) - interval 100 day where id = ?", h);
        assertEquals(0, get(s.other(), "/api/v1/impact/me?days=90", 200).path("completedExchanges").asInt());
        assertEquals(1, get(s.other(), "/api/v1/impact/me?days=365", 200).path("completedExchanges").asInt());
    }
}
