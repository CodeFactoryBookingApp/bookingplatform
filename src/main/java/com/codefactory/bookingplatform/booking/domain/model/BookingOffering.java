package com.codefactory.bookingplatform.booking.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Current offer shown to a client before a single-seat reservation. */
public record BookingOffering(UUID serviceId, String service, UUID locationId, String location,
                              UUID organizationId, String organization, ZoneId zone, BigDecimal price,
                              String currency, int duration, int preparation, int cleanup, int capacity,
                              UUID policyId, int policyVersion, int freeCancellationWindowMinutes,
                              int minNotice, int maxNotice, boolean manualConfirmation) {

    /** Opaque equality token for the exact time, price, currency and policy reviewed by the client. */
    public String termsVersion(Instant start) {
        String terms = String.join("/", serviceId.toString(), locationId.toString(), start.toString(),
                Integer.toString(duration), price.toPlainString(), currency, policyId.toString());
        return UUID.nameUUIDFromBytes(terms.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public BookingStatus initialStatus(int recentNoShows) {
        return manualConfirmation || recentNoShows > 3 ? BookingStatus.RESERVED : BookingStatus.CONFIRMED;
    }
}
