package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.application.ConfigureBookingRemindersUseCase;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/booking-reminders")
@Tag(name = "Organization reminders", description = "HU-015 - Configure booking reminder lead times")
public class OrganizationReminderController {

    private final ConfigureBookingRemindersUseCase useCase;

    public OrganizationReminderController(ConfigureBookingRemindersUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public LeadTimes settings(@PathVariable UUID organizationId, JwtAuthenticationToken auth) {
        return new LeadTimes(useCase.get(organizationId, AuthenticatedUser.from(auth).id()));
    }

    @PutMapping
    public LeadTimes change(@PathVariable UUID organizationId, @Valid @RequestBody LeadTimes request,
                            JwtAuthenticationToken auth) {
        return new LeadTimes(useCase.change(organizationId, AuthenticatedUser.from(auth).id(), request.leadMinutes()));
    }

    public record LeadTimes(@NotNull @Size(max = 10) List<@NotNull @Min(1) @Max(10080) Integer> leadMinutes) {
    }
}
