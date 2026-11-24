package com.codefactory.bookingplatform.booking.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServiceDemand {

    private ServiceDemand() {
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Window(Instant from, Instant to) {
    }

    public record Group(UUID serviceId, String serviceName, BookingStatus status, String currency,
                        long count, BigDecimal amount) {
    }

    public record Bucket(int dayOfWeek, int hour, long bookings) {
    }

    public record Metrics(long created, long completed, long cancelled, long noShow, BigDecimal completionRatePercent,
                          BigDecimal cancellationRatePercent, BigDecimal noShowRatePercent,
                          Map<String, BigDecimal> estimatedRevenueByCurrency) {
    }

    public record Variation(BigDecimal createdPercent, BigDecimal completedPercent, BigDecimal cancelledPercent,
                            BigDecimal noShowPercent, BigDecimal completionRatePercent,
                            BigDecimal cancellationRatePercent, BigDecimal noShowRatePercent,
                            Map<String, BigDecimal> estimatedRevenuePercent) {
    }

    public record Service(UUID id, String name, Metrics current, Metrics previous, Variation variation,
                          boolean highCancellation) {
    }

    public record Report(Period period, Period previousPeriod, String timezone, List<Service> services,
                         List<Bucket> demand, List<Bucket> previousDemand) {
    }
}
