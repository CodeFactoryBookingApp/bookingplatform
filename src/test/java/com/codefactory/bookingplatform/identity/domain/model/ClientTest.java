package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client aggregate: state machine {PENDING_VERIFICATION, ACTIVE, SUSPENDED}
 * over the transitions {verifyEmail, suspend, reactivate}.
 *
 * Techniques applied:
 * - State transition testing (the full 3x3 table is exercised cell by cell).
 * - Decision table for canConfirmBooking (one row per status).
 * - Branch coverage: every guard inside verifyEmail/suspend/reactivate, both
 *   for the path that mutates the state and for the path that throws.
 */
class ClientTest {

    private Client newPendingClient() {
        return Client.pendingVerification(UUID.randomUUID(), "Ana Perez", "12345678",
                LocalDate.of(1995, 4, 10), "ana@example.com", "+573001234567",
                "Bogota", NotificationChannel.EMAIL);
    }

    private Client clientInStatus(ClientStatus status) {
        return new Client(UUID.randomUUID(), "Ana Perez", "12345678",
                LocalDate.of(1995, 4, 10), "ana@example.com", "+573001234567",
                "Bogota", NotificationChannel.EMAIL, status);
    }

    @Test
    @DisplayName("New client starts as PENDING_VERIFICATION and cannot confirm bookings")
    void newClientIsPendingVerification() {
        Client client = newPendingClient();
        assertEquals(ClientStatus.PENDING_VERIFICATION, client.getStatus());
        assertFalse(client.canConfirmBooking());
    }

    @Test
    @DisplayName("Email verification moves client to ACTIVE and enables booking confirmation")
    void verifyEmailActivatesClient() {
        Client client = newPendingClient();
        client.verifyEmail();
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
        assertTrue(client.canConfirmBooking());
    }

    @Test
    @DisplayName("Verifying twice is idempotent")
    void verifyTwiceIsIdempotent() {
        Client client = newPendingClient();
        client.verifyEmail();
        client.verifyEmail();
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
    }

    @Test
    @DisplayName("Suspended client cannot confirm bookings and cannot be verified")
    void suspendedClientCannotConfirmBookings() {
        Client client = newPendingClient();
        client.verifyEmail();
        client.suspend();
        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
        assertFalse(client.canConfirmBooking());
        BusinessException ex = assertThrows(BusinessException.class, client::verifyEmail);
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
    }

    @Test
    @DisplayName("Pending client cannot be suspended; suspended can be reactivated")
    void suspensionLifecycle() {
        Client pending = newPendingClient();
        assertThrows(BusinessException.class, pending::suspend);

        pending.verifyEmail();
        pending.suspend();
        pending.reactivate();
        assertEquals(ClientStatus.ACTIVE, pending.getStatus());
        assertTrue(pending.canConfirmBooking());
    }

    // --- Factory and projection ---------------------------------------------

    @Test
    @DisplayName("The pendingVerification factory keeps every attribute it receives")
    void factoryKeepsEveryAttribute() {
        UUID id = UUID.randomUUID();
        LocalDate birthDate = LocalDate.of(1990, 1, 31);
        Client client = Client.pendingVerification(id, "Ana Perez", "CC-98765", birthDate,
                "ana@example.com", "+573001234567", "Medellin", NotificationChannel.WHATSAPP);

        assertEquals(id, client.getId());
        assertEquals("Ana Perez", client.getFullName());
        assertEquals("CC-98765", client.getDocument());
        assertEquals(birthDate, client.getBirthDate());
        assertEquals("ana@example.com", client.getEmail());
        assertEquals("+573001234567", client.getPhone());
        assertEquals("Medellin", client.getCity());
        assertEquals(NotificationChannel.WHATSAPP, client.getNotificationChannel());
        assertEquals(ClientStatus.PENDING_VERIFICATION, client.getStatus());
    }

    @Test
    @DisplayName("The rehydration constructor accepts any persisted status without re-running the state machine")
    void rehydrationConstructorAcceptsAnyStatus() {
        Client suspended = clientInStatus(ClientStatus.SUSPENDED);
        assertEquals(ClientStatus.SUSPENDED, suspended.getStatus());
        assertNotNull(suspended.getId());
    }

    // --- State transition table: 3 states x 3 transitions = 9 cells ----------

    /**
     * from                 | verifyEmail          | suspend              | reactivate
     * ---------------------+----------------------+----------------------+-------------------
     * PENDING_VERIFICATION | to ACTIVE            | BusinessException    | BusinessException
     * ACTIVE               | stays ACTIVE (no-op) | to SUSPENDED         | BusinessException
     * SUSPENDED            | BusinessException    | stays SUSPENDED      | to ACTIVE
     */
    @ParameterizedTest(name = "{0} + {1} -> {2}")
    @DisplayName("State transition table: every allowed transition lands on its target status")
    @CsvSource({
            "PENDING_VERIFICATION, verifyEmail, ACTIVE",
            "ACTIVE,               verifyEmail, ACTIVE",
            "ACTIVE,               suspend,     SUSPENDED",
            "SUSPENDED,            suspend,     SUSPENDED",
            "SUSPENDED,            reactivate,  ACTIVE"
    })
    void allowedTransitions(ClientStatus from, String transition, ClientStatus expected) {
        Client client = clientInStatus(from);

        applyTransition(client, transition);

        assertEquals(expected, client.getStatus());
    }

