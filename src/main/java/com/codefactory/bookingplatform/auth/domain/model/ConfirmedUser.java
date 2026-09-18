package com.codefactory.bookingplatform.auth.domain.model;

import java.util.UUID;

public record ConfirmedUser(UUID userId, String email) {
}
