package com.commonweave.impact;

import com.commonweave.listing.Category;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Assumption constants for the impact estimate. Echoed to clients in the methodology block. */
@ConfigurationProperties(prefix = "app.impact")
public record ImpactProperties(double loanShare, double giftShare, double serviceCo2Share, int hourlyRateInr,
                               double defaultCo2Kg, Map<Category, Double> co2KgByCategory) {

    public double co2Factor(Category category) {
        return co2KgByCategory.getOrDefault(category, defaultCo2Kg);
    }
}
