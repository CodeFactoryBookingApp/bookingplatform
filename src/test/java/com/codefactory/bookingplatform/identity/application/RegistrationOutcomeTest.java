package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Output carrier shared by registration and email confirmation.
 *
 * Techniques applied: contract testing of the record (accessors, value
 * equality, hashCode consistency) and equivalence partitioning over the
 * statuses the use cases can return.
 */
class RegistrationOutcomeTest {

    private static final UUID CLIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("The outcome exposes the client id, email and status returned to the API")
    void exposesEveryField() {
        RegistrationOutcome outcome =
                new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.PENDING_VERIFICATION);

        assertEquals(CLIENT_ID, outcome.clientId());
        assertEquals("ana@example.com", outcome.email());
        assertEquals(ClientStatus.PENDING_VERIFICATION, outcome.status());
    }

    @Test
    @DisplayName("Two outcomes describing the same registration are equal and share the hash")
    void valueEquality() {
        RegistrationOutcome one = new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.ACTIVE);
        RegistrationOutcome another = new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.ACTIVE);

        assertEquals(one, another);
        assertEquals(one.hashCode(), another.hashCode());
    }

    @Test
    @DisplayName("Registration and confirmation outcomes of the same client differ by status")
    void differentStatusBreaksEquality() {
        RegistrationOutcome registered =
                new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.PENDING_VERIFICATION);
        RegistrationOutcome confirmed =
                new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.ACTIVE);

        assertNotEquals(registered, confirmed);
    }

    @ParameterizedTest
    @DisplayName("Any client status can be reported back without losing information")
    @EnumSource(ClientStatus.class)
    void carriesAnyStatus(ClientStatus status) {
        assertEquals(status, new RegistrationOutcome(CLIENT_ID, "ana@example.com", status).status());
    }

    @Test
    @DisplayName("The outcome rendering keeps the client id used when tracing a registration")
    void renderingKeepsClientId() {
        String rendered = new RegistrationOutcome(CLIENT_ID, "ana@example.com", ClientStatus.ACTIVE).toString();

        assertTrue(rendered.contains(CLIENT_ID.toString()), rendered);
    }
}
