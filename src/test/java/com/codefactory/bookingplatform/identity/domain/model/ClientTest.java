package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        // Act
        Client client = newPendingClient();

        // Assert
        assertEquals(ClientStatus.PENDING_VERIFICATION, client.getStatus());
        assertFalse(client.canConfirmBooking());
    }

    @Test
    @DisplayName("Suspended client cannot confirm bookings")
    void suspendedClientCannotConfirmBookings() {
        // Arrange
        Client client = newPendingClient();
        client.verifyEmail();

        // Act
        client.suspend();

        // Assert
        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
        assertFalse(client.canConfirmBooking());
    }

    @Test
    @DisplayName("Suspended client can be reactivated after the full verify and suspend lifecycle")
    void suspensionLifecycle() {
        // Arrange
        Client pending = newPendingClient();
        pending.verifyEmail();
        pending.suspend();

        // Act
        pending.reactivate();

        // Assert
        assertEquals(ClientStatus.ACTIVE, pending.getStatus());
        assertTrue(pending.canConfirmBooking());
    }

    // --- Factory and projection ---------------------------------------------

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
        // Arrange
        Client client = clientInStatus(from);

        // Act
        applyTransition(client, transition);

        // Assert
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
        // Arrange
        Client client = clientInStatus(from);

        // Act
        Executable act = () -> applyTransition(client, transition);

        // Assert
        BusinessException ex = assertThrows(BusinessException.class, act);
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
            // Arrange
            Client client = clientInStatus(ClientStatus.SUSPENDED);

            // Act
            Executable act = client::verifyEmail;

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals("Client SUSPENDED cannot be moved to ACTIVE by email verification", ex.getMessage());
        }

        @Test
        @DisplayName("Suspending a pending client explains that verification comes first")
        void suspendPendingMessage() {
            // Arrange
            Client client = clientInStatus(ClientStatus.PENDING_VERIFICATION);

            // Act
            Executable act = client::suspend;

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
            assertEquals("A pending verification client cannot be suspended", ex.getMessage());
        }

        @ParameterizedTest(name = "reactivate on {0}")
        @DisplayName("Reactivating a client that is not suspended explains that only suspended clients qualify")
        @CsvSource({"PENDING_VERIFICATION", "ACTIVE"})
        void reactivateNonSuspendedMessage(ClientStatus from) {
            // Arrange
            Client client = clientInStatus(from);

            // Act
            Executable act = client::reactivate;

            // Assert
            BusinessException ex = assertThrows(BusinessException.class, act);
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
        // Arrange
        Client client = clientInStatus(status);

        // Act
        boolean canConfirm = client.canConfirmBooking();

        // Assert
        assertEquals(expected, canConfirm);
    }
}
