package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Delivery;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Kind;
import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * HU-015 / ADR-0010: delivering one queued booking notice to the in-app inbox. Essential notices
 * (created, confirmed, cancelled, rescheduled) always go out; a reminder goes out only for a
 * confirmed, future appointment of a recipient who wants reminders, and is postponed to the end of
 * their quiet period when that ends before the appointment. The real {@link EmailNotifications} is
 * used, so what reaches the inbox port is what the user reads.
 *
 * <p>Techniques: decision table for reminders (enabled x current status x appointment in the future x
 * quiet period end vs appointment), boundary values (appointment = now, quiet end = appointment) and
 * equivalence partitioning of the notice kinds and booking states.
 * Times: "now" is 2026-11-21 12:00 UTC (07:00 in Bogota, outside the default quiet period).
 */
class BookingNoticeDeliveryUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");
    private static final UUID NOTICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant APPOINTMENT = Instant.parse("2026-11-25T15:00:00Z");
    private static final ReminderPreferences NO_QUIET =
            new ReminderPreferences(true, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, null);

    private BookingNotices notices;
    private SentEmailRepository inbox;
    private BookingNoticeDeliveryUseCase useCase;

    @BeforeEach
    void setUp() {
        notices = mock(BookingNotices.class);
        inbox = mock(SentEmailRepository.class);
        when(notices.lockBooking(BOOKING)).thenReturn(true);
        useCase = new BookingNoticeDeliveryUseCase(notices, new EmailNotifications(mock(EmailSender.class), inbox),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Delivery notice(Kind kind, String currentStatus, Instant appointment, ReminderPreferences preferences) {
        return new Delivery(NOTICE, kind, RECIPIENT, "ana@example.com", "BK-7F3A", "Corte de cabello", "Sede Centro",
                appointment, "CONFIRMED", "America/Bogota", currentStatus, preferences);
    }

    private void claims(Delivery delivery) {
        when(notices.claim(NOTICE, NOW)).thenReturn(Optional.of(delivery));
    }

    private void deliver() {
        useCase.deliver(new Candidate(NOTICE, BOOKING));
    }

    private EmailMessage storedMessage() {
        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(inbox).saveNotice(eq(NOTICE), eq(RECIPIENT),
                message.capture(), eq(NOW));
        return message.getValue();
    }

    @Nested
    @DisplayName("Claiming")
    class Claiming {

        @Test
        @DisplayName("ADR-0010: if the booking is locked by a writer (or gone), the notice is not claimed now and stays queued")
        void lockedBookingIsSkipped() {
            // Arrange
            when(notices.lockBooking(BOOKING)).thenReturn(false);

            // Act
            deliver();

            // Assert
            verify(notices, never()).claim(any(), any());
            verifyNoInteractions(inbox);
        }

        @Test
        @DisplayName("ADR-0010: a notice already delivered by another worker (nothing to claim) is not delivered twice")
        void alreadyClaimedNoticeIsNotDeliveredAgain() {
            // Arrange
            when(notices.claim(NOTICE, NOW)).thenReturn(Optional.empty());

            // Act
            deliver();

            // Assert
            verifyNoInteractions(inbox);
            verify(notices, never()).markProcessed(any(), any());
        }

        @Test
        @DisplayName("ADR-0010: a notice without a recipient account (no owner) is closed without an email")
        void noticeWithoutRecipientIsClosed() {
            // Arrange
            claims(new Delivery(NOTICE, Kind.CREATED, null, "contacto@example.com", "BK-7F3A", "Corte", null,
                    APPOINTMENT, "RESERVED", "America/Bogota", "RESERVED", ReminderPreferences.DEFAULTS));

            // Act
            deliver();

            // Assert
            verify(notices).markProcessed(NOTICE, NOW);
            verifyNoInteractions(inbox);
        }
    }

    @Nested
    @DisplayName("Essential notices")
    class Essential {

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @CsvSource({
                "CREATED,     Reserva creada",
                "CONFIRMED,   Reserva confirmada",
                "CANCELLED,   Reserva cancelada",
                "RESCHEDULED, Reserva reprogramada"
        })
        @DisplayName("HU-015 / ADR-0010: essential notices are delivered even with reminders off, in quiet hours and for a cancelled booking")
        void essentialNoticesIgnoreReminderSettings(Kind kind, String subject) {
            // Arrange: reminders off, quiet all day except the last minute, booking already cancelled
            claims(notice(kind, "CANCELLED", APPOINTMENT,
                    new ReminderPreferences(false, LocalTime.of(0, 0), LocalTime.of(23, 59), null)));

            // Act
            deliver();

            // Assert
            assertEquals(new EmailMessage("ana@example.com", subject, """
                    Código: BK-7F3A
                    Servicio: Corte de cabello
                    Sede: Sede Centro
                    Fecha: 2026-11-25 10:00 America/Bogota
                    Estado: Confirmada
                    """), storedMessage());
            verify(notices).markProcessed(NOTICE, NOW);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource({
                "RESERVED,  Pendiente",
                "CONFIRMED, Confirmada",
                "CANCELLED, Cancelada",
                "COMPLETED, Completada",
                "NO_SHOW,   Inasistencia",
                "ON_HOLD,   ON_HOLD"
        })
        @DisplayName("HU-015: the status at the time of the notice is shown in Spanish; an unknown status is shown as is")
        void statusIsTranslated(String status, String shown) {
            // Arrange
            claims(new Delivery(NOTICE, Kind.CREATED, RECIPIENT, "ana@example.com", "BK-7F3A", "Corte", "Sede",
                    APPOINTMENT, status, "America/Bogota", status, ReminderPreferences.DEFAULTS));

            // Act
            deliver();

            // Assert
            assertTrue(storedMessage().body().endsWith("Estado: " + shown + "\n"));
        }

        @Test
        @DisplayName("HU-015: without a location the notice says so instead of printing null")
        void missingLocationIsExplained() {
            // Arrange
            claims(new Delivery(NOTICE, Kind.CREATED, RECIPIENT, "ana@example.com", "BK-7F3A", "Corte", null,
                    APPOINTMENT, "RESERVED", "America/Bogota", "RESERVED", ReminderPreferences.DEFAULTS));

            // Act
            deliver();

            // Assert
            assertTrue(storedMessage().body().contains("Sede: Sin sede disponible\n"));
        }

        @Test
        @DisplayName("HU-015: the appointment time is written in the appointment's own time zone")
        void appointmentTimeUsesItsZone() {
            // Arrange
            claims(new Delivery(NOTICE, Kind.CREATED, RECIPIENT, "ana@example.com", "BK-7F3A", "Corte", "Sede",
                    APPOINTMENT, "RESERVED", "Europe/Madrid", "RESERVED", ReminderPreferences.DEFAULTS));

            // Act
            deliver();

            // Assert
            assertTrue(storedMessage().body().contains("Fecha: 2026-11-25 16:00 Europe/Madrid\n"));
        }
    }

    @Nested
    @DisplayName("Reminders (decision table)")
    class Reminders {

        @Test
        @DisplayName("HU-015: a reminder for a confirmed future appointment, outside quiet hours, is delivered")
        void reminderIsDelivered() {
            // Arrange
            claims(notice(Kind.REMINDER, "CONFIRMED", APPOINTMENT, NO_QUIET));

            // Act
            deliver();

            // Assert
            assertEquals("Recordatorio de reserva", storedMessage().subject());
            verify(notices).markProcessed(NOTICE, NOW);
        }

        @Test
        @DisplayName("HU-015: a recipient who turned reminders off gets none; the reminder is closed")
        void disabledRemindersAreDropped() {
            // Arrange
            claims(notice(Kind.REMINDER, "CONFIRMED", APPOINTMENT,
                    new ReminderPreferences(false, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, null)));

            // Act
            deliver();

            // Assert
            verifyNoInteractions(inbox);
            verify(notices).markProcessed(NOTICE, NOW);
        }

        @ParameterizedTest(name = "[{index}] current status {0}")
        @ValueSource(strings = {"RESERVED", "CANCELLED", "COMPLETED", "NO_SHOW"})
        @DisplayName("ADR-0010: reminders are only for bookings that are still confirmed")
        void reminderNeedsAConfirmedBooking(String status) {
            // Arrange
            claims(notice(Kind.REMINDER, status, APPOINTMENT, NO_QUIET));

            // Act
            deliver();

            // Assert
            verifyNoInteractions(inbox);
            verify(notices).markProcessed(NOTICE, NOW);
        }

        @ParameterizedTest(name = "[{index}] appointment at now{0} s -> delivered={1}")
        @CsvSource({"-1, false", "0, false", "1, true"})
        @DisplayName("HU-015 (BVA): a reminder for an appointment that already started is not sent")
        void reminderNeedsAFutureAppointment(long offset, boolean delivered) {
            // Arrange
            claims(notice(Kind.REMINDER, "CONFIRMED", NOW.plusSeconds(offset), NO_QUIET));

            // Act
            deliver();

            // Assert
            if (delivered) {
                storedMessage();
            } else {
                verifyNoInteractions(inbox);
            }
            verify(notices).markProcessed(NOTICE, NOW);
        }

        @Test
        @DisplayName("ADR-0010: in quiet hours that end before the appointment, the reminder is postponed to the end of the quiet period")
        void quietHoursPostponeTheReminder() {
            // Arrange: quiet 06:00-09:00 Bogota; now is 07:00 Bogota, so it ends at 14:00 UTC
            claims(notice(Kind.REMINDER, "CONFIRMED", APPOINTMENT,
                    new ReminderPreferences(true, LocalTime.of(6, 0), LocalTime.of(9, 0), null)));

            // Act
            deliver();

            // Assert
            verify(notices).defer(NOTICE, Instant.parse("2026-11-21T14:00:00Z"));
            verify(notices, never()).markProcessed(any(), any());
            verifyNoInteractions(inbox);
        }

        @ParameterizedTest(name = "[{index}] appointment at quiet end{0} s -> postponed={1}")
        @CsvSource({"1, true", "0, false", "-60, false"})
        @DisplayName("ADR-0010 (BVA): if the quiet period does not end before the appointment, the reminder is dropped")
        void quietHoursUntilTheAppointmentDropTheReminder(long offset, boolean postponed) {
            // Arrange: quiet until 14:00 UTC (09:00 Bogota)
            Instant quietEnd = Instant.parse("2026-11-21T14:00:00Z");
            claims(notice(Kind.REMINDER, "CONFIRMED", quietEnd.plusSeconds(offset),
                    new ReminderPreferences(true, LocalTime.of(6, 0), LocalTime.of(9, 0), null)));

            // Act
            deliver();

            // Assert
            verifyNoInteractions(inbox);
            if (postponed) {
                verify(notices).defer(NOTICE, quietEnd);
            } else {
                verify(notices, never()).defer(any(), any());
                verify(notices).markProcessed(NOTICE, NOW);
            }
        }
    }
}
