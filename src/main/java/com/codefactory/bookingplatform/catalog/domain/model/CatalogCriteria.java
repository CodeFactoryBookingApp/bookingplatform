package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import com.codefactory.bookingplatform.shared.listing.ListingParams;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HU-005 search of the catalog. Every filter is optional and they combine with AND. The keyword
 * matches the name or description of the service, or the name of the provider, ignoring case and
 * accents.
 */
public record CatalogCriteria(String keyword, UUID categoryId, UUID cityId, UUID providerId, BigDecimal minPrice,
                              BigDecimal maxPrice, Sort sort, boolean descending, int page, int size) {

    /** The columns a client may sort by. */
    public enum Sort { NAME, PRICE, DURATION, PROVIDER }

    public CatalogCriteria {
        Map<String, String> errors = new LinkedHashMap<>();
        if (minPrice != null && minPrice.signum() < 0) {
            errors.put("minPrice", "must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            errors.put("maxPrice", "must not be lower than minPrice");
        }
        ListingParams.checkWindow(page, size, errors);
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid search", errors);
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        sort = sort == null ? Sort.NAME : sort;
    }
}
