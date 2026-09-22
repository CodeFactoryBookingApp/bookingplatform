package com.codefactory.bookingplatform.identity;

import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-001 - acceptance criterion "cliente no verificado no puede confirmar reserva".
 *
 * <p>There is no booking flow in Sprint 1, so the criterion cannot be verified through an endpoint.
 * What <em>can</em> be verified, and is not verified anywhere today, is that the invariant is
 * computable from persisted state: {@code ClientTest} exercises {@code canConfirmBooking()} on an
 * object built in memory, which proves nothing about a client that went through the HTTP API, the
 * mapper and PostgreSQL. This test rebuilds the aggregate from the database at both ends of the
 * verification transition.
 *
 * @see com.codefactory.bookingplatform.architecture.BookingConfirmationInvariantTest for the guard
 *      that fires when a booking-confirmation path is added without consulting the invariant.
 */
class ClientVerificationInvariantIT extends JwtIntegrationTestBase {

    private static final String EMAIL = "ana.perez@example.com";
    private static final String DOCUMENT = "CC-1020304050";

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("HU-001 AC: a client persisted as PENDING_VERIFICATION cannot confirm bookings")
    void pendingClientCannotConfirmBookings() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated());

        UUID clientId = identityProvider.userIdOf(EMAIL);
        Client pending = reload(clientId);
        assertTrue(pending.getStatus() == ClientStatus.PENDING_VERIFICATION);
        assertFalse(pending.canConfirmBooking(),
                "an unverified client, read back from the database, must not be able to confirm a booking");
    }

    @Test
    @DisplayName("HU-001 AC: only after confirming the email does the client become able to confirm bookings")
    void confirmedClientCanConfirmBookings() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(EMAIL, DOCUMENT)))
                .andExpect(status().isCreated());
        UUID clientId = identityProvider.userIdOf(EMAIL);
        assertFalse(reload(clientId).canConfirmBooking());

        mockMvc.perform(post("/api/v1/registrations/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenHash\":\"" + identityProvider.currentEmailToken(EMAIL) + "\"}"))
                .andExpect(status().isOk());

        Client active = reload(clientId);
        assertTrue(active.getStatus() == ClientStatus.ACTIVE);
        assertTrue(active.canConfirmBooking(),
                "the verification transition must be what flips the invariant, and it must survive persistence");
    }

    private Client reload(UUID clientId) {
        return clientRepository.findById(clientId).orElseThrow();
    }
}
