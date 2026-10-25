package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;

import java.util.List;
import java.util.UUID;

/**
 * HU-002 registration: the account (email, password), the business and its operating rules.
 * {@code contactEmail} and {@code timezone} are optional; they default to the account email and
 * America/Bogota.
 */
public record RegisterProviderCommand(
        String email,
        String password,
        String name,
        UUID categoryId,
        IdentificationType identificationType,
        String identificationNumber,
        String contactEmail,
        String contactPhone,
        String timezone,
        List<LocationData> locations,
        int bookingNoticeMinutesMin,
        int bookingNoticeMinutesMax,
        int freeCancellationWindowMinutes) {

    public record LocationData(String name, String address, UUID cityId, String timezone) {
    }
}