    @ParameterizedTest(name = "{0} + {1} is rejected")
    @DisplayName("State transition table: every forbidden transition raises VALIDATION_ERROR and leaves the status untouched")
    @CsvSource({
            "PENDING_VERIFICATION, suspend",
            "PENDING_VERIFICATION, reactivate",
            "ACTIVE,               reactivate",
            "SUSPENDED,            verifyEmail"
    })
    void forbiddenTransitions(ClientStatus from, String transition) {
        Client client = clientInStatus(from);

        BusinessException ex = assertThrows(BusinessException.class, () -> applyTransition(client, transition));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
        assertNotNull(ex.getMessage());
        assertEquals(from, client.getStatus(), "a rejected transition must not mutate the aggregate");
    }

    private void applyTransition(Client client, String transition) {
        switch (transition) {
            case "verifyEmail" -> client.verifyEmail();
            case "suspend" -> client.suspend();
            case "reactivate" -> client.reactivate();
            default -> throw new IllegalArgumentException("Unknown transition " + transition);
        }
    }

    @Nested
    @DisplayName("Messages of the rejected transitions")
    class RejectionMessages {

        @Test
        @DisplayName("Verifying a suspended client explains that the status blocks the activation")
        void verifySuspendedMessage() {
            Client client = clientInStatus(ClientStatus.SUSPENDED);
            BusinessException ex = assertThrows(BusinessException.class, client::verifyEmail);
            assertEquals("Client SUSPENDED cannot be moved to ACTIVE by email verification", ex.getMessage());
        }

        @Test
        @DisplayName("Suspending a pending client explains that verification comes first")
        void suspendPendingMessage() {
            Client client = clientInStatus(ClientStatus.PENDING_VERIFICATION);
            BusinessException ex = assertThrows(BusinessException.class, client::suspend);
            assertEquals("A pending verification client cannot be suspended", ex.getMessage());
        }

        @ParameterizedTest(name = "reactivate on {0}")
        @DisplayName("Reactivating a client that is not suspended explains that only suspended clients qualify")
        @CsvSource({"PENDING_VERIFICATION", "ACTIVE"})
        void reactivateNonSuspendedMessage(ClientStatus from) {
            Client client = clientInStatus(from);
            BusinessException ex = assertThrows(BusinessException.class, client::reactivate);
            assertEquals("Only suspended clients can be reactivated", ex.getMessage());
        }
    }

    // --- Decision table for the booking invariant ----------------------------

    @ParameterizedTest(name = "{0} can confirm bookings? {1}")
    @DisplayName("Only an ACTIVE client may confirm bookings")
    @CsvSource({
            "PENDING_VERIFICATION, false",
            "ACTIVE,               true",
            "SUSPENDED,            false"
    })
    void canConfirmBookingDecisionTable(ClientStatus status, boolean expected) {
        assertEquals(expected, clientInStatus(status).canConfirmBooking());
    }

    @ParameterizedTest
    @DisplayName("Every declared status is covered by the booking decision table")
    @EnumSource(ClientStatus.class)
    void everyStatusAnswersTheBookingInvariant(ClientStatus status) {
        Client client = clientInStatus(status);
        assertEquals(status == ClientStatus.ACTIVE, client.canConfirmBooking());
    }

    // --- Longer paths through the machine ------------------------------------

    @Test
    @DisplayName("A client can be suspended and reactivated repeatedly without losing the booking invariant")
    void suspendReactivateCycleIsRepeatable() {
        Client client = newPendingClient();
        client.verifyEmail();

        for (int cycle = 0; cycle < 3; cycle++) {
            client.suspend();
            assertFalse(client.canConfirmBooking());
            client.reactivate();
            assertTrue(client.canConfirmBooking());
        }
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
    }

    @Test
    @DisplayName("Suspending twice keeps the client suspended instead of failing")
    void suspendTwiceIsIdempotent() {
        Client client = clientInStatus(ClientStatus.ACTIVE);
        client.suspend();
        client.suspend();
        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
    }

    @Test
    @DisplayName("A reactivated client can be verified again without error because verification is idempotent on ACTIVE")
    void verifyAfterReactivationIsIdempotent() {
        Client client = clientInStatus(ClientStatus.SUSPENDED);
        client.reactivate();
        client.verifyEmail();
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
    }
}
