package com.commonweave.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters") String password,
            @NotBlank @Size(max = 80) String displayName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }
}
