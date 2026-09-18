package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTest {

    private Client newPendingClient() {
        return Client.pendingVerification(UUID.randomUUID(), "Ana Perez", "12345678",
                LocalDate.of(1995, 4, 10), "ana@example.com", "+573001234567",
                "Bogota", NotificationChannel.EMAIL);
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
}
