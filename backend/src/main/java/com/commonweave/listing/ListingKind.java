package com.commonweave.listing;

public enum ListingKind {
    ITEM("Item"), SKILL("Skill"), TIME("Time");

    private final String label;

    ListingKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
