package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingDetail;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.StatusChange;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BookingReceiptPdf {

    public static final String MEDIA_TYPE = "application/pdf";

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV", Locale.ROOT);

    private BookingReceiptPdf() {
    }

    public static byte[] render(ClientBookingDetail detail) {
        List<String> lines = new ArrayList<>();
        lines.add("COMPROBANTE DE RESERVA");
        lines.add("------------------------------------------------------------");
        lines.add("Codigo de reserva: " + detail.code());
        lines.add("ID de reserva: " + detail.id());
        lines.add("Estado actual: " + statusLabel(detail.status().name()));
        lines.add("Cliente: " + detail.clientName() + " (" + detail.clientEmail() + ")");
        lines.add("Proveedor: " + detail.organizationName());
        lines.add("Servicio: " + detail.serviceName());
        lines.add("Sede: " + detail.locationName());
        lines.add("Direccion: " + detail.locationAddress());
        lines.add("Inicio: " + DATE_TIME.format(detail.startsAt().atZone(detail.zone())));
        lines.add("Fin: " + DATE_TIME.format(detail.endsAt().atZone(detail.zone())));
        lines.add("Asistentes: " + detail.attendees());
        lines.add("Importe total: " + detail.totalPrice().toPlainString() + " " + detail.currency());
        lines.add("Recursos asignados: " + (detail.resources().isEmpty() ? "N/A" : String.join(", ", detail.resources())));
        lines.add("Ventana sin penalidad (min): " + detail.freeCancellationWindowMinutes());
        lines.add("Reprogramaciones: " + detail.rescheduleCount() + " / " + detail.maxReschedules());
        lines.add("------------------------------------------------------------");
        lines.add("Historial de estados:");
        for (StatusChange change : detail.statusHistory()) {
            String from = change.fromStatus() == null ? "INICIO" : statusLabel(change.fromStatus().name());
            String to = statusLabel(change.toStatus().name());
            String at = DATE_TIME.format(change.changedAt().atZone(detail.zone()));
            String reason = change.reason() == null || change.reason().isBlank() ? "" : " - " + change.reason();
            lines.add(" * " + at + ": " + from + " -> " + to + reason);
        }
        return buildPdf(lines);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "RESERVED" -> "Pendiente (RESERVED)";
            case "CONFIRMED" -> "Confirmada (CONFIRMED)";
            case "CANCELLED" -> "Cancelada (CANCELLED)";
            case "COMPLETED" -> "Completada (COMPLETED)";
            case "NO_SHOW" -> "Inasistencia (NO_SHOW)";
            default -> status;
        };
    }

    private static byte[] buildPdf(List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 11 Tf\n50 750 Td\n15 TL\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                stream.append("T*\n");
            }
            stream.append('(').append(escapePdfText(lines.get(i))).append(") Tj\n");
        }
        stream.append("ET\n");
        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.ISO_8859_1);

        List<byte[]> objects = List.of(
                ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"),
                ascii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"),
                ascii("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n"),
                concat(ascii("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n"),
                        streamBytes,
                        ascii("endstream\nendobj\n")),
                ascii("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ascii("%PDF-1.4\n"));
        int[] offsets = new int[objects.size()];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i] = out.size();
            write(out, objects.get(i));
        }
        int xrefOffset = out.size();
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(objects.size() + 1).append('\n');
        xref.append("0000000000 65535 f \n");
        for (int offset : offsets) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        xref.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        write(out, ascii(xref.toString()));
        return out.toByteArray();
    }

    static String escapePdfText(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            char normalized = ch <= 255 ? ch : '?';
            if (normalized == '\\' || normalized == '(' || normalized == ')') {
                escaped.append('\\');
            }
            escaped.append(normalized);
        }
        return escaped.toString();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[] first, byte[] second, byte[] third) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(first.length + second.length + third.length);
        write(out, first);
        write(out, second);
        write(out, third);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
