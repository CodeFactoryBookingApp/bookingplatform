package com.codefactory.bookingplatform.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value object describing the user whose email has just been confirmed. It is the pair the identity
 * module needs to link the platform account with the identity provider account.
 */
class ConfirmedUserTest {

    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final ConfirmedUser USER = new ConfirmedUser(USER_ID, "ana@example.com");

    @Test
    @DisplayName("The record exposes the identifier and the email exactly as they were built")
    void exposesEveryField() {
        assertEquals(USER_ID, USER.userId());
        assertEquals("ana@example.com", USER.email());
    }

    @Test
    @DisplayName("Two confirmed users with the same identifier and email are equal")
    void equalValuesAreEqual() {
        assertEquals(USER, new ConfirmedUser(USER_ID, "ana@example.com"));
    }

    @Test
    @DisplayName("Equal confirmed users share the same hash code")
    void equalValuesShareHashCode() {
        assertEquals(USER.hashCode(), new ConfirmedUser(USER_ID, "ana@example.com").hashCode());
    }

    @Test
    @DisplayName("A different identifier makes the value object different even when the email matches")
    void differentIdentifierIsNotEqual() {
        assertNotEquals(USER, new ConfirmedUser(UUID.randomUUID(), "ana@example.com"));
    }

    @Test
    @DisplayName("Email comparison is case sensitive, so the record does not normalise it")
    void emailComparisonIsCaseSensitive() {
        assertNotEquals(USER, new ConfirmedUser(USER_ID, "ANA@example.com"));
    }

    @Test
    @DisplayName("The value object is never equal to null or to another type")
    void notEqualToNullOrOtherTypes() {
        assertNotEquals(null, USER);
        assertNotEquals("ana@example.com", USER);
    }

    @Test
    @DisplayName("The textual form names the record and its fields")
    void toStringDescribesTheRecord() {
        String text = USER.toString();
        assertTrue(text.contains("ConfirmedUser"), () -> "Expected the record name in " + text);
        assertTrue(text.contains("email"), () -> "Expected the field names in " + text);
    }

    @Test
    @DisplayName("Null components are accepted, so the record does not validate on its own")
    void nullComponentsAreAccepted() {
        ConfirmedUser empty = new ConfirmedUser(null, null);
        assertNull(empty.userId());
        assertNull(empty.email());
    }
}
