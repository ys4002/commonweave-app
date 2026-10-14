package com.commonweave.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@Size(max = 80) String displayName, @Size(max = 500) String bio,
                                   @Size(max = 120) String locality, Boolean onboarded) {
}
