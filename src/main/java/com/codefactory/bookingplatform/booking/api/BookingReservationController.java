package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.ClientBookingsView;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.Preview;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.RebookView;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.TimeSlot;
import com.codefactory.bookingplatform.booking.domain.model.BookingStatus;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.AttendanceReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.CancellationReceipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.ClientBookingDetail;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Receipt;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.RescheduleReceipt;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BookingReservationController {
    public record BookingRequest(@NotNull UUID serviceId, @NotNull UUID locationId, @NotNull Instant startsAt,
                                 @NotBlank String termsVersion) {}
    public record ClientCancellationRequest(Boolean confirm, @Size(max = 300) String reason) {
        public boolean confirmed() {
            return Boolean.TRUE.equals(confirm);
        }
    }
    public record ProviderCancellationRequest(@NotBlank @Size(max = 300) String reason) {}
    public record ClientRescheduleRequest(@NotNull Instant startsAt, UUID locationId, UUID resourceId, UUID serviceId) {}
    public record AttendanceRequest(@NotNull BookingStatus status, @Size(max = 300) String observation) {}

    private final BookAppointmentUseCase bookings;

    public BookingReservationController(BookAppointmentUseCase bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/booking/availability")
    public List<TimeSlot> availability(@RequestParam UUID serviceId, @RequestParam UUID locationId,
                                       @RequestParam LocalDate date) {
        return bookings.availability(serviceId, locationId, date);
    }

    @GetMapping("/booking/preview")
    public Preview preview(@RequestParam UUID serviceId, @RequestParam UUID locationId, @RequestParam Instant startsAt) {
        return bookings.preview(serviceId, locationId, startsAt);
    }

    @GetMapping("/bookings")
    public ClientBookingsView clientBookings(JwtAuthenticationToken token) {
        return bookings.clientBookings(AuthenticatedUser.from(token).id());
    }

    @GetMapping("/bookings/{bookingId}")
    public ClientBookingDetail clientBookingDetail(@PathVariable UUID bookingId, JwtAuthenticationToken token) {
        return bookings.clientBookingDetail(AuthenticatedUser.from(token).id(), bookingId);
    }

    @GetMapping("/bookings/{bookingId}/rebook")
    public RebookView rebook(@PathVariable UUID bookingId,
                             @RequestParam(required = false) LocalDate date,
                             JwtAuthenticationToken token) {
        return bookings.rebook(AuthenticatedUser.from(token).id(), bookingId, date);
    }

    @GetMapping(value = "/bookings/{bookingId}/receipt", produces = BookingReceiptPdf.MEDIA_TYPE)
    public ResponseEntity<byte[]> receipt(@PathVariable UUID bookingId, JwtAuthenticationToken token) {
        ClientBookingDetail detail = bookings.clientBookingDetail(AuthenticatedUser.from(token).id(), bookingId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprobante-" + detail.code() + ".pdf\"")
                .body(BookingReceiptPdf.render(detail));
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt create(@Valid @RequestBody BookingRequest request, JwtAuthenticationToken token) {
        return bookings.create(AuthenticatedUser.from(token).id(), request.serviceId(), request.locationId(),
                request.startsAt(), request.termsVersion());
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public CancellationReceipt cancelByClient(@PathVariable UUID bookingId,
                                              @Valid @RequestBody(required = false) ClientCancellationRequest request,
                                              JwtAuthenticationToken token) {
        boolean confirmed = request != null && request.confirmed();
        String reason = request == null ? null : request.reason();
        return bookings.cancelByClient(AuthenticatedUser.from(token).id(), bookingId, confirmed, reason);
    }

    @PostMapping("/bookings/{bookingId}/reactivate")
    public void reactivateByClient(@PathVariable UUID bookingId, JwtAuthenticationToken token) {
        bookings.reactivateByClient(AuthenticatedUser.from(token).id(), bookingId);
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    public RescheduleReceipt rescheduleByClient(@PathVariable UUID bookingId,
                                                @Valid @RequestBody ClientRescheduleRequest request,
                                                JwtAuthenticationToken token) {
        return bookings.rescheduleByClient(AuthenticatedUser.from(token).id(), bookingId, request.startsAt(),
                request.locationId(), request.resourceId(), request.serviceId());
    }

    @PostMapping("/organizations/{organizationId}/bookings/{bookingId}/confirm")
    public Receipt confirm(@PathVariable UUID organizationId, @PathVariable UUID bookingId, JwtAuthenticationToken token) {
        return bookings.confirm(organizationId, bookingId, AuthenticatedUser.from(token).id());
    }

    @PostMapping("/organizations/{organizationId}/bookings/{bookingId}/cancel")
    public CancellationReceipt cancelByProvider(@PathVariable UUID organizationId, @PathVariable UUID bookingId,
                                                @Valid @RequestBody ProviderCancellationRequest request,
                                                JwtAuthenticationToken token) {
        return bookings.cancelByProvider(organizationId, bookingId, AuthenticatedUser.from(token).id(), request.reason());
    }

    @PostMapping("/organizations/{organizationId}/bookings/{bookingId}/attendance")
    public AttendanceReceipt recordAttendance(@PathVariable UUID organizationId, @PathVariable UUID bookingId,
                                              @Valid @RequestBody AttendanceRequest request,
                                              JwtAuthenticationToken token) {
        return bookings.recordAttendance(organizationId, bookingId, AuthenticatedUser.from(token).id(),
                request.status(), request.observation());
    }
}
