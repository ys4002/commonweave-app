package com.commonweave.seed;

import com.commonweave.circle.Circle;
import com.commonweave.circle.CircleRepository;
import com.commonweave.circle.MemberRole;
import com.commonweave.circle.MemberStatus;
import com.commonweave.circle.Membership;
import com.commonweave.circle.MembershipRepository;
import com.commonweave.common.TimeConfig;
import com.commonweave.handoff.Handoff;
import com.commonweave.handoff.HandoffEvent;
import com.commonweave.handoff.HandoffEventRepository;
import com.commonweave.handoff.HandoffRepository;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.Category;
import com.commonweave.listing.Listing;
import com.commonweave.listing.ListingKind;
import com.commonweave.listing.ListingRepository;
import com.commonweave.listing.ListingStatus;
import com.commonweave.listing.ListingType;
import com.commonweave.moderation.ModerationAction;
import com.commonweave.moderation.ModerationActionRepository;
import com.commonweave.moderation.Report;
import com.commonweave.moderation.ReportRepository;
import com.commonweave.moderation.ReportStatus;
import com.commonweave.moderation.ReportTargetType;
import com.commonweave.notification.Notification;
import com.commonweave.notification.NotificationRepository;
import com.commonweave.user.User;
import com.commonweave.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Indian demo data for the dev profile, loaded only into an empty database. All dates are relative to today.
 * Demo password for every seeded member: Welcome@123 (demo only).
 */
