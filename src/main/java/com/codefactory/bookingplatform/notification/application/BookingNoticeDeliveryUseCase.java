package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Delivery;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Kind;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class BookingNoticeDeliveryUseCase {

    private static final DateTimeFormatter APPOINTMENT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV", Locale.ROOT);

    private final BookingNotices notices;
    private final EmailNotifications emails;
    private final Clock clock;

    public BookingNoticeDeliveryUseCase(BookingNotices notices, EmailNotifications emails, Clock clock) {
        this.notices = notices;
        this.emails = emails;
        this.clock = clock;
    }

    @Transactional
    public void deliver(Candidate candidate) {
        // Booking writers lock the booking before updating its notices. Use the same
        // order so a cancellation cannot race the delivery of a pending reminder.
        if (notices.lockBooking(candidate.bookingId())) {
            Instant now = clock.instant();
            notices.claim(candidate.id(), now).ifPresent(notice -> deliver(notice, now));
        }
    }

    private void deliver(Delivery notice, Instant now) {
        if (notice.recipientId() == null) {
            notices.markProcessed(notice.id(), now);
            return;
        }
        if (notice.kind() == Kind.REMINDER) {
            if (!notice.preferences().remindersEnabled()
                    || !"CONFIRMED".equals(notice.currentStatus()) || !notice.appointmentAt().isAfter(now)) {
                notices.markProcessed(notice.id(), now);
                return;
            }
            var quietEnd = notice.preferences().quietUntil(now, notice.appointmentTimezone());
            if (quietEnd.isPresent()) {
                if (quietEnd.get().isBefore(notice.appointmentAt())) {
                    notices.defer(notice.id(), quietEnd.get());
                } else {
                    notices.markProcessed(notice.id(), now);
                }
                return;
            }
        }
        emails.storeBookingNotice(notice.id(), notice.recipientId(), notice.recipientEmail(), subject(notice.kind()),
                body(notice), now);
        notices.markProcessed(notice.id(), now);
    }

    private static String subject(Kind kind) {
        return switch (kind) {
            case CREATED -> "Reserva creada";
            case CONFIRMED -> "Reserva confirmada";
            case CANCELLED -> "Reserva cancelada";
            case RESCHEDULED -> "Reserva reprogramada";
            case REMINDER -> "Recordatorio de reserva";
        };
    }

    private static String body(Delivery notice) {
        String appointment = APPOINTMENT.format(notice.appointmentAt().atZone(ZoneId.of(notice.appointmentTimezone())));
        String status = switch (notice.appointmentStatus()) {
            case "RESERVED" -> "Pendiente";
            case "CONFIRMED" -> "Confirmada";
            case "CANCELLED" -> "Cancelada";
            case "COMPLETED" -> "Completada";
            case "NO_SHOW" -> "Inasistencia";
            default -> notice.appointmentStatus();
        };
        return """
                Código: %s
                Servicio: %s
                Sede: %s
                Fecha: %s
                Estado: %s
                """.formatted(notice.code(), notice.serviceName(),
                notice.locationName() == null ? "Sin sede disponible" : notice.locationName(), appointment, status);
    }
}
