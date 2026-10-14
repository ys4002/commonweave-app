package com.commonweave.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonweave.impact.ImpactCalculator.Exchange;
import com.commonweave.impact.ImpactDtos.ImpactResponse;
import com.commonweave.listing.Category;
import com.commonweave.listing.ListingKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImpactCalculatorTest {
    private final ImpactCalculator calc = new ImpactCalculator(new ImpactProperties(0.20, 0.60, 0.10, 250, 1.0,
            Map.of(Category.TOOLS, 6.0, Category.ELECTRONICS, 15.0, Category.FURNITURE, 8.0, Category.KITCHEN, 3.0,
                    Category.GARDEN, 2.0, Category.BOOKS, 1.0, Category.KIDS, 4.0, Category.SPORTS_CYCLES, 7.0)));

    private final Exchange loan = new Exchange(ListingKind.ITEM, Category.TOOLS, true, 4500, 0);
    private final Exchange gift = new Exchange(ListingKind.ITEM, Category.BOOKS, false, 800, 0);
    private final Exchange skill = new Exchange(ListingKind.SKILL, Category.TUTORING, false, 0, 2);
    private final Exchange time = new Exchange(ListingKind.TIME, Category.GARDEN, false, 0, 3);

    @Test
    void itemLoanSaves20PercentOfValue() {
        assertEquals(900, calc.rupeesSaved(loan));
        assertEquals(1.2, calc.co2Kg(loan), 1e-9);
        assertEquals(0, calc.hoursShared(loan));
    }

    @Test
    void itemGiftSaves60PercentOfValue() {
        assertEquals(480, calc.rupeesSaved(gift));
        assertEquals(0.6, calc.co2Kg(gift), 1e-9);
    }

    @Test
    void skillAndTimeUseHoursTimes250AndTenPercentCo2() {
        assertEquals(500, calc.rupeesSaved(skill));
        assertEquals(750, calc.rupeesSaved(time));
        assertEquals(0.1, calc.co2Kg(skill), 1e-9);   // OTHER-like category falls back to factor 1
        assertEquals(0.2, calc.co2Kg(time), 1e-9);    // GARDEN 2 x 0.1
        assertEquals(2, calc.hoursShared(skill));
        assertEquals(3, calc.hoursShared(time));
    }

    @Test
    void unknownCategoryUsesDefaultFactorOfOne() {
        Exchange e = new Exchange(ListingKind.ITEM, Category.OTHER, false, 1000, 0);
        assertEquals(0.6, calc.co2Kg(e), 1e-9);
    }

    @Test
    void summaryTotalsAreDeterministicAndRounded() {
        ImpactResponse r = calc.summarise(List.of(loan, gift, skill, time), 90, 4);
        assertEquals(4, r.completedExchanges());
        assertEquals(900 + 480 + 500 + 750, r.rupeesSavedEstimate());
        assertEquals(Math.round((1.2 + 0.6 + 0.1 + 0.2) * 10) / 10.0, r.co2KgAvoidedEstimate(), 1e-9);
        assertEquals(5, r.hoursShared());
        assertEquals(90, r.periodDays());
        assertEquals(4, r.activeMembers());
        assertEquals(4, r.byCategory().size());
        assertEquals(r, calc.summarise(List.of(loan, gift, skill, time), 90, 4));
    }

    @Test
    void emptyPeriodIsAllZero() {
        ImpactResponse r = calc.summarise(List.of(), 30, 0);
        assertEquals(0, r.rupeesSavedEstimate());
        assertEquals(0.0, r.co2KgAvoidedEstimate());
        assertTrue(r.byCategory().isEmpty());
    }

    @Test
    void methodologyAndDisclaimerAreAlwaysIncluded() {
        ImpactResponse r = calc.summarise(List.of(), 90, 0);
        assertTrue(r.disclaimer().startsWith("Estimates only"));
        assertTrue(r.methodology().size() >= 4);
        assertTrue(r.methodology().stream().anyMatch(m -> m.formula().contains("20%")));
        assertTrue(r.methodology().stream().anyMatch(m -> m.formula().contains("250")));
    }
}
