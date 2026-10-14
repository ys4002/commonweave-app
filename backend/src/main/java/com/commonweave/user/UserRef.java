package com.commonweave.user;

/** Minimal public view of a person, used across DTOs. */
public record UserRef(Long id, String displayName) {
    public static UserRef of(User u) {
        return new UserRef(u.getId(), u.getDisplayName());
    }
}
