package com.codefactory.bookingplatform.e2e.support;

/** A user that exists in the stack and has logged in: who it is and the access token it holds. */
public record Session(String email, String password, String userId, String accessToken) {
}
