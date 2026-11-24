package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingDetail;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.StatusChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingReceiptPdfTest {

    @Test
    void rendersReadablePdfWithHeaderXrefBookingDetailsAndEscapedCharacters() {
        // Arrange
        UUID bookingId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID orgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID serviceId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID locationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Instant start = Instant.parse("2026-11-25T15:00:00Z");
        Instant end = Instant.parse("2026-11-25T15:30:00Z");
        var detail = new ClientBookingDetail(
                bookingId,
                "RES-001",
                "Ana (Cliente) \\ Test",
                "ana@example.com",
                orgId,
                "Barbería El Poblado",
                serviceId,
                "Corte de cabello",
                locationId,
                "Sede Principal",
                "Cra 43A # 5-15",
                ZoneId.of("America/Bogota"),
                start,
                end,
                1,
                new BigDecimal("45000.00"),
                "COP",
                BookingStatus.CONFIRMED,
                60,
                1,
                0,
                false,
                false,
                List.of("Silla 1"),
                List.of(new StatusChange(null, BookingStatus.CONFIRMED, "Confirmación inicial", start.minusSeconds(3600)))
        );

        // Act
        byte[] pdf = BookingReceiptPdf.render(detail);
        String content = new String(pdf, StandardCharsets.ISO_8859_1);

        // Assert
        assertThat(content)
                .startsWith("%PDF-1.4\n")
                .contains("COMPROBANTE DE RESERVA")
                .contains("Codigo de reserva: RES-001")
                .contains("Confirmada \\(CONFIRMED\\)")
                .contains("Ana \\(Cliente\\) \\\\ Test")
                .contains("Corte de cabello")
                .contains("Sede Principal")
                .contains("45000.00 COP")
                .contains("Silla 1")
                .contains("xref\n")
                .endsWith("%%EOF\n");
    }
}
