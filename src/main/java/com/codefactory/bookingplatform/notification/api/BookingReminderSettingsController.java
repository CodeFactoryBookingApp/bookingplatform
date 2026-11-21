package com.codefactory.bookingplatform.notification.api;

import com.codefactory.bookingplatform.notification.application.ManageBookingRemindersUseCase;
import com.codefactory.bookingplatform.notification.domain.model.ReminderPreferences;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;

@RestController
@Tag(name = "Booking notifications", description = "HU-015 - Booking reminders and recipient preferences")
public class BookingReminderSettingsController {

    private static final String PERSONAL = "/api/v1/notifications/booking-preferences";

    private final ManageBookingRemindersUseCase useCase;

    public BookingReminderSettingsController(ManageBookingRemindersUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping(PERSONAL)
    public ReminderPreferences preferences(JwtAuthenticationToken auth) {
        return useCase.preferences(AuthenticatedUser.from(auth).id());
    }

    @PutMapping(PERSONAL)
    public ReminderPreferences changePreferences(@Valid @RequestBody PreferencesRequest request,
                                                 JwtAuthenticationToken auth) {
        return useCase.changePreferences(AuthenticatedUser.from(auth).id(),
                new ReminderPreferences(request.remindersEnabled(), request.quietFrom(), request.quietUntil(),
                        request.timezone()));
    }

    public record PreferencesRequest(@NotNull Boolean remindersEnabled, @NotNull LocalTime quietFrom,
                                     @NotNull LocalTime quietUntil, @Size(max = 80) String timezone) {
    }

}
