package com.codefactory.bookingplatform.notification;

import com.codefactory.bookingplatform.notification.application.BookingNoticeDeliveryUseCase;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.BookingFixture.Appointment;
import com.codefactory.bookingplatform.support.BookingFixture.Booking;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingNoticesIT extends JwtIntegrationTestBase {

    private static final String PREFERENCES = "/api/v1/notifications/booking-preferences";
    private static final String ORGANIZATION_SETTINGS = "/api/v1/organizations/{id}/booking-reminders";
    private static final String CREATED = "Reserva creada";
    private static final String REMINDER = "Recordatorio de reserva";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingNotices queue;

    @Autowired
    private BookingNoticeDeliveryUseCase delivery;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    private ProviderFixture providers;
    private BookingFixture bookings;
    private Provider provider;
    private UUID service;

    @BeforeEach
    void fixture() {
        providers = new ProviderFixture(jdbc);
        bookings = new BookingFixture(jdbc);
        provider = providers.activeProvider();
        service = providers.activeService(provider);
    }

    @AfterEach
    void cleanup() {
        bookings.clean();
    }

    @Test
    void committedBookingEventsReachBothAccountInboxesOnlyOnce() throws Exception {
        // Arrange
        Instant now = Instant.now().plusSeconds(120);
        var booking = create(now.plusSeconds(4 * 86400), "RESERVED");
        jdbc.update("update bookings set status = 'CONFIRMED' where id = ?", booking.id());
        jdbc.update("update bookings set starts_at = starts_at + interval '1 day', ends_at = ends_at + interval '1 day' where id = ?",
                booking.id());
        jdbc.update("update bookings set status = 'CANCELLED', cancelled_at = now() where id = ?", booking.id());
        jdbc.update("update bookings set status = status where id = ?", booking.id());
        jdbc.update("update organizations set contact_email = 'contact@external.example' where id = ?", provider.organizationId());

        // Act
        when(clock.instant()).thenReturn(now);
        List<Candidate> due = forBooking(booking.id(), now);
        due.forEach(delivery::deliver);
        due.forEach(delivery::deliver);
        var client = inbox(booking.clientId(), booking.clientEmail(), "CLIENT");
        var owner = inbox(provider.ownerId(), provider.ownerEmail(), "PROVIDER");
        var external = inbox(UUID.randomUUID(), "contact@external.example", "CLIENT");

        // Assert
        assertThat(client).containsExactlyInAnyOrder(CREATED, "Reserva confirmada", "Reserva reprogramada", "Reserva cancelada");
        assertThat(owner).containsExactlyInAnyOrder(CREATED, "Reserva confirmada", "Reserva reprogramada", "Reserva cancelada");
        assertThat(external).isEmpty();
        assertThat(pendingReminders(booking.id())).isZero();
    }

    @Test
    void configuredReminderDefersOnlyTheRecipientInQuietHoursAndIsDeliveredAfterwards() throws Exception {
        // Arrange
        Instant dueAt = LocalDate.now(ZoneOffset.UTC).plusDays(3).atTime(4, 0).toInstant(ZoneOffset.UTC);
        Instant afterQuietHours = dueAt.plusSeconds(8 * 3600L);
        Instant appointment = dueAt.plusSeconds(12 * 3600L);
        var booking = create(appointment, "CONFIRMED");
        mockMvc.perform(put(ORGANIZATION_SETTINGS, provider.organizationId())
                        .header("Authorization", ownerToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadMinutes\":[720]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leadMinutes[0]").value(720));
        preferences(provider.ownerId(), provider.ownerEmail(), "PROVIDER", true, "00:00", "00:00");
        preferences(booking.clientId(), booking.clientEmail(), "CLIENT", true, "22:00", "07:00");

        // Act
        dispatch(booking.id(), dueAt.minusSeconds(1));
        var ownerBeforeDue = inbox(provider.ownerId(), provider.ownerEmail(), "PROVIDER");
        dispatch(booking.id(), dueAt);
        var ownerAtDue = inbox(provider.ownerId(), provider.ownerEmail(), "PROVIDER");
        var clientAtDue = inbox(booking.clientId(), booking.clientEmail(), "CLIENT");
        dispatch(booking.id(), afterQuietHours);
        var clientAfterQuietHours = inbox(booking.clientId(), booking.clientEmail(), "CLIENT");

        // Assert
        assertThat(ownerBeforeDue).containsExactly(CREATED);
        assertThat(ownerAtDue).containsExactly(REMINDER, CREATED);
        assertThat(clientAtDue).containsExactly(CREATED);
        assertThat(clientAfterQuietHours).containsExactly(REMINDER, CREATED);
    }

    @Test
    void optOutAndCancellationSuppressRemindersWhileEssentialNoticesStillArrive() throws Exception {
        // Arrange
        Instant dueAt = LocalDate.now(ZoneOffset.UTC).plusDays(3).atTime(15, 0).toInstant(ZoneOffset.UTC);
        Instant appointment = dueAt.plusSeconds(7200);
        var cancelled = create(appointment, "CONFIRMED");
        var muted = create(appointment, "CONFIRMED");
        preferences(muted.clientId(), muted.clientEmail(), "CLIENT", false, "22:00", "07:00");
        jdbc.update("update bookings set status = 'CANCELLED', cancelled_at = now() where id = ?", cancelled.id());

        // Act
        dispatch(cancelled.id(), dueAt);
        dispatch(muted.id(), dueAt);
        var cancelledInbox = inbox(cancelled.clientId(), cancelled.clientEmail(), "CLIENT");
        var mutedInbox = inbox(muted.clientId(), muted.clientEmail(), "CLIENT");
        var providerInbox = inbox(provider.ownerId(), provider.ownerEmail(), "PROVIDER");

        // Assert
        assertThat(cancelledInbox).containsExactlyInAnyOrder(CREATED, "Reserva cancelada");
        assertThat(mutedInbox).containsExactly(CREATED);
        assertThat(providerInbox).contains(CREATED, "Reserva cancelada", REMINDER);
        assertThat(pendingReminders(cancelled.id())).isZero();
    }

    @Test
    void rollbackDiscardsTheNoticesAndTheAuditRecord() {
        // Arrange
        Instant startsAt = Instant.now().plusSeconds(3 * 86400);
        var transaction = new TransactionTemplate(transactionManager);

        // Act
        UUID bookingId = transaction.execute(tx -> {
            var booking = create(startsAt, "CONFIRMED");
            tx.setRollbackOnly();
            return booking.id();
        });

        // Assert
        assertThat(jdbc.queryForObject("select count(*) from booking_notices where booking_id = ?", Long.class, bookingId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_id = ?", Long.class, bookingId))
                .isZero();
    }

    private Booking create(Instant appointment, String state) {
        return bookings.create(provider, service, new Appointment(appointment, Instant.now(), state, 200));
    }

    private void preferences(UUID id, String address, String role, boolean enabled, String from, String to) throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer(id, address, role))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"remindersEnabled":%s,"quietFrom":"%s","quietUntil":"%s","timezone":"America/Bogota"}
                                """.formatted(enabled, from, to)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PREFERENCES).header("Authorization", bearer(id, address, role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remindersEnabled").value(enabled));
    }

    private void dispatch(UUID booking, Instant at) {
        when(clock.instant()).thenReturn(at);
        forBooking(booking, at).forEach(delivery::deliver);
    }

    private List<Candidate> forBooking(UUID bookingId, Instant at) {
        return queue.due(at, 1000).stream().filter(candidate -> candidate.bookingId().equals(bookingId)).toList();
    }

    private long pendingReminders(UUID id) {
        return jdbc.queryForObject("""
                select count(*) from booking_notices where booking_id = ? and kind = 'REMINDER' and processed_at is null
                """, Long.class, id);
    }

    private List<String> inbox(UUID id, String address, String role) throws Exception {
        String json = mockMvc.perform(get("/api/v1/notifications/emails").header("Authorization", bearer(id, address, role)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].subject");
    }

    private String ownerToken() {
        return bearer(provider.ownerId(), provider.ownerEmail(), "PROVIDER");
    }

    private static String bearer(UUID id, String address, String role) {
        return "Bearer " + jwks().accessToken(id, address, role);
    }
}
