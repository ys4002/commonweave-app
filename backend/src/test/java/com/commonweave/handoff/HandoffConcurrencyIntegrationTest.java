package com.commonweave.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.commonweave.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** The race the whole design exists for: many confirms for one listing, exactly one may win. */
class HandoffConcurrencyIntegrationTest extends IntegrationTest {
    private static final int THREADS = 8;

    private record Outcome(int status, String code) {
    }

    private List<Outcome> runConcurrently(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Outcome> t : tasks) {
                futures.add(pool.submit(() -> {
                    barrier.await(30, TimeUnit.SECONDS);
                    return t.call();
                }));
            }
            List<Outcome> out = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome confirmAs(Actor owner, long handoffId) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/handoffs/" + handoffId + "/transitions").session(owner.session())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(confirmBody()))).andReturn();
        String body = r.getResponse().getContentAsString();
        JsonNode node = body.isBlank() ? json.createObjectNode() : json.readTree(body);
        return new Outcome(r.getResponse().getStatus(), node.path("code").asText());
    }

    @Test
    void eightConcurrentConfirmsOnOneListingYieldExactlyOneWinner() throws Exception {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Contested drill", true);
        List<Long> handoffIds = new ArrayList<>();
        List<Actor> ownerSessions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Actor proposer = newUser("Proposer" + i);
            join(proposer, s.circleId());
            handoffIds.add(propose(proposer, listing));
            ownerSessions.add(login(s.owner().id(), s.owner().email(), PASSWORD));
        }
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Actor owner = ownerSessions.get(i);
            long handoffId = handoffIds.get(i);
            tasks.add(() -> confirmAs(owner, handoffId));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        long wins = outcomes.stream().filter(o -> o.status() == 200).count();
        long unavailable = outcomes.stream()
                .filter(o -> o.status() == 409 && o.code().equals("LISTING_UNAVAILABLE")).count();
        assertEquals(1, wins, outcomes.toString());
        assertEquals(THREADS - 1, unavailable, outcomes.toString());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from handoffs where listing_id = ? and status = 'SCHEDULED'", Integer.class, listing));
        assertEquals(THREADS - 1, jdbc.queryForObject(
                "select count(*) from handoffs where listing_id = ? and status = 'PROPOSED'", Integer.class, listing));
        assertEquals("RESERVED", listingStatus(s.owner(), listing));
    }

    @Test
    void concurrentConfirmsOfTheSameHandoffSucceedOnlyOnce() throws Exception {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Double click item", true);
        long handoff = propose(s.other(), listing);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Actor owner = login(s.owner().id(), s.owner().email(), PASSWORD);
            tasks.add(() -> confirmAs(owner, handoff));
        }
        List<Outcome> outcomes = runConcurrently(tasks);
        assertEquals(1, outcomes.stream().filter(o -> o.status() == 200).count(), outcomes.toString());
        assertEquals(3, outcomes.stream()
                .filter(o -> o.status() == 409 && o.code().equals("INVALID_TRANSITION")).count(), outcomes.toString());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from handoff_events where handoff_id = ? and to_status = 'SCHEDULED'",
                Integer.class, handoff));
    }

    @Test
    void concurrentProposalsFromOneUserCreateOnlyOneOpenProposal() throws Exception {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Eager borrower item", true);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Actor other = login(s.other().id(), s.other().email(), PASSWORD);
            tasks.add(() -> {
                MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/v1/handoffs").session(other.session()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("listingId", listing)))).andReturn();
                return new Outcome(r.getResponse().getStatus(), "");
            });
        }
        List<Outcome> outcomes = runConcurrently(tasks);
        assertEquals(1, outcomes.stream().filter(o -> o.status() == 201).count(), outcomes.toString());
        assertEquals(3, outcomes.stream().filter(o -> o.status() == 409).count(), outcomes.toString());
    }
}
