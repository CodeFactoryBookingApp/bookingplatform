package com.codefactory.bookingplatform.catalog.application;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

/** A resource that does not exist or belongs to another provider: the same 404 for both (HU-022). */
final class ResourceNotFound {

    private ResourceNotFound() {
    }

    static BusinessException exception() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
    }
}
