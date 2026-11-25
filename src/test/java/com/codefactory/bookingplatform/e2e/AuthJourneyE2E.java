package com.codefactory.bookingplatform.e2e;

import com.codefactory.bookingplatform.e2e.support.Accounts;
import com.codefactory.bookingplatform.e2e.support.Api;
import com.codefactory.bookingplatform.e2e.support.MailpitClient;
import com.codefactory.bookingplatform.e2e.support.Session;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S7 and S9, the authentication edge cases against the real stack: password recovery, account
 * lockout and logout.
 */
class AuthJourneyE2E {

    private static final String NEW_PASSWORD = "Yyyy456!y";

    @Test
    @DisplayName("S7 recovery: asking for a reset answers 202 and sends the 'Reset Your Password' email")
    void recoveryRequestSendsTheResetEmail() {
        // Arrange
        Session client = Accounts.verifiedClientSession();

        // Act
        Response request = requestRecovery(client.email());

        // Assert
        request.then().statusCode(202);
        MailpitClient.awaitToken(client.email(), MailpitClient.RESET_PASSWORD_SUBJECT);
    }

    @Test
    @DisplayName("S7 recovery: an unknown email also answers 202, so the endpoint does not reveal who has an account")
    void recoveryForAnUnknownEmailDoesNotRevealIt() {
        // Arrange
        String unknownEmail = Accounts.uniqueEmail("nobody");

        // Act
        Response request = requestRecovery(unknownEmail);

        // Assert
        request.then().statusCode(202);
    }

    // Fails today because of defect L5 (odd/tasks/e2e-automation.md): the reset answers 204 but
    // GoTrueClient.resetPasswordWithToken sends the new password to /verify, which ignores it.
    @Test
    @DisplayName("S9 reset: the new password logs in and the old one is rejected (fails until defect L5 is fixed)")
    void resetPasswordChangesThePassword() {
        // Arrange
        Session client = Accounts.verifiedClientSession();
        String tokenHash = requestResetToken(client.email());

        // Act
        Response reset = resetPassword(tokenHash, NEW_PASSWORD);

        // Assert
        assertEquals(204, reset.statusCode(), "the reset with a fresh token must be accepted");
        assertEquals(200, Api.login(client.email(), NEW_PASSWORD).statusCode(),
                "the new password must log in after the reset");
        assertEquals(401, Api.login(client.email(), client.password()).statusCode(),
                "the old password must be rejected after the reset");
    }

    @Test
    @DisplayName("S7 recovery: a reset token cannot be used twice, the second attempt answers 400 VERIFICATION_TOKEN_INVALID")
    void resetTokenCannotBeReused() {
        // Arrange
        Session client = Accounts.verifiedClientSession();
        String tokenHash = requestResetToken(client.email());
        resetPassword(tokenHash, NEW_PASSWORD).then().statusCode(204);

        // Act
        Response secondReset = resetPassword(tokenHash, "Zzzz789!z");

        // Assert
        secondReset.then()
                .statusCode(400)
                .body("errorCode", equalTo("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("S7 lockout: after 5 failed logins the account is locked with 429 and not even the right password gets in")
    void fiveFailedLoginsLockTheAccount() {
        // Arrange
        Session client = Accounts.verifiedClientSession();
        // The lock is checked before each attempt: the 5th failure still answers 401 and the next attempt is
        // refused. (A login before confirming the email counts as a failure too, so that client locks one sooner.)
        for (int attempt = 1; attempt <= 5; attempt++) {
            Api.login(client.email(), "Wrong-password-" + attempt).then()
                    .statusCode(401)
                    .body("errorCode", equalTo("INVALID_CREDENTIALS"));
        }

        // Act
        Response wrongPassword = Api.login(client.email(), "Wrong-password-6");
        Response rightPassword = Api.login(client.email(), client.password());

        // Assert
        wrongPassword.then()
                .statusCode(429)
                .body("errorCode", equalTo("ACCOUNT_LOCKED"))
                .body("details.retryAfterMinutes", equalTo("15"));
        rightPassword.then()
                .statusCode(429)
                .body("errorCode", equalTo("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("S7 logout: it answers 204 and the access token keeps working until it expires (ADR-0003)")
    void logoutAnswers204AndTheAccessTokenLivesUntilExpiry() {
        // Arrange
        Session client = Accounts.verifiedClientSession();

        // Act
        Response logout = Api.as(client.accessToken()).post("/api/v1/auth/logout");

        // Assert
        logout.then().statusCode(204);
        // ADR-0003: logout revokes the refresh token; an access token is a JWT valid until its expiry.
        Api.as(client.accessToken()).get("/api/v1/auth/me").then()
                .statusCode(200)
                .body("email", equalTo(client.email()));
    }

    private static Response requestRecovery(String email) {
        return Api.anonymous().body(Map.of("email", email)).post("/api/v1/auth/password-recovery-requests");
    }

    private static String requestResetToken(String email) {
        requestRecovery(email).then().statusCode(202);
        return MailpitClient.awaitToken(email, MailpitClient.RESET_PASSWORD_SUBJECT);
    }

    private static Response resetPassword(String tokenHash, String newPassword) {
        return Api.anonymous().body(Map.of("tokenHash", tokenHash, "newPassword", newPassword))
                .post("/api/v1/auth/password-resets");
    }
}
