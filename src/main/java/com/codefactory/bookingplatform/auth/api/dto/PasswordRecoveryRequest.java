package com.codefactory.bookingplatform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        String email) {
}
