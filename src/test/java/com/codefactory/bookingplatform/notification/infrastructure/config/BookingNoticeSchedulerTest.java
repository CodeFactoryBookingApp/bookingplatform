package com.codefactory.bookingplatform.notification.infrastructure.config;

import com.codefactory.bookingplatform.notification.application.BookingNoticeDeliveryUseCase;
import com.codefactory.bookingplatform.notification.domain.model.BookingNotice.Candidate;
import com.codefactory.bookingplatform.notification.domain.port.BookingNotices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-0010: each polling round takes up to 100 due notices at the clock time and delivers them one
 * by one; a notice that fails does not stop the rest of the batch (it stays queued for the next round).
 */
class BookingNoticeSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    @DisplayName("ADR-0010: a failing notice does not stop the delivery of the others in the batch")
    void failingNoticeDoesNotStopTheBatch() {
        // Arrange
        BookingNotices notices = mock(BookingNotices.class);
        BookingNoticeDeliveryUseCase delivery = mock(BookingNoticeDeliveryUseCase.class);
        Candidate failing = new Candidate(UUID.randomUUID(), UUID.randomUUID());
        Candidate next = new Candidate(UUID.randomUUID(), UUID.randomUUID());
        when(notices.due(NOW, 100)).thenReturn(List.of(failing, next));
        doThrow(new IllegalStateException("database hiccup")).when(delivery).deliver(failing);

        // Act
        new BookingNoticeScheduler(notices, delivery, Clock.fixed(NOW, ZoneOffset.UTC)).dispatch();

        // Assert
        verify(delivery).deliver(failing);
        verify(delivery).deliver(next);
    }
}
