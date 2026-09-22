package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value object returned by a successful authentication. Being a record it is immutable and compared
 * by value, which is what lets the application layer pass it around safely.
 */
class AuthTokensTest {

    private static final AuthTokens TOKENS = new AuthTokens("access-123", "refresh-456", "bearer", 3600L);

    @Test
    @DisplayName("The record exposes every token field exactly as it was built")
    void exposesEveryField() {
        assertEquals("access-123", TOKENS.accessToken());
        assertEquals("refresh-456", TOKENS.refreshToken());
        assertEquals("bearer", TOKENS.tokenType());
        assertEquals(3600L, TOKENS.expiresIn());
    }

    @Test
    @DisplayName("Two token sets with the same values are equal, because the record is compared by value")
    void equalValuesAreEqual() {
        assertEquals(TOKENS, new AuthTokens("access-123", "refresh-456", "bearer", 3600L));
    }

    @Test
    @DisplayName("Equal token sets share the same hash code, so they can be used as map keys")
    void equalValuesShareHashCode() {
        assertEquals(TOKENS.hashCode(), new AuthTokens("access-123", "refresh-456", "bearer", 3600L).hashCode());
    }

    @Test
    @DisplayName("A different access token makes the value object different")
    void differentAccessTokenIsNotEqual() {
        assertNotEquals(TOKENS, new AuthTokens("other", "refresh-456", "bearer", 3600L));
    }

    @Test
    @DisplayName("A different expiry makes the value object different")
    void differentExpiryIsNotEqual() {
        assertNotEquals(TOKENS, new AuthTokens("access-123", "refresh-456", "bearer", 60L));
    }

    @Test
    @DisplayName("The value object is never equal to null or to another type")
    void notEqualToNullOrOtherTypes() {
        assertNotEquals(null, TOKENS);
        assertNotEquals("access-123", TOKENS);
    }

    @Test
    @DisplayName("The textual form names the record and its fields, which is what ends up in the logs")
    void toStringDescribesTheRecord() {
        String text = TOKENS.toString();
        assertTrue(text.contains("AuthTokens"), () -> "Expected the record name in " + text);
        assertTrue(text.contains("accessToken"), () -> "Expected the field names in " + text);
    }

    @Test
    @DisplayName("Missing tokens are accepted as null so a partial upstream response can still be modelled")
    void nullFieldsAreAccepted() {
        AuthTokens partial = new AuthTokens(null, null, null, 0L);
        assertNull(partial.accessToken());
        assertNull(partial.refreshToken());
        assertNull(partial.tokenType());
        assertEquals(0L, partial.expiresIn());
    }
}
