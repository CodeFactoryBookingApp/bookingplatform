package com.codefactory.bookingplatform.notification.infrastructure.config;

import com.codefactory.bookingplatform.notification.application.BookingNoticeDeliveryUseCase;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.booking-notices.scheduler-enabled", matchIfMissing = true)
public class BookingNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingNoticeScheduler.class);
    private static final int BATCH_SIZE = 100;

    private final BookingNotices notices;
    private final BookingNoticeDeliveryUseCase delivery;
    private final Clock clock;

    public BookingNoticeScheduler(BookingNotices notices, BookingNoticeDeliveryUseCase delivery, Clock clock) {
        this.notices = notices;
        this.delivery = delivery;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.booking-notices.poll-ms:15000}")
    void dispatch() {
        for (Candidate candidate : notices.due(clock.instant(), BATCH_SIZE)) {
            try {
                delivery.deliver(candidate);
            } catch (RuntimeException ex) {
                log.error("Could not deliver booking notice {}", candidate.id(), ex);
            }
        }
    }
}
