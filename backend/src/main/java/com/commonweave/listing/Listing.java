package com.commonweave.listing;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long circleId;
    private Long ownerId;
    @Enumerated(EnumType.STRING)
    private ListingType type;
    @Enumerated(EnumType.STRING)
    private ListingKind kind;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private Category category;
    private String tags;
    private LocalDate availableFrom;
    private LocalDate availableTo;
    private String pickupPreference;
    private int estimatedValueInr;
    private int depositInr;
    private int hoursEstimate;
    private boolean returnExpected;
    @Enumerated(EnumType.STRING)
    private ListingStatus status;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public List<String> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).filter(t -> !t.isBlank()).toList();
    }
}
