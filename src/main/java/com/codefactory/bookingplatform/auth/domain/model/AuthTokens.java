package com.codefactory.bookingplatform.auth.domain.model;

public record AuthTokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
