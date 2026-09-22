package com.codefactory.bookingplatform.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error catalogue is the public API contract: every code maps to one HTTP status and one
 * default message, and both travel to the client inside the ProblemDetail body.
 *
 * <p>Black box: the whole enum is swept with {@code values()} so a code added later without a
 * status or a message fails here rather than in production; the status of each code is then pinned
 * against its documented semantics with a decision table.</p>
 */
class ErrorCodeTest {

    @ParameterizedTest(name = "{0} declares an HTTP status")
    @EnumSource(ErrorCode.class)
    @DisplayName("Every error code declares a non-null HTTP status, because the handler builds the response from it")
    void everyCodeDeclaresAStatus(ErrorCode code) {
        assertNotNull(code.status(), () -> code.name() + " has no HTTP status");
    }

    @ParameterizedTest(name = "{0} declares a default message")
    @EnumSource(ErrorCode.class)
    @DisplayName("Every error code declares a non-blank default message, which is what the client reads when no specific one is given")
    void everyCodeDeclaresAMessage(ErrorCode code) {
        assertNotNull(code.defaultMessage(), () -> code.name() + " has no default message");
        assertFalse(code.defaultMessage().isBlank(), () -> code.name() + " has a blank default message");
    }

    @ParameterizedTest(name = "{0} is an error status")
    @EnumSource(ErrorCode.class)
    @DisplayName("Every error code maps to a 4xx or 5xx status, never to a success or a redirect")
    void everyCodeMapsToAnErrorStatus(ErrorCode code) {
        assertTrue(code.status().isError(),
                () -> code.name() + " maps to " + code.status() + ", which is not an error status");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "VALIDATION_ERROR,            BAD_REQUEST",
            "MINOR_NOT_ALLOWED,           BAD_REQUEST",
            "PASSWORD_TOO_WEAK,           BAD_REQUEST",
            "VERIFICATION_TOKEN_INVALID,  BAD_REQUEST",
            "AUTH_REQUIRED,               UNAUTHORIZED",
            "INVALID_CREDENTIALS,         UNAUTHORIZED",
            "AUTH_TOKEN_INVALID,          UNAUTHORIZED",
            "RESOURCE_NOT_FOUND,          NOT_FOUND",
            "EMAIL_NOT_CONFIRMED,         FORBIDDEN",
            "ACCESS_DENIED,               FORBIDDEN",
            "DUPLICATE_EMAIL,             CONFLICT",
            "DUPLICATE_DOCUMENT,          CONFLICT",
            "RATE_LIMITED,                TOO_MANY_REQUESTS",
            "ACCOUNT_LOCKED,              TOO_MANY_REQUESTS",
            "UPSTREAM_AUTH_ERROR,         BAD_GATEWAY",
            "INTERNAL_ERROR,              INTERNAL_SERVER_ERROR"
    })
    @DisplayName("Each error code maps to the HTTP status its semantics demand")
    void statusMatchesTheSemanticsOfTheCode(ErrorCode code, HttpStatus expectedStatus) {
        assertEquals(expectedStatus, code.status());
    }

    @Test
    @DisplayName("The decision table above covers every code in the catalogue, so a new code cannot slip through untested")
    void decisionTableCoversTheWholeCatalogue() {
        assertEquals(16, ErrorCode.values().length,
                "A code was added or removed: update the status decision table in this test");
    }

    @Test
    @DisplayName("Only the upstream failure and the internal error are server faults, every other code blames the caller")
    void onlyTwoCodesAreServerFaults() {
        assertEquals(List.of(ErrorCode.UPSTREAM_AUTH_ERROR, ErrorCode.INTERNAL_ERROR),
                Arrays.stream(ErrorCode.values()).filter(c -> c.status().is5xxServerError()).toList());
    }

    @Test
    @DisplayName("An upstream failure is reported as a gateway problem, not as a client mistake")
    void upstreamFailureIsAGatewayProblem() {
        assertTrue(ErrorCode.UPSTREAM_AUTH_ERROR.status().is5xxServerError());
    }

    @Test
    @DisplayName("A locked account is reported as a rate-limit status so clients back off instead of retrying")
    void lockedAccountIsRateLimited() {
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.ACCOUNT_LOCKED.status());
    }

    @ParameterizedTest(name = "{0} round-trips through its name")
    @EnumSource(ErrorCode.class)
    @DisplayName("Every error code can be resolved back from its own name, which is the value sent to the client")
    void everyCodeRoundTripsThroughItsName(ErrorCode code) {
        assertEquals(code, ErrorCode.valueOf(code.name()));
    }

    @Test
    @DisplayName("An unknown error code name is rejected rather than resolved to a default")
    void unknownCodeNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.valueOf("TEAPOT"));
    }
}