@Component
@Profile("dev")
@Order(1)
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "Welcome@123";

    private final UserRepository users;
    private final CircleRepository circles;
    private final MembershipRepository memberships;
    private final ListingRepository listings;
    private final HandoffRepository handoffs;
    private final HandoffEventRepository events;
    private final NotificationRepository notifications;
    private final ReportRepository reports;
    private final ModerationActionRepository modActions;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    private final Map<String, User> people = new HashMap<>();
    private Instant now;
    private LocalDate today;

    public DemoDataSeeder(UserRepository users, CircleRepository circles, MembershipRepository memberships,
                          ListingRepository listings, HandoffRepository handoffs, HandoffEventRepository events,
                          NotificationRepository notifications, ReportRepository reports,
                          ModerationActionRepository modActions, PasswordEncoder encoder, TransactionTemplate tx,
                          Clock clock) {
        this.users = users;
        this.circles = circles;
        this.memberships = memberships;
        this.listings = listings;
        this.handoffs = handoffs;
        this.events = events;
        this.notifications = notifications;
        this.reports = reports;
        this.modActions = modActions;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        tx.executeWithoutResult(status -> seed());
        log.info("Loaded demo data: {} users, {} listings", users.count(), listings.count());
    }

    private void seed() {
        now = clock.instant();
        today = TimeConfig.today(clock);
        seedPeople();
        Circle kora = circle("Koramangala 5th Block Neighbours", "Bengaluru", "Koramangala 5th Block",
                "Neighbours on and around Church Road sharing tools, skills and spare things.");
        Circle bandra = circle("Bandra West Commons", "Mumbai", "Bandra West",
                "A friendly circle for Bandra West buildings and lanes to lend, borrow and learn together.");
        Circle hauz = circle("Hauz Khas Village Circle", "Delhi", "Hauz Khas Village",
                "Hauz Khas residents sharing gear, gardening help and weekend time.");
        seedMemberships(kora, bandra, hauz);
        Map<String, Listing> l = seedListings(kora, bandra, hauz);
        seedHandoffs(l);
        seedNotifications();
        seedModeration(bandra, l);
    }

    private void seedPeople() {
        String hash = encoder.encode(DEMO_PASSWORD);
        person("ananya", "Ananya Rao", "Koramangala, Bengaluru", "Likes fixing bikes and lending tools.", hash);
        person("rohan", "Rohan Mehta", "Koramangala, Bengaluru", "Moderator of our Koramangala circle.", hash);
        person("priya", "Priya Nair", "Koramangala, Bengaluru", "Teacher, gardener, occasional DIYer.", hash);
        person("karthik", "Karthik Iyer", "Koramangala, Bengaluru", "Happy to teach Kannada over chai.", hash);
        person("meera", "Meera Kulkarni", "Bandra West, Mumbai", "Runs the Bandra West circle.", hash);
        person("arjun", "Arjun Shah", "Bandra West, Mumbai", "Weekend camper and photographer.", hash);
        person("sana", "Sana Khan", "Bandra West, Mumbai", "Mum of two, loves a good swap.", hash);
        person("vikram", "Vikram Singh", "Hauz Khas, Delhi", "Moderator for Hauz Khas Village.", hash);
        person("neha", "Neha Gupta", "Hauz Khas, Delhi", "Plants, planters and a lot of yoga.", hash);
        person("imran", "Imran Qureshi", "Bandra West, Mumbai", "Cooks, bikes, and borrows ladders.", hash);
        person("divya", "Divya Menon", "Koramangala, Bengaluru", "Hindi tutor and balcony gardener.", hash);
        person("aditya", "Aditya Verma", "Hauz Khas, Delhi", "Fixes laptops for friends.", hash);
    }

    private void person(String key, String name, String locality, String bio, String hash) {
        User u = new User();
        u.setEmail(key + "@example.in");
        u.setPasswordHash(hash);
        u.setDisplayName(name);
        u.setLocality(locality);
        u.setBio(bio);
        u.setOnboarded(true);
        u.setCreatedAt(ago(60));
        people.put(key, users.save(u));
    }

    private Circle circle(String name, String city, String area, String description) {
        Circle c = new Circle();
        c.setName(name);
        c.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-$", ""));
        c.setCity(city);
        c.setArea(area);
        c.setDescription(description);
        c.setGuidelines("1. Be kind and honest.\n2. Return things in the condition you got them.\n"
                + "3. Meet in public or common areas for handoffs.\n4. No selling; this circle is for sharing.");
        c.setCreatedAt(ago(55));
        return circles.save(c);
    }

    private void join(Circle c, String key, MemberRole role, MemberStatus status) {
        Membership m = new Membership(c.getId(), people.get(key).getId(), role);
        m.setStatus(status);
        m.setJoinedAt(ago(50));
        memberships.save(m);
    }

    private void seedMemberships(Circle kora, Circle bandra, Circle hauz) {
        join(kora, "rohan", MemberRole.MODERATOR, MemberStatus.ACTIVE);
        for (String k : List.of("ananya", "priya", "karthik", "divya", "imran")) {
            join(kora, k, MemberRole.MEMBER, MemberStatus.ACTIVE);
        }
        join(bandra, "meera", MemberRole.MODERATOR, MemberStatus.ACTIVE);
        for (String k : List.of("arjun", "sana", "imran", "ananya")) {
            join(bandra, k, MemberRole.MEMBER, MemberStatus.ACTIVE);
        }
        join(hauz, "vikram", MemberRole.MODERATOR, MemberStatus.ACTIVE);
        for (String k : List.of("neha", "aditya", "arjun", "priya")) {
            join(hauz, k, MemberRole.MEMBER, MemberStatus.ACTIVE);
        }
    }

    private Map<String, Listing> seedListings(Circle k, Circle b, Circle h) {
        Map<String, Listing> m = new HashMap<>();
        // Koramangala
        m.put("drill", item(k, "ananya", ListingType.OFFER, "Bosch cordless drill", Category.TOOLS,
                "drill,tools,diy", 4500, 500, true, "Evenings near Church Road, 5th Block", 3,
                "18V cordless drill with 20 bits. Charger included."));
        m.put("drillReq", item(k, "priya", ListingType.REQUEST, "Need a power drill for the weekend",
                Category.TOOLS, "drill,wall,shelf", 0, 0, true, "Weekend evenings, 5th Block", 2,
                "Need to put up two shelves on Saturday."));
        m.put("table", item(k, "rohan", ListingType.OFFER, "Folding table (6 ft)", Category.FURNITURE,
                "table,folding,party", 3000, 300, true, "Ground floor lobby, 5th Block", 6,
                "Sturdy folding table, good for parties and stalls."));
        m.put("cooker", item(k, "karthik", ListingType.OFFER, "Prestige pressure cooker 5L", Category.KITCHEN,
                "cooker,kitchen,cooking", 1800, 0, true, "Mornings, 80 Feet Road side", 9,
                "Spare 5-litre cooker for festival cooking."));
        m.put("hindi", service(k, "divya", ListingType.OFFER, ListingKind.SKILL, "Hindi tutoring for school kids",
                Category.TUTORING, "hindi,tutoring,school", 2, "Evenings, my flat or online", 4,
                "Friendly Hindi help for Class 4 to 8."));
        m.put("kannadaReq", service(k, "imran", ListingType.REQUEST, ListingKind.SKILL,
                "Kannada conversation practice", Category.TUTORING, "kannada,speaking,beginner", 1,
                "Weekends, a cafe on 80 Feet Road", 5, "Want to speak confidently with my neighbours."));
        m.put("kannada", service(k, "karthik", ListingType.OFFER, ListingKind.SKILL, "Kannada lessons for beginners",
                Category.TUTORING, "kannada,speaking,beginner", 2, "Weekends, a cafe on 80 Feet Road", 7,
                "Relaxed conversation-first lessons."));
        m.put("cycleRepair", service(k, "ananya", ListingType.OFFER, ListingKind.SKILL, "Cycle repair and tune-up",
                Category.REPAIR_SKILLS, "cycle,repair,bike", 1, "Sunday mornings, 5th Block park", 8,
                "Puncture, brakes, gears. Bring your cycle."));
        m.put("chairs", item(k, "rohan", ListingType.REQUEST, "Chairs and a tent for society Diwali event",
                Category.EVENTS, "chairs,tent,diwali,event", 6000, 0, true, "Society gate, Church Road", 1,
                "Need 30 chairs and a small tent for 1 day."));
        m.put("decor", item(k, "priya", ListingType.OFFER, "Diwali decor swap: lanterns and torans",
                Category.EVENTS, "diwali,decor,lantern", 1500, 0, false, "Weekends, 5th Block", 5,
                "Swap or take: paper lanterns, torans, diyas."));
        m.put("balcony", service(k, "divya", ListingType.OFFER, ListingKind.SKILL, "Balcony-garden advice",
                Category.GARDEN, "garden,balcony,plants", 1, "Saturday mornings, my balcony", 6,
                "What grows well on a Bengaluru balcony."));
        // Bandra
        m.put("hammer", item(b, "meera", ListingType.OFFER, "Bosch hammer drill", Category.TOOLS, "drill,hammer,tools",
                5200, 800, true, "Evenings, Hill Road side", 4, "Heavy-duty hammer drill for concrete walls."));
        m.put("drillBandra", item(b, "arjun", ListingType.REQUEST, "Looking for a drill machine", Category.TOOLS,
                "drill,wall,tools", 0, 0, true, "Evenings, Hill Road side", 2, "Need it for one afternoon."));
        m.put("bicycle", item(b, "sana", ListingType.OFFER, "Kids bicycle (age 6-9)", Category.SPORTS_CYCLES,
                "cycle,kids,bicycle", 2500, 0, true, "Mornings, Carter Road promenade", 10,
                "Outgrown, still in good shape."));
        m.put("pickle", service(b, "imran", ListingType.OFFER, ListingKind.SKILL, "Home-made pickle workshop",
                Category.KITCHEN, "pickle,cooking,workshop", 2, "Sunday afternoons, community hall", 6,
                "Learn mango and lime pickles from scratch."));
        m.put("bandraChairs", item(b, "arjun", ListingType.OFFER, "Society event chairs (20)", Category.EVENTS,
                "chairs,event,society", 4000, 1000, true, "Society gate, Pali Hill", 3,
                "Plastic chairs, ideal for building events."));
        m.put("wifi", service(b, "meera", ListingType.REQUEST, ListingKind.SKILL, "Help setting up a Wi-Fi router",
                Category.ELECTRONICS, "wifi,router,internet", 1, "Weekday evenings, Linking Road", 2,
                "New fibre router needs configuring."));
        m.put("english", service(b, "ananya", ListingType.OFFER, ListingKind.SKILL, "Spoken English tutoring",
                Category.TUTORING, "english,speaking,tutoring", 2, "Online, weekend mornings", 11,
                "Confidence-building practice sessions."));
        m.put("supplements", item(b, "imran", ListingType.OFFER, "Imported protein supplements (sealed)",
                Category.OTHER, "protein,fitness", 2000, 0, false, "Message me", 1, "Brand new tubs, ask for details."));
        m.put("tent", item(b, "arjun", ListingType.OFFER, "Camping tent (4-person)", Category.SPORTS_CYCLES,
                "tent,camping,trek", 7000, 1000, true, "Weekends, Pali Hill", 8, "Waterproof, used twice."));
        // Hauz Khas
        m.put("washer", item(h, "vikram", ListingType.OFFER, "Electric pressure washer", Category.TOOLS,
                "washer,cleaning,car", 8000, 1000, true, "Mornings, Hauz Khas main road", 12,
                "Great for cars, balconies and terraces."));
        m.put("ladder", item(h, "neha", ListingType.REQUEST, "Need a ladder for a day", Category.TOOLS,
                "ladder,tools", 0, 0, true, "Afternoons, Hauz Khas Village", 2, "Changing a light fixture."));
        m.put("laptop", service(h, "aditya", ListingType.OFFER, ListingKind.SKILL, "Laptop repair and OS reinstall",
                Category.ELECTRONICS, "laptop,repair,windows", 2, "Weekends, Deer Park gate", 6,
                "Slow laptop? Let us have a look."));
        m.put("python", service(h, "priya", ListingType.REQUEST, ListingKind.SKILL,
                "Help with Python homework for Class 11", Category.TUTORING, "python,homework,coding", 1,
                "Online, weekday evenings", 3, "My niece is stuck on loops."));
        m.put("planters", item(h, "neha", ListingType.OFFER, "Terracotta planters (set of 6)", Category.GARDEN,
                "planters,garden,terracotta", 1200, 0, false, "Weekends, Hauz Khas Village", 5,
                "Moving flats, happy to gift."));
        m.put("plantwater", service(h, "vikram", ListingType.OFFER, ListingKind.TIME,
                "Weekend plant-watering while you travel", Category.GARDEN, "plants,watering,travel", 2,
                "Hauz Khas Village lanes", 4, "Two hours each weekend, within the circle."));
        m.put("lights", item(h, "arjun", ListingType.OFFER, "Photography lighting kit", Category.ELECTRONICS,
                "photography,lighting,studio", 9000, 1500, true, "Weekdays, Hauz Khas Village", 9,
                "Two softboxes and stands."));
        m.put("mixer", item(h, "aditya", ListingType.REQUEST, "Mixer grinder for a month", Category.KITCHEN,
                "mixer,grinder,kitchen", 3500, 0, true, "Evenings, Deer Park gate", 3,
                "Mine broke before the festive season."));
        m.put("yoga", item(h, "neha", ListingType.OFFER, "Yoga mats (set of 4)", Category.SPORTS_CYCLES,
                "yoga,mats,fitness", 2000, 0, true, "Mornings, Hauz Khas Village", 7, "Clean, lightly used."));
        m.put("cleanup", service(h, "vikram", ListingType.REQUEST, ListingKind.TIME,
                "Volunteers for Diwali lane cleanup", Category.EVENTS, "diwali,cleanup,volunteer", 3,
                "Village lane, Saturday morning", 1, "Gloves and bags provided."));
        // Completed and closed exchanges
        m.put("books", closed(item(k, "ananya", ListingType.OFFER, "Old school books (Class 8-10)", Category.BOOKS,
                "books,school,textbooks", 800, 0, false, "Weekends, 5th Block", 30, "NCERT and guides.")));
        m.put("rangoli", closed(service(k, "divya", ListingType.OFFER, ListingKind.SKILL, "Rangoli design session",
                Category.EVENTS, "rangoli,diwali,art", 2, "Society courtyard", 20, "Learn simple dot-rangoli designs.")));
        m.put("resume", closed(service(h, "aditya", ListingType.OFFER, ListingKind.SKILL, "Resume review",
                Category.OTHER, "resume,career", 1, "Online", 40, "One-hour review with feedback.")));
        m.put("stroller", closed(item(b, "meera", ListingType.OFFER, "Baby stroller (gently used)", Category.KIDS,
                "stroller,baby,kids", 5000, 0, false, "Mornings, Bandra Bandstand", 50, "Passed on with love.")));
        return m;
    }

    private Listing item(Circle c, String owner, ListingType type, String title, Category cat, String tags, int value,
                         int deposit, boolean returnExpected, String pickup, int ageDays, String description) {
        return saveListing(c, owner, type, ListingKind.ITEM, title, cat, tags, value, deposit, 0, returnExpected,
                pickup, ageDays, description);
    }

    private Listing service(Circle c, String owner, ListingType type, ListingKind kind, String title, Category cat,
                            String tags, int hours, String pickup, int ageDays, String description) {
        return saveListing(c, owner, type, kind, title, cat, tags, 0, 0, hours, false, pickup, ageDays, description);
    }

    private Listing closed(Listing l) {
        l.setStatus(ListingStatus.CLOSED);
        return listings.save(l);
    }

    private Listing saveListing(Circle c, String owner, ListingType type, ListingKind kind, String title,
                                Category cat, String tags, int value, int deposit, int hours, boolean returnExpected,
                                String pickup, int ageDays, String description) {
        Listing l = new Listing();
        l.setCircleId(c.getId());
        l.setOwnerId(people.get(owner).getId());
        l.setType(type);
        l.setKind(kind);
        l.setTitle(title);
        l.setDescription(description);
        l.setCategory(cat);
        l.setTags(tags);
        l.setAvailableFrom(today.minusDays(2));
        l.setAvailableTo(today.plusDays(45));
        l.setPickupPreference(pickup);
        l.setEstimatedValueInr(value);
        l.setDepositInr(deposit);
        l.setHoursEstimate(hours);
        l.setReturnExpected(returnExpected);
        l.setStatus(ListingStatus.ACTIVE);
        l.setCreatedAt(ago(ageDays));
        l.setUpdatedAt(ago(ageDays));
        return listings.save(l);
    }

    private void seedHandoffs(Map<String, Listing> l) {
        handoff(l.get("drill"), "priya", HandoffStatus.PROPOSED, "Can I borrow it this Saturday?", null, null, 1, false);
        handoff(l.get("drill"), "divya", HandoffStatus.PROPOSED, "Need it for a curtain rod, one evening.", null, null, 0, false);
        handoff(l.get("table"), "divya", HandoffStatus.SCHEDULED, "For our building's Diwali stall.",
                now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(5)), 1, false);
        handoff(l.get("cooker"), "ananya", HandoffStatus.HANDED_OFF, "Cooking for 30 guests.",
                now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(3)), 2, false);
        handoff(l.get("bicycle"), "imran", HandoffStatus.HANDED_OFF, "For my nephew's visit.",
                now.minus(Duration.ofDays(9)), now.minus(Duration.ofDays(2)), 9, false);
        handoff(l.get("hammer"), "arjun", HandoffStatus.RETURNED, "Mounting a TV bracket.",
                now.minus(Duration.ofDays(14)), now.minus(Duration.ofDays(11)), 10, false);
        handoff(l.get("washer"), "neha", HandoffStatus.RETURNED, "Cleaning the terrace.",
                now.minus(Duration.ofDays(25)), now.minus(Duration.ofDays(22)), 20, false);
        handoff(l.get("books"), "priya", HandoffStatus.COMPLETED, "For my cousin in Class 9.",
                now.minus(Duration.ofDays(17)), null, 15, false);
        handoff(l.get("rangoli"), "priya", HandoffStatus.COMPLETED, "Would love to learn!",
                now.minus(Duration.ofDays(10)), null, 8, false);
        handoff(l.get("resume"), "neha", HandoffStatus.COMPLETED, "Applying for a new role.",
                now.minus(Duration.ofDays(27)), null, 25, false);
        handoff(l.get("stroller"), "sana", HandoffStatus.COMPLETED, "Thank you Meera.",
                now.minus(Duration.ofDays(42)), null, 40, false);
        handoff(l.get("decor"), "karthik", HandoffStatus.CANCELLED, "Can I pick up a few lanterns?", null, null, 4, false);
        handoff(l.get("cycleRepair"), "imran", HandoffStatus.CANCELLED, "Brakes are squeaking.",
                now.plus(Duration.ofDays(1)), null, 6, true);
        reserve(l, "table", "cooker", "bicycle");
        l.get("books").setUpdatedAt(ago(15));
        listings.save(l.get("books"));
    }

    private void reserve(Map<String, Listing> l, String... keys) {
        for (String k : keys) {
            Listing x = l.get(k);
            x.setStatus(ListingStatus.RESERVED);
            listings.save(x);
        }
    }

    private void handoff(Listing l, String proposerKey, HandoffStatus status, String message, Instant scheduledAt,
                         Instant returnBy, int updatedDaysAgo, boolean cancelledFromScheduled) {
        Long proposer = people.get(proposerKey).getId();
        boolean offer = l.getType() == ListingType.OFFER;
        Handoff h = new Handoff();
        h.setListingId(l.getId());
        h.setCircleId(l.getCircleId());
        h.setProviderId(offer ? l.getOwnerId() : proposer);
        h.setRecipientId(offer ? proposer : l.getOwnerId());
        h.setProposerId(proposer);
        h.setStatus(status);
        h.setMessage(message);
        h.setScheduledAt(scheduledAt);
        h.setPickupNote(scheduledAt == null ? null : "Meet at the society gate.");
        h.setReturnExpected(l.isReturnExpected());
        h.setReturnBy(l.isReturnExpected() ? returnBy : null);
        Instant updated = updatedDaysAgo == 0 ? now.minus(Duration.ofHours(3)) : ago(updatedDaysAgo);
        h.setCreatedAt(updated.minus(Duration.ofDays(3)));
        h.setUpdatedAt(updated);
        handoffs.save(h);
        saveEvents(h, l, cancelledFromScheduled, updated);
    }

    private void saveEvents(Handoff h, Listing l, boolean cancelledFromScheduled, Instant updated) {
        List<HandoffStatus> path = new ArrayList<>(List.of(HandoffStatus.PROPOSED));
        switch (h.getStatus()) {
            case SCHEDULED -> path.add(HandoffStatus.SCHEDULED);
            case HANDED_OFF -> path.addAll(List.of(HandoffStatus.SCHEDULED, HandoffStatus.HANDED_OFF));
            case RETURNED, COMPLETED -> path.addAll(List.of(HandoffStatus.SCHEDULED, HandoffStatus.HANDED_OFF,
                    h.getStatus()));
            case CANCELLED -> {
                if (cancelledFromScheduled) {
                    path.add(HandoffStatus.SCHEDULED);
                }
                path.add(HandoffStatus.CANCELLED);
            }
            default -> { }
        }
        HandoffStatus from = null;
        for (int i = 0; i < path.size(); i++) {
            HandoffStatus to = path.get(i);
            Instant at = updated.minus(Duration.ofHours(6L * (path.size() - 1 - i)));
            events.save(new HandoffEvent(h.getId(), actorFor(h, to), from, to, null, at));
            from = to;
        }
    }

    private Long actorFor(Handoff h, HandoffStatus to) {
        return switch (to) {
            case PROPOSED, CANCELLED -> h.getProposerId();
            case SCHEDULED -> h.ownerId();
            case HANDED_OFF -> h.getProviderId();
            default -> h.getRecipientId();
        };
    }

    private void seedNotifications() {
        Long ananya = people.get("ananya").getId();
        Long rohan = people.get("rohan").getId();
        Long karthik = people.get("karthik").getId();
        notify(ananya, "HANDOFF_PROPOSED", "New proposal: Bosch cordless drill",
                "Priya Nair would like to arrange a handoff.", "/listings", false, 1);
        notify(ananya, "HANDOFF_PROPOSED", "New proposal: Bosch cordless drill",
                "Divya Menon would like to arrange a handoff.", "/listings", false, 0);
        notify(ananya, "REMINDER_RETURN_DUE", "Return due soon: Prestige pressure cooker 5L",
                "Prestige pressure cooker 5L is due back within 24 hours.", "/handoffs", false, 0);
        notify(ananya, "HANDOFF_COMPLETED", "Exchange completed: Old school books (Class 8-10)",
                "Priya Nair marked the exchange as complete.", "/handoffs", true, 15);
        notify(rohan, "HANDOFF_SCHEDULED", "Handoff scheduled: Folding table (6 ft)",
                "You confirmed the handoff.", "/handoffs", true, 1);
        notify(karthik, "HANDOFF_HANDED_OFF", "Handed over: Prestige pressure cooker 5L",
                "Ananya Rao marked the item as handed over.", "/handoffs", false, 2);
    }

    private void notify(Long userId, String type, String title, String body, String link, boolean read, int daysAgo) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setCreatedAt(daysAgo == 0 ? now.minus(Duration.ofHours(2)) : ago(daysAgo));
        n.setReadAt(read ? n.getCreatedAt().plus(Duration.ofHours(1)) : null);
        notifications.save(n);
    }

    private void seedModeration(Circle bandra, Map<String, Listing> l) {
        Report open = new Report();
        open.setCircleId(bandra.getId());
        open.setReporterId(people.get("sana").getId());
        open.setTargetType(ReportTargetType.LISTING);
        open.setTargetId(l.get("supplements").getId());
        open.setReason("This looks like a product being sold, not something shared with neighbours.");
        open.setStatus(ReportStatus.OPEN);
        open.setCreatedAt(ago(1));
        reports.save(open);

        Report closed = new Report();
        closed.setCircleId(bandra.getId());
        closed.setReporterId(people.get("arjun").getId());
        closed.setTargetType(ReportTargetType.LISTING);
        closed.setTargetId(l.get("english").getId());
        closed.setReason("Is this paid tutoring? Please check the listing.");
        closed.setStatus(ReportStatus.DISMISSED);
        closed.setResolutionNote("Checked with the owner: free sessions, no fees.");
        closed.setResolvedBy(people.get("meera").getId());
        closed.setCreatedAt(ago(12));
        closed.setResolvedAt(ago(11));
        reports.save(closed);
        modActions.save(new ModerationAction(bandra.getId(), people.get("meera").getId(), "REPORT_DISMISSED",
                "LISTING", l.get("english").getId(), "Checked with the owner: free sessions, no fees.", ago(11)));
    }

    private Instant ago(int days) {
        return now.minus(Duration.ofDays(days));
    }
}
