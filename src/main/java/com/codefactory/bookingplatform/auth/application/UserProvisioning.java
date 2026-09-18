package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;

import java.util.UUID;

/**
 * Public facade of the auth module consumed by other business modules (e.g. identity).
 * Cross-module calls go through this interface only, never through auth.infrastructure.
 */
public interface UserProvisioning {

    UUID provisionClientUser(String email, String password);

    void deprovisionUser(UUID userId);

    void resendSignupVerification(String email);

    ConfirmedUser confirmEmail(String tokenHash);
}
