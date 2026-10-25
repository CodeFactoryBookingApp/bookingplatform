package com.codefactory.bookingplatform.identity.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ListingParams;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-003 filters of the client listing. All optional, combined with AND. {@code city} is matched
 * without case or accents (it is free text in the client profile); registration dates are days in
 * Colombia, the platform's zone, both ends included.
 */
public record ClientFilter(ClientStatus status, String city, LocalDate registeredFrom, LocalDate registeredTo,
                           Sort sort, boolean descending, int page, int size) {

    /** The columns the listing may be sorted by (HU-003 "Ordenamiento por columna"). */
    public enum Sort { NAME, EMAIL, CITY, STATUS, REGISTERED }

    public ClientFilter {
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
