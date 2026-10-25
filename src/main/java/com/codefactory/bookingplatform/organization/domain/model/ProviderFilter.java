package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ListingParams;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HU-003 filters of the provider listing. All optional, combined with AND. {@code city} is a city
 * name matched without case or accents against the cities of the provider's locations;
 * registration dates are days in Colombia, the platform's zone, both ends included.
 */
public record ProviderFilter(OrganizationStatus status, UUID categoryId, String city, LocalDate registeredFrom,
                             LocalDate registeredTo, Sort sort, boolean descending, int page, int size) {

    /** The columns the listing may be sorted by (HU-003 "Ordenamiento por columna"). */
    public enum Sort { NAME, STATUS, CATEGORY, REGISTERED }

    public ProviderFilter {
        Map<String, String> errors = new LinkedHashMap<>();
        ListingParams.checkRange(registeredFrom, registeredTo, "registeredTo", errors);
        ListingParams.checkWindow(page, size, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid listing parameters", errors);
        }
        city = city == null || city.isBlank() ? null : city.trim();
        sort = sort == null ? Sort.REGISTERED : sort;
    }
}
