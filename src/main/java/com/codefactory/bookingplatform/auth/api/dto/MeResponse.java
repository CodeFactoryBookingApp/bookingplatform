package com.codefactory.bookingplatform.auth.api.dto;

import java.util.UUID;

public record MeResponse(UUID id, String email, String role) {
}
