package com.commonweave.notification;

import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hourly reminders. Each (user, dedupe key) pair is created at most once, so reruns are harmless. */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final Duration WINDOW = Duration.ofHours(24);

    private final HandoffRepository handoffs;
    private final ListingRepository listings;
    private final NotificationService notifications;
    private final Clock clock;

    public ReminderService(HandoffRepository handoffs, ListingRepository listings,
                           NotificationService notifications, Clock clock) {
        this.handoffs = handoffs;
        this.listings = listings;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void hourly() {
        log.info("Reminder job created {} notifications", createReminders());
    }

    @Transactional
    public int createReminders() {
        Instant now = clock.instant();
        Instant horizon = now.plus(WINDOW);
        int created = 0;
        for (Handoff h : handoffs.findByStatusAndScheduledAtBetween(HandoffStatus.SCHEDULED, now, horizon)) {
            created += remind(h, "REMINDER_PICKUP", "pickup-24h", "Handoff coming up", "is scheduled within 24 hours");
        }
        for (Handoff h : handoffs.findByStatusAndReturnExpectedTrueAndReturnByBetween(
                HandoffStatus.HANDED_OFF, now, horizon)) {
            created += remind(h, "REMINDER_RETURN_DUE", "return-due", "Return due soon", "is due back within 24 hours");
        }
        for (Handoff h : handoffs.findByStatusAndReturnExpectedTrueAndReturnByBefore(HandoffStatus.HANDED_OFF, now)) {
            created += remind(h, "REMINDER_RETURN_OVERDUE", "return-overdue", "Return overdue", "is overdue for return");
        }
        return created;
    }

    private int remind(Handoff h, String type, String keyPrefix, String title, String what) {
        String itemTitle = listings.findById(h.getListingId()).map(Listing::getTitle).orElse("An item");
        String key = keyPrefix + ":" + h.getId();
        int created = 0;
        for (Long userId : List.of(h.getProviderId(), h.getRecipientId())) {
            if (notifications.notifyOnce(userId, type, title + ": " + itemTitle, itemTitle + " " + what + ".",
                    "/handoffs/" + h.getId(), key)) {
                created++;
            }
        }
        return created;
    }
}
