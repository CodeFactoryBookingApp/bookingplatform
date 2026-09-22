package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Catalogue of failures the identity provider can report. The adapter translates the upstream
 * response into one of these, so the set is the contract between the Supabase adapter and the
 * domain.
 */
class UpstreamAuthErrorTest {

    @Test
    @DisplayName("The catalogue covers the eight failures the identity provider can report")
    void catalogueHasTheEightKnownFailures() {
        assertIterableEquals(
                List.of(UpstreamAuthError.INVALID_CREDENTIALS,
                        UpstreamAuthError.EMAIL_NOT_CONFIRMED,
                        UpstreamAuthError.USER_ALREADY_EXISTS,
                        UpstreamAuthError.USER_NOT_FOUND,
                        UpstreamAuthError.TOKEN_INVALID,
                        UpstreamAuthError.TOKEN_EXPIRED,
                        UpstreamAuthError.RATE_LIMITED,
                        UpstreamAuthError.UNAVAILABLE),
                Stream.of(UpstreamAuthError.values()).toList());
    }

    @ParameterizedTest(name = "{0} round-trips through its name")
    @EnumSource(UpstreamAuthError.class)
    @DisplayName("Every upstream failure can be resolved back from its own name")
    void everyErrorRoundTripsThroughItsName(UpstreamAuthError error) {
        assertEquals(error, UpstreamAuthError.valueOf(error.name()));
    }

    @ParameterizedTest(name = "{0} has a name")
    @EnumSource(UpstreamAuthError.class)
    @DisplayName("Every upstream failure exposes a non-null name")
    void everyErrorHasAName(UpstreamAuthError error) {
        assertNotNull(error.name());
    }

    @Test
    @DisplayName("An upstream failure the domain does not know about is rejected instead of being mapped blindly")
    void unknownErrorNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> UpstreamAuthError.valueOf("QUOTA_EXCEEDED"));
    }
}
