package com.codefactory.bookingplatform.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * HU-012: the status a new booking starts in and the terms token the client must have reviewed.
 *
 * <p>Initial status: decision table over the provider's manual confirmation and the client's
 * recent no-shows at that provider, with boundary values 3 (still automatic) and 4 (needs approval).
 * Terms token: one change at a time over each reviewed term (time, duration, price, currency,
 * policy) must invalidate the preview.
 */
class BookingOfferingTest {

    private static final UUID SERVICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID POLICY = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant START = Instant.parse("2026-11-26T15:00:00Z");

    private static BookingOffering offering(boolean manual, int duration, String price, String currency, UUID policy) {
        return new BookingOffering(SERVICE, "Corte", LOCATION, "Sede", UUID.randomUUID(), "Barbería",
                ZoneId.of("America/Bogota"), new BigDecimal(price), currency, duration, 0, 0, 1, policy, 1, 60,
                60, 43200, manual);
    }

    private static BookingOffering base() {
        return offering(false, 30, "45000.00", "COP", POLICY);
    }

    @ParameterizedTest(name = "manual={0}, recent no-shows={1} -> {2}")
    @CsvSource({
            "false, 0, CONFIRMED",
            "false, 3, CONFIRMED",
            "false, 4, RESERVED",
            "false, 5, RESERVED",
            "true, 0, RESERVED",
            "true, 4, RESERVED"
    })
    @DisplayName("HU-012: a booking is confirmed at once unless the provider approves manually or the client has more than 3 recent no-shows")
    void initialStatusDecisionTable(boolean manual, int recentNoShows, BookingStatus expected) {
        // Arrange
        BookingOffering offer = offering(manual, 30, "45000.00", "COP", POLICY);

        // Act
        BookingStatus status = offer.initialStatus(recentNoShows);

        // Assert
        assertEquals(expected, status);
    }

    @Test
    @DisplayName("HU-012: the same offer at the same time always yields the same terms token")
    void sameTermsSameToken() {
        // Act
        String token = base().termsVersion(START);

        // Assert
        assertEquals(base().termsVersion(START), token);
    }

    @ParameterizedTest(name = "other {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"service", "location"})
    @DisplayName("HU-012: the token is bound to the service and the location that were previewed")
    void tokenIsBoundToServiceAndLocation(String which) {
        // Arrange
        BookingOffering base = base();
        BookingOffering other = new BookingOffering("service".equals(which) ? UUID.randomUUID() : SERVICE, "Corte",
                "location".equals(which) ? UUID.randomUUID() : LOCATION, "Sede", base.organizationId(), "Barbería",
                base.zone(), base.price(), "COP", 30, 0, 0, 1, POLICY, 1, 60, 60, 43200, false);

        // Act
        String token = other.termsVersion(START);

        // Assert
        assertNotEquals(base.termsVersion(START), token);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "start time, 30, 45000.00, COP, false",
            "duration, 45, 45000.00, COP, false",
            "price, 30, 45001.00, COP, false",
            "currency, 30, 45000.00, USD, false",
            "policy, 30, 45000.00, COP, true"
    })
    @DisplayName("HU-012: changing any reviewed term (time, duration, price, currency, policy) invalidates the preview")
    void anyReviewedTermChangesTheToken(String term, int duration, String price, String currency, boolean newPolicy) {
        // Arrange
        Instant start = "start time".equals(term) ? START.plusSeconds(1800) : START;
        BookingOffering changed = offering(false, duration, price, currency, newPolicy ? UUID.randomUUID() : POLICY);

        // Act
        String token = changed.termsVersion(start);

        // Assert
        assertNotEquals(base().termsVersion(START), token);
    }
}
