package com.codefactory.bookingplatform.booking.api;

import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.Preview;
import com.codefactory.bookingplatform.booking.application.BookAppointmentUseCase.TimeSlot;
import com.codefactory.bookingplatform.booking.domain.port.BookingReservations.Receipt;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt create(@Valid @RequestBody BookingRequest request, JwtAuthenticationToken token) {
        return bookings.create(AuthenticatedUser.from(token).id(), request.serviceId(), request.locationId(),
                request.startsAt(), request.termsVersion());
    }

    @PostMapping("/organizations/{organizationId}/bookings/{bookingId}/confirm")
    public Receipt confirm(@PathVariable UUID organizationId, @PathVariable UUID bookingId, JwtAuthenticationToken token) {
        return bookings.confirm(organizationId, bookingId, AuthenticatedUser.from(token).id());
    }
}
