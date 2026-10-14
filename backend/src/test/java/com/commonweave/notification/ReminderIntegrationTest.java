package com.commonweave.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.commonweave.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReminderIntegrationTest extends IntegrationTest {
    @Autowired ReminderService reminders;

    private int count(String key) {
        return jdbc.queryForObject("select count(*) from notifications where dedupe_key = ?", Integer.class, key);
    }

    @Test
    void pickupReminderIsCreatedOncePerParticipant() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Soon item", false);
        long id = propose(s.other(), listing);
        post(s.owner(), "/api/v1/handoffs/" + id + "/transitions", Map.of("action", "CONFIRM",
                "scheduledAt", Instant.now().plus(3, ChronoUnit.HOURS).toString()), 200);
        reminders.createReminders();
        assertEquals(2, count("pickup-24h:" + id));
        reminders.createReminders();
        assertEquals(2, count("pickup-24h:" + id));
        assertEquals(1, get(s.owner(), "/api/v1/notifications", 200).path("content").findValuesAsText("type").stream()
                .filter("REMINDER_PICKUP"::equals).count());
    }

    @Test
    void farAwayHandoffsGetNoReminder() {
        Setup s = setup();
        long listing = createListing(s.owner(), s.circleId(), "OFFER", "Far item", false);
        long id = propose(s.other(), listing);
        confirm(s.owner(), id, 200);
        reminders.createReminders();
        assertEquals(0, count("pickup-24h:" + id));
    }

    @Test
    void returnDueAndOverdueRemindersAreDeduped() {
        Setup s = setup();
        long dueListing = createListing(s.owner(), s.circleId(), "OFFER", "Due item", true);
        long due = propose(s.other(), dueListing);
        confirm(s.owner(), due, 200);
        transition(s.owner(), due, "MARK_HANDED_OFF", 200);
        jdbc.update("update handoffs set return_by = UTC_TIMESTAMP(6) + interval 5 hour where id = ?", due);

        long lateListing = createListing(s.owner(), s.circleId(), "OFFER", "Late item", true);
        long late = propose(s.other(), lateListing);
        confirm(s.owner(), late, 200);
        transition(s.owner(), late, "MARK_HANDED_OFF", 200);
        jdbc.update("update handoffs set return_by = UTC_TIMESTAMP(6) - interval 2 day where id = ?", late);

        reminders.createReminders();
        reminders.createReminders();
        assertEquals(2, count("return-due:" + due));
        assertEquals(0, count("return-overdue:" + due));
        assertEquals(2, count("return-overdue:" + late));
        assertEquals(0, count("return-due:" + late));
        assertEquals(1, get(s.owner(), "/api/v1/dashboard", 200).path("needsAction").size());
    }
}
