package com.codefactory.bookingplatform.catalog.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.util.Map;
import java.util.UUID;

/** HU-004/HU-009: a service needs {@code quantity} resources of a type to be provided once. */
public record ResourceRequirement(UUID resourceTypeId, int quantity) {

    public ResourceRequirement {
        if (quantity < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid resource requirement",
                    Map.of("quantity", "must be at least 1"));
        }
    }
}
