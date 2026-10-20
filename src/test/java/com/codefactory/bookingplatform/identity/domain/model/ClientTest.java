package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("Email verification moves client to ACTIVE and enables booking confirmation")
    void verifyEmailActivatesClient() {
        // Arrange
        Client client = newPendingClient();

        // Act
        client.verifyEmail();

        // Assert
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
        assertTrue(client.canConfirmBooking());
    }

    @Test
    @DisplayName("Verifying twice is idempotent")
    void verifyTwiceIsIdempotent() {
        // Arrange
        Client client = newPendingClient();
        client.verifyEmail();

        // Act
        client.verifyEmail();

        // Assert
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
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
    @DisplayName("Suspended client cannot be verified")
    void suspendedClientCannotBeVerified() {
        // Arrange
        Client client = newPendingClient();
        client.verifyEmail();
        client.suspend();

        // Act
        Executable act = client::verifyEmail;

        // Assert
        BusinessException ex = assertThrows(BusinessException.class, act);
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode());
    }

    @Test
    @DisplayName("Pending client cannot be suspended")
    void pendingClientCannotBeSuspended() {
        // Arrange
        Client pending = newPendingClient();

        // Act
        Executable act = pending::suspend;

        // Assert
        assertThrows(BusinessException.class, act);
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

    @Test
    @DisplayName("The pendingVerification factory keeps every attribute it receives")
    void factoryKeepsEveryAttribute() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDate birthDate = LocalDate.of(1990, 1, 31);

        // Act
        Client client = Client.pendingVerification(id, "Ana Perez", "CC-98765", birthDate,
                "ana@example.com", "+573001234567", "Medellin", NotificationChannel.WHATSAPP);

        // Assert
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
        // Act
        Client suspended = clientInStatus(ClientStatus.SUSPENDED);

        // Assert
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

    @ParameterizedTest
    @DisplayName("Every declared status is covered by the booking decision table")
    @EnumSource(ClientStatus.class)
    void everyStatusAnswersTheBookingInvariant(ClientStatus status) {
        // Arrange
        Client client = clientInStatus(status);

        // Act
        boolean canConfirm = client.canConfirmBooking();

        // Assert
        assertEquals(status == ClientStatus.ACTIVE, canConfirm);
    }

    // --- Longer paths through the machine ------------------------------------

    /*
     * The original test ran three suspend/reactivate cycles with assertions inside the loop.
     * It is split into one test per transition of the cycle, parameterized over how many
     * full cycles already happened (0, 1, 2), so every intermediate check is still made.
     */

    @ParameterizedTest(name = "after {0} previous cycles, suspend blocks bookings")
    @DisplayName("Inside a repeated suspend/reactivate cycle, every suspension blocks booking confirmation")
    @ValueSource(ints = {0, 1, 2})
    void suspendInsideTheCycleBlocksBookings(int previousCycles) {
        // Arrange
        Client client = newPendingClient();
        client.verifyEmail();
        for (int cycle = 0; cycle < previousCycles; cycle++) {
            client.suspend();
            client.reactivate();
        }

        // Act
        client.suspend();

        // Assert
        assertFalse(client.canConfirmBooking());
    }

    @ParameterizedTest(name = "after {0} previous cycles, reactivate restores bookings")
    @DisplayName("A client can be suspended and reactivated repeatedly without losing the booking invariant")
    @ValueSource(ints = {0, 1, 2})
    void suspendReactivateCycleIsRepeatable(int previousCycles) {
        // Arrange
        Client client = newPendingClient();
        client.verifyEmail();
        for (int cycle = 0; cycle < previousCycles; cycle++) {
            client.suspend();
            client.reactivate();
        }
        client.suspend();

        // Act
        client.reactivate();

        // Assert
        assertTrue(client.canConfirmBooking());
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
    }

    @Test
    @DisplayName("Suspending twice keeps the client suspended instead of failing")
    void suspendTwiceIsIdempotent() {
        // Arrange
        Client client = clientInStatus(ClientStatus.ACTIVE);
        client.suspend();

        // Act
        client.suspend();

        // Assert
        assertEquals(ClientStatus.SUSPENDED, client.getStatus());
    }

    @Test
    @DisplayName("A reactivated client can be verified again without error because verification is idempotent on ACTIVE")
    void verifyAfterReactivationIsIdempotent() {
        // Arrange
        Client client = clientInStatus(ClientStatus.SUSPENDED);
        client.reactivate();

        // Act
        client.verifyEmail();

        // Assert
        assertEquals(ClientStatus.ACTIVE, client.getStatus());
    }
}
