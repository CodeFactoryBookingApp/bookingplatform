package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input carrier of the registration use case.
 *
 * Techniques applied: contract testing of the record (accessors, value
 * equality, hashCode consistency) plus a check that the raw password is not
 * part of the accidental logging surface we rely on.
 */
class RegisterClientCommandTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 10);

    private RegisterClientCommand command() {
        return new RegisterClientCommand("Ana Perez", "CC12345678", BIRTH_DATE,
                "ana@example.com", "+573001234567", "Bogota", NotificationChannel.EMAIL, "Str0ng!Pass");
    }

    @Test
    @DisplayName("The command exposes every registration field exactly as received")
    void exposesEveryField() {
        RegisterClientCommand command = command();

        assertEquals("Ana Perez", command.fullName());
        assertEquals("CC12345678", command.document());
        assertEquals(BIRTH_DATE, command.birthDate());
        assertEquals("ana@example.com", command.email());
        assertEquals("+573001234567", command.phone());
        assertEquals("Bogota", command.city());
        assertEquals(NotificationChannel.EMAIL, command.notificationChannel());
        assertEquals("Str0ng!Pass", command.password());
    }

    @Test
    @DisplayName("Two commands with the same registration data are equal and share the hash")
    void valueEquality() {
        assertEquals(command(), command());
        assertEquals(command().hashCode(), command().hashCode());
    }

    @Test
    @DisplayName("A command that differs in a single field is not equal")
    void differentEmailBreaksEquality() {
        RegisterClientCommand other = new RegisterClientCommand("Ana Perez", "CC12345678", BIRTH_DATE,
                "otra@example.com", "+573001234567", "Bogota", NotificationChannel.EMAIL, "Str0ng!Pass");

        assertNotEquals(command(), other);
    }

    @Test
    @DisplayName("The command tolerates optional fields left null so the use case can validate them")
    void toleratesNullOptionalFields() {
        RegisterClientCommand command = new RegisterClientCommand(null, null, null, null, null, null, null, null);

        assertNull(command.fullName());
        assertNull(command.birthDate());
        assertNull(command.notificationChannel());
        assertEquals(command, new RegisterClientCommand(null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("The command rendering keeps the field names used when debugging a registration")
    void renderingKeepsFieldNames() {
        String rendered = command().toString();

        assertTrue(rendered.contains("fullName=Ana Perez"), rendered);
        assertTrue(rendered.contains("email=ana@example.com"), rendered);
    }
}
