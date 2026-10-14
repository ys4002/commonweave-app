package com.commonweave.matching;

import com.commonweave.listing.Listing;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable 0-100 match score: category 35 + keywords 25 + availability 25 + pickup 15.
 * Pure functions only, so the formula can be unit-tested without a database.
 */
public final class MatchScorer {
    public static final int MIN_SCORE = 30;
    static final int CATEGORY_MAX = 35;
    static final int KEYWORD_MAX = 25;
    static final int AVAILABILITY_MAX = 25;
    static final int PICKUP_MAX = 15;
    static final int PICKUP_UNKNOWN_POINTS = 8;

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "for", "to", "in", "on", "at", "with", "my", "your", "our",
            "need", "needed", "want", "looking", "offer", "offering", "available", "lend", "borrow", "can", "will",
            "please", "some", "any", "is", "are", "be", "by", "from", "near", "this", "that", "it", "we", "i",
            "help", "have", "has", "get");

    public record Reason(String factor, int points, int max, String explanation) {
    }

    public record Result(int score, List<Reason> reasons) {
    }

    private MatchScorer() {
    }

    public static Result score(Listing a, Listing b) {
        List<Reason> reasons = new ArrayList<>(List.of(category(a, b), keywords(a, b), availability(a, b), pickup(a, b)));
        int total = reasons.stream().mapToInt(Reason::points).sum();
        return new Result(total, List.copyOf(reasons));
    }

    static Reason category(Listing a, Listing b) {
        boolean same = a.getCategory() == b.getCategory();
        return new Reason("CATEGORY", same ? CATEGORY_MAX : 0, CATEGORY_MAX,
                same ? "Same category: " + a.getCategory() : "Different categories");
    }

    static Reason keywords(Listing a, Listing b) {
        Set<String> ta = tokens(a.getTitle() + " " + String.join(" ", a.tagList()));
        Set<String> tb = tokens(b.getTitle() + " " + String.join(" ", b.tagList()));
        Set<String> shared = intersection(ta, tb);
        int points = (int) Math.round(jaccard(ta, tb) * KEYWORD_MAX);
        String text = shared.isEmpty() ? "No shared keywords"
                : "Shared keywords: " + String.join(", ", shared.stream().sorted().toList());
        return new Reason("KEYWORDS", points, KEYWORD_MAX, text);
    }

    static Reason availability(Listing a, Listing b) {
        long overlap = overlapDays(a.getAvailableFrom(), a.getAvailableTo(), b.getAvailableFrom(), b.getAvailableTo());
        long shorter = Math.min(days(a.getAvailableFrom(), a.getAvailableTo()),
                days(b.getAvailableFrom(), b.getAvailableTo()));
        int points = (int) Math.round((double) overlap / shorter * AVAILABILITY_MAX);
        String text = overlap == 0 ? "Availability windows do not overlap"
                : "Windows overlap for " + overlap + (overlap == 1 ? " day" : " days");
        return new Reason("AVAILABILITY", points, AVAILABILITY_MAX, text);
    }

    static Reason pickup(Listing a, Listing b) {
        if (isBlank(a.getPickupPreference()) || isBlank(b.getPickupPreference())) {
            return new Reason("PICKUP", PICKUP_UNKNOWN_POINTS, PICKUP_MAX, "Pickup preference not specified by one side");
        }
        Set<String> ta = tokens(a.getPickupPreference());
        Set<String> tb = tokens(b.getPickupPreference());
        int points = (int) Math.round(jaccard(ta, tb) * PICKUP_MAX);
        Set<String> shared = intersection(ta, tb);
        String text = shared.isEmpty() ? "Pickup preferences differ"
                : "Similar pickup preference: " + String.join(", ", shared.stream().sorted().toList());
        return new Reason("PICKUP", points, PICKUP_MAX, text);
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String t : SPLIT.split(text.toLowerCase())) {
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection(a, b).size() / union.size();
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static long days(LocalDate from, LocalDate to) {
        return Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
    }

    private static long overlapDays(LocalDate f1, LocalDate t1, LocalDate f2, LocalDate t2) {
        LocalDate start = f1.isAfter(f2) ? f1 : f2;
        LocalDate end = t1.isBefore(t2) ? t1 : t2;
        return end.isBefore(start) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
