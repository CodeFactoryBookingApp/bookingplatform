package com.codefactory.bookingplatform.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailRequest(
        @NotBlank(message = "tokenHash is required")
        String tokenHash) {
}
