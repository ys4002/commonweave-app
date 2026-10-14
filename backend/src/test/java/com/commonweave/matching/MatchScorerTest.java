package com.commonweave.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.listing.Category;
import com.commonweave.listing.Listing;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchScorerTest {
    private static final LocalDate D = LocalDate.of(2026, 10, 1);

    private Listing listing(long id, Category cat, String title, String tags, int fromOffset, int toOffset,
                            String pickup) {
        Listing l = new Listing();
        l.setId(id);
        l.setCategory(cat);
        l.setTitle(title);
        l.setTags(tags);
        l.setAvailableFrom(D.plusDays(fromOffset));
        l.setAvailableTo(D.plusDays(toOffset));
        l.setPickupPreference(pickup);
        return l;
    }

    @Test
    void identicalListingsScoreFull100() {
        Listing a = listing(1, Category.TOOLS, "Cordless drill", "drill,diy", 0, 9, "evening gate");
        Listing b = listing(2, Category.TOOLS, "Cordless drill", "drill,diy", 0, 9, "evening gate");
        MatchScorer.Result r = MatchScorer.score(a, b);
        assertEquals(100, r.score());
        assertEquals(List.of("CATEGORY", "KEYWORDS", "AVAILABILITY", "PICKUP"),
                r.reasons().stream().map(MatchScorer.Reason::factor).toList());
    }

    @Test
    void reasonsAlwaysSumToScoreAndRespectMax() {
        Listing a = listing(1, Category.TOOLS, "Power drill for shelves", "drill,wall", 0, 9, "5th block evenings");
        Listing b = listing(2, Category.KITCHEN, "Pressure cooker", "cooker", 5, 30, "mornings");
        MatchScorer.Result r = MatchScorer.score(a, b);
        assertEquals(r.score(), r.reasons().stream().mapToInt(MatchScorer.Reason::points).sum());
        r.reasons().forEach(x -> assertTrue(x.points() <= x.max() && x.points() >= 0));
        assertEquals(0, r.reasons().get(0).points());
    }

    @Test
    void categoryGives35OnlyWhenSame() {
        Listing a = listing(1, Category.BOOKS, "x1", null, 0, 1, null);
        assertEquals(35, MatchScorer.category(a, listing(2, Category.BOOKS, "y1", null, 0, 1, null)).points());
        assertEquals(0, MatchScorer.category(a, listing(2, Category.KIDS, "y1", null, 0, 1, null)).points());
    }

    @Test
    void keywordsUseJaccardOverTitleAndTagsWithoutStopWords() {
        Listing a = listing(1, Category.TOOLS, "Need a power drill", "wall", 0, 1, null);
        Listing b = listing(2, Category.TOOLS, "Offering the power drill", "wall,shelf", 0, 1, null);
        // tokens a: power, drill, wall; b: power, drill, wall, shelf -> 3/4 * 25 = 18.75 -> 19
        MatchScorer.Reason r = MatchScorer.keywords(a, b);
        assertEquals(19, r.points());
        assertTrue(r.explanation().contains("drill"));
    }

    @Test
    void noKeywordsMeansZero() {
        Listing a = listing(1, Category.TOOLS, "the and of", null, 0, 1, null);
        Listing b = listing(2, Category.TOOLS, "for with", null, 0, 1, null);
        assertEquals(0, MatchScorer.keywords(a, b).points());
    }

    @Test
    void availabilityIsOverlapOverShorterWindow() {
        // a: 10 days (0..9), b: 20 days (5..24) -> overlap 5 days / 10 = 0.5 * 25 = 12.5 -> 13
        Listing a = listing(1, Category.TOOLS, "t1", null, 0, 9, null);
        Listing b = listing(2, Category.TOOLS, "t2", null, 5, 24, null);
        assertEquals(13, MatchScorer.availability(a, b).points());
        Listing far = listing(3, Category.TOOLS, "t3", null, 40, 50, null);
        assertEquals(0, MatchScorer.availability(a, far).points());
        Listing inside = listing(4, Category.TOOLS, "t4", null, 2, 3, null);
        assertEquals(25, MatchScorer.availability(a, inside).points());
    }

    @Test
    void pickupUsesJaccardOr8WhenEitherBlank() {
        Listing a = listing(1, Category.TOOLS, "t1", null, 0, 1, "evenings near church road");
        Listing blank = listing(2, Category.TOOLS, "t2", null, 0, 1, "  ");
        Listing nul = listing(3, Category.TOOLS, "t3", null, 0, 1, null);
        assertEquals(8, MatchScorer.pickup(a, blank).points());
        assertEquals(8, MatchScorer.pickup(nul, a).points());
        Listing half = listing(4, Category.TOOLS, "t4", null, 0, 1, "evenings church road gate");
        // a: evenings, church, road ("near" is a stop word); half: evenings, church, road, gate -> 3/4*15 = 11.25 -> 11
        assertEquals(11, MatchScorer.pickup(a, half).points());
    }

    @Test
    void rankDropsBelowThresholdSortsDescAndBreaksTiesByNewerId() {
        Listing source = listing(100, Category.TOOLS, "Power drill", "drill", 0, 9, "gate");
        Listing strong = listing(1, Category.TOOLS, "Power drill", "drill", 0, 9, "gate");
        Listing tieOld = listing(5, Category.TOOLS, "Drill machine", "drill", 0, 9, "gate");
        Listing tieNew = listing(9, Category.TOOLS, "Drill machine", "drill", 0, 9, "gate");
        Listing weak = listing(7, Category.KIDS, "Toy", "toy", 40, 50, "far away");
        var ranked = MatchingService.rank(source, List.of(weak, tieOld, strong, tieNew), 5);
        assertEquals(List.of(1L, 9L, 5L), ranked.stream().map(s -> s.candidate().getId()).toList());
        assertTrue(ranked.stream().allMatch(s -> s.result().score() >= MatchScorer.MIN_SCORE));
    }

    @Test
    void scoringIsDeterministic() {
        Listing a = listing(1, Category.TOOLS, "Power drill", "drill,wall", 0, 9, "gate evenings");
        Listing b = listing(2, Category.TOOLS, "Hammer drill", "drill", 3, 12, "evenings");
        assertEquals(MatchScorer.score(a, b), MatchScorer.score(a, b));
    }
}
