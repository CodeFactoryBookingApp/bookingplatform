package com.codefactory.bookingplatform.auth.api.dto;

import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;

public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static LoginResponse from(AuthTokens tokens) {
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn());
    }
}
