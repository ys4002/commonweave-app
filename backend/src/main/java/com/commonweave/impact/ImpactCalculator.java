package com.commonweave.impact;

import com.commonweave.impact.ImpactDtos.CategoryImpact;
import com.commonweave.impact.ImpactDtos.ImpactResponse;
import com.commonweave.impact.ImpactDtos.Methodology;
import com.commonweave.listing.Category;
import com.commonweave.listing.ListingKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Deterministic impact formulas. These are labelled estimates, never measurements. */
public final class ImpactCalculator {
    public static final String DISCLAIMER = "Estimates only. These are rough planning figures based on the "
            + "assumptions listed in the methodology, not measurements of actual savings or emissions.";

    /** One finished exchange (COMPLETED or RETURNED handoff) and the listing facts it needs. */
    public record Exchange(ListingKind kind, Category category, boolean returnExpected, int estimatedValueInr,
                           int hoursEstimate) {
    }

    private final ImpactProperties props;

    public ImpactCalculator(ImpactProperties props) {
        this.props = props;
    }

    public long rupeesSaved(Exchange e) {
        if (isService(e)) {
            return (long) e.hoursEstimate() * props.hourlyRateInr();
        }
        return Math.round(e.estimatedValueInr() * itemShare(e));
    }

    public double co2Kg(Exchange e) {
        double share = isService(e) ? props.serviceCo2Share() : itemShare(e);
        return props.co2Factor(e.category()) * share;
    }

    public int hoursShared(Exchange e) {
        return isService(e) ? e.hoursEstimate() : 0;
    }

    public ImpactResponse summarise(List<Exchange> exchanges, int periodDays, int activeMembers) {
        long rupees = 0;
        double co2 = 0;
        int hours = 0;
        Map<Category, long[]> byCategory = new EnumMap<>(Category.class);
        for (Exchange e : exchanges) {
            long saved = rupeesSaved(e);
            rupees += saved;
            co2 += co2Kg(e);
            hours += hoursShared(e);
            long[] agg = byCategory.computeIfAbsent(e.category(), k -> new long[2]);
            agg[0]++;
            agg[1] += saved;
        }
        List<CategoryImpact> cats = byCategory.entrySet().stream()
                .map(en -> new CategoryImpact(en.getKey(), (int) en.getValue()[0], en.getValue()[1]))
                .sorted((a, b) -> a.count() != b.count() ? Integer.compare(b.count(), a.count())
                        : a.category().compareTo(b.category()))
                .toList();
        return new ImpactResponse(periodDays, exchanges.size(), activeMembers, rupees,
                Math.round(co2 * 10.0) / 10.0, hours, cats, methodology(), DISCLAIMER);
    }

    public List<Methodology> methodology() {
        return List.of(
                new Methodology("Rupees saved - item loan",
                        pct(props.loanShare()) + " x estimated value (INR)",
                        "Assumes a borrowed item is shared about 5 times instead of each borrower buying one."),
                new Methodology("Rupees saved - item gift",
                        pct(props.giftShare()) + " x estimated value (INR)",
                        "Assumes receiving a second-hand item saves this share of the new price."),
                new Methodology("Rupees saved - skill or time",
                        "hours x INR " + props.hourlyRateInr(),
                        "Assumes a local service rate of INR " + props.hourlyRateInr() + " per hour."),
                new Methodology("CO2e avoided (kg)",
                        "category factor x share (loan " + props.loanShare() + ", gift " + props.giftShare()
                                + ", skill/time " + props.serviceCo2Share() + ")",
                        "Category factors in kg CO2e per new item avoided: " + factors()),
                new Methodology("Hours shared", "sum of estimated hours for skill and time exchanges",
                        "Uses the hours estimated by the listing owner."));
    }

    private double itemShare(Exchange e) {
        return e.returnExpected() ? props.loanShare() : props.giftShare();
    }

    private static boolean isService(Exchange e) {
        return e.kind() == ListingKind.SKILL || e.kind() == ListingKind.TIME;
    }

    private static String pct(double share) {
        return Math.round(share * 100) + "%";
    }

    private String factors() {
        StringBuilder sb = new StringBuilder();
        props.co2KgByCategory().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(en -> sb.append(en.getKey()).append(' ').append(en.getValue()).append(", "));
        return sb + "others " + props.defaultCo2Kg();
    }
}
