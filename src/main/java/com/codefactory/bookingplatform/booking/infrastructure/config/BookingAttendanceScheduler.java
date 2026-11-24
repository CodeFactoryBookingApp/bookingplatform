package com.codefactory.bookingplatform.booking.infrastructure.config;

import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.booking-attendance.scheduler-enabled", matchIfMissing = true)
public class BookingAttendanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingAttendanceScheduler.class);

    private final BookAppointmentUseCase bookings;

    public BookingAttendanceScheduler(BookAppointmentUseCase bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${app.booking-attendance.poll-ms:60000}")
    void closeOverdue() {
        try {
            bookings.autoCloseUnmarked();
        } catch (RuntimeException ex) {
            log.error("Could not auto-close overdue unmarked bookings", ex);
        }
    }
}
