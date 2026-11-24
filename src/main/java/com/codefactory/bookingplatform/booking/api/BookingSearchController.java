package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.application.SearchBookingsUseCase;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Filter;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Page;
import com.codefactory.bookingplatform.booking.domain.model.BookingSearch.Row;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.shared.export.Csv;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/bookings")
@Tag(name = "Booking search", description = "HU-018 - Scoped booking search, summary and export")
public class BookingSearchController {

    private final SearchBookingsUseCase search;

    public BookingSearchController(SearchBookingsUseCase search) {
        this.search = search;
    }

    @GetMapping
    @Operation(summary = "Search bookings", description = "Filters combine. The start time range includes from and excludes to.")
    public Page page(@PathVariable UUID organizationId, @ParameterObject FilterRequest filters,
                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                     JwtAuthenticationToken auth) {
        return search.search(organizationId, AuthenticatedUser.from(auth).id(), filters.toFilter(), page, size);
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Find a booking by its exact code")
    public Row byCode(@PathVariable UUID organizationId, @PathVariable String code, JwtAuthenticationToken auth) {
        return search.byCode(organizationId, AuthenticatedUser.from(auth).id(), code);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export all bookings matching the same search filters")
    public ResponseEntity<String> export(@PathVariable UUID organizationId, @ParameterObject FilterRequest filters,
                                         JwtAuthenticationToken auth) {
        var rows = search.export(organizationId, AuthenticatedUser.from(auth).id(), filters.toFilter()).stream()
                .map(BookingSearchController::csvRow).toList();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Csv.MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reservas.csv\"")
                .body(Csv.of(List.of("codigo", "cliente", "servicio", "sede", "inicio", "fin", "asistentes",
                        "estado", "importe", "moneda"), rows));
    }

    private static List<String> csvRow(Row booking) {
        return List.of(booking.code(), booking.clientName(), booking.serviceName(), booking.locationName(),
                booking.startsAt().toString(), booking.endsAt().toString(), Integer.toString(booking.attendees()),
                booking.status().name(), booking.totalPrice().toPlainString(), booking.currency());
    }

    public record FilterRequest(Instant from, Instant to, BookingStatus status, UUID serviceId, UUID locationId,
                                UUID resourceId, UUID clientId, String code) {
        Filter toFilter() {
            return new Filter(from, to, status, serviceId, locationId, resourceId, clientId, code);
        }
    }
}
