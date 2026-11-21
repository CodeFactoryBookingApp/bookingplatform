package com.codefactory.bookingplatform.booking.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BookingSearch {

    private BookingSearch() {
    }

    public record Filter(Instant from, Instant to, BookingStatus status, UUID serviceId,
                         UUID locationId, UUID resourceId, UUID clientId, String code) {

        public static Filter byCode(String code) {
            return new Filter(null, null, null, null, null, null, null, code);
        }
    }

    public record Row(UUID id, String code, UUID clientId, String clientName, UUID serviceId, String serviceName,
                      UUID locationId, String locationName, Instant startsAt, Instant endsAt, int attendees,
                      BigDecimal totalPrice, String currency, BookingStatus status) {
    }

    public record Summary(long total, Map<BookingStatus, Long> byStatus, Map<String, BigDecimal> amountByCurrency,
                          BigDecimal cancellationRatePercent) {
    }

    public record Page(List<Row> items, Summary summary, int page, int size) {
    }
}
