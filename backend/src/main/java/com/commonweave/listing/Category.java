package com.commonweave.listing;

public enum Category {
    TOOLS("Tools & DIY"),
    KITCHEN("Kitchen & Home"),
    GARDEN("Garden"),
    ELECTRONICS("Electronics"),
    FURNITURE("Furniture"),
    BOOKS("Books"),
    KIDS("Kids & Baby"),
    SPORTS_CYCLES("Sports & Cycles"),
    REPAIR_SKILLS("Repair Skills"),
    TUTORING("Tutoring & Lessons"),
    EVENTS("Events & Decor"),
    OTHER("Other");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
