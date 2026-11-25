package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingDetail;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.StatusChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HU-017 receipt: a one-page PDF a viewer can open, with the booking data in the provider's time
 * zone, the status history and text escaped so that it cannot break the content stream.
 *
 * <p>Equivalence partitioning over the text (PDF delimiters, Latin-1, beyond Latin-1), over the
 * status labels and over optional data (resources, initial status, reason).
 */
class BookingReceiptPdfTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant START = Instant.parse("2026-11-25T15:00:00Z");

    private static ClientBookingDetail detail(String clientName, BookingStatus status, List<String> resources,
                                              List<StatusChange> history) {
        return new ClientBookingDetail(UUID.fromString("11111111-1111-1111-1111-111111111111"), "RES-001", clientName,
                "ana@example.com", UUID.randomUUID(), "Barberia", UUID.randomUUID(), "Corte", UUID.randomUUID(),
                "Sede", "Calle 1", BOGOTA, START, START.plusSeconds(1800), 1, new BigDecimal("45000.00"), "COP",
                status, 60, 2, 1, false, false, resources, history);
    }

    private static String text(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

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

    @Test
    @DisplayName("HU-017: the cross-reference table points at each of the five objects and the stream length is exact")
    void crossReferenceAndStreamLengthAreExact() {
        // Arrange
        ClientBookingDetail booking = detail("Ana (Cliente)", BookingStatus.CONFIRMED, List.of("Silla 1"),
                List.of(new StatusChange(null, BookingStatus.CONFIRMED, "Confirmación inicial", START.minusSeconds(3600))));

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        Matcher entries = Pattern.compile("(\\d{10}) 00000 n ").matcher(pdf);
        int object = 1;
        while (entries.find()) {
            int offset = Integer.parseInt(entries.group(1));
            assertThat(pdf.substring(offset)).startsWith(object + " 0 obj\n");
            object++;
        }
        assertEquals(6, object);
        int startxref = Integer.parseInt(pdf.substring(pdf.lastIndexOf("startxref\n") + 10, pdf.lastIndexOf("\n%%EOF")));
        assertThat(pdf.substring(startxref)).startsWith("xref\n0 6\n0000000000 65535 f \n");
        Matcher length = Pattern.compile("/Length (\\d+) >>\nstream\n").matcher(pdf);
        assertThat(length.find()).isTrue();
        int streamStart = length.end();
        assertEquals(Integer.parseInt(length.group(1)), pdf.indexOf("endstream", streamStart) - streamStart);
    }

    @Test
    @DisplayName("HU-017: the content stream sets the font once and writes every receipt line, one per text line, in order")
    void contentStreamWritesEveryLineInOrder() {
        // Arrange
        ClientBookingDetail booking = detail("Ana", BookingStatus.CONFIRMED, List.of("Silla 1"), List.of());

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        String stream = pdf.substring(pdf.indexOf("stream\n") + 7, pdf.indexOf("endstream"));
        String separator = "------------------------------------------------------------";
        assertEquals("BT\n/F1 11 Tf\n50 750 Td\n15 TL\n"
                + "(COMPROBANTE DE RESERVA) Tj\n"
                + "T*\n(" + separator + ") Tj\n"
                + "T*\n(Codigo de reserva: RES-001) Tj\n"
                + "T*\n(ID de reserva: 11111111-1111-1111-1111-111111111111) Tj\n"
                + "T*\n(Estado actual: Confirmada \\(CONFIRMED\\)) Tj\n"
                + "T*\n(Cliente: Ana \\(ana@example.com\\)) Tj\n"
                + "T*\n(Proveedor: Barberia) Tj\n"
                + "T*\n(Servicio: Corte) Tj\n"
                + "T*\n(Sede: Sede) Tj\n"
                + "T*\n(Direccion: Calle 1) Tj\n"
                + "T*\n(Inicio: 2026-11-25 10:00 America/Bogota) Tj\n"
                + "T*\n(Fin: 2026-11-25 10:30 America/Bogota) Tj\n"
                + "T*\n(Asistentes: 1) Tj\n"
                + "T*\n(Importe total: 45000.00 COP) Tj\n"
                + "T*\n(Recursos asignados: Silla 1) Tj\n"
                + "T*\n(Ventana sin penalidad \\(min\\): 60) Tj\n"
                + "T*\n(Reprogramaciones: 1 / 2) Tj\n"
                + "T*\n(" + separator + ") Tj\n"
                + "T*\n(Historial de estados:) Tj\n"
                + "ET\n", stream);
        assertThat(pdf).contains("trailer\n<< /Size 6 /Root 1 0 R >>\n");
    }

    @Test
    @DisplayName("HU-017: start, end and status changes are written in the provider's time zone")
    void timesInTheProvidersZone() {
        // Arrange
        ClientBookingDetail booking = detail("Ana", BookingStatus.CONFIRMED, List.of(),
                List.of(new StatusChange(null, BookingStatus.RESERVED, null, Instant.parse("2026-11-20T02:30:00Z"))));

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        assertThat(pdf).contains("(Inicio: 2026-11-25 10:00 America/Bogota) Tj")
                .contains("(Fin: 2026-11-25 10:30 America/Bogota) Tj")
                .contains("( * 2026-11-19 21:30 America/Bogota: INICIO -> Pendiente \\(RESERVED\\)) Tj");
    }

    @Test
    @DisplayName("HU-017: each status change shows from, to and its reason; a blank reason adds nothing")
    void statusHistoryLines() {
        // Arrange
        ClientBookingDetail booking = detail("Ana", BookingStatus.CANCELLED, List.of("Silla 1", "Lavacabezas"), List.of(
                new StatusChange(BookingStatus.RESERVED, BookingStatus.CONFIRMED, "  ", START.minusSeconds(7200)),
                new StatusChange(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, "Viaje", START.minusSeconds(3600))));

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        assertThat(pdf)
                .contains("( * 2026-11-25 08:00 America/Bogota: Pendiente \\(RESERVED\\) -> Confirmada \\(CONFIRMED\\)) Tj")
                .contains("( * 2026-11-25 09:00 America/Bogota: Confirmada \\(CONFIRMED\\) -> Cancelada \\(CANCELLED\\) - Viaje) Tj")
                .contains("(Recursos asignados: Silla 1, Lavacabezas) Tj")
                .contains("(Reprogramaciones: 1 / 2) Tj");
    }

    @Test
    @DisplayName("HU-017: a booking without assigned resources says N/A")
    void noResourcesIsNotApplicable() {
        // Arrange
        ClientBookingDetail booking = detail("Ana", BookingStatus.RESERVED, List.of(), List.of());

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        assertThat(pdf).contains("(Recursos asignados: N/A) Tj");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "RESERVED, Pendiente \\(RESERVED\\)",
            "CONFIRMED, Confirmada \\(CONFIRMED\\)",
            "CANCELLED, Cancelada \\(CANCELLED\\)",
            "COMPLETED, Completada \\(COMPLETED\\)",
            "NO_SHOW, Inasistencia \\(NO_SHOW\\)"
    })
    @DisplayName("HU-017: the current status is shown with its Spanish label and its code")
    void statusLabels(BookingStatus status, String label) {
        // Arrange
        ClientBookingDetail booking = detail("Ana", status, List.of(), List.of());

        // Act
        String pdf = text(BookingReceiptPdf.render(booking));

        // Assert
        assertThat(pdf).contains("(Estado actual: " + label + ") Tj");
    }

    @Test
    @DisplayName("HU-017: Latin-1 letters are kept and characters the PDF font cannot encode become '?'")
    void charactersOutsideLatin1AreReplaced() {
        // Act
        String escaped = BookingReceiptPdf.escapePdfText("Zoë Łukasz € ÿĀ");

        // Assert
        assertEquals("Zoë ?ukasz ? ÿ?", escaped);
    }
}
