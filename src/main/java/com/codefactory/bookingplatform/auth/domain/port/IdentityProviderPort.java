package com.codefactory.bookingplatform.auth.domain.port;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;

import java.util.UUID;

/**
 * Port to the external identity provider (Supabase Auth / GoTrue).
 * Implemented by auth.infrastructure.supabase.GoTrueClient.
 */
public interface IdentityProviderPort {

    UUID createUser(String email, String password, AppRole role);

    void deleteUser(UUID userId);

    AuthTokens requestPasswordToken(String email, String password);

    ConfirmedUser verifyEmailToken(String tokenHash);

    void resendSignupVerification(String email);

    void sendPasswordRecovery(String email);

    void resetPasswordWithToken(String tokenHash, String newPassword);

    void signOut(String accessToken);
}
