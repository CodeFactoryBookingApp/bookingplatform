package com.codefactory.bookingplatform.organization.domain.model;

import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operating rules of a provider (HU-002): how far in advance a booking may be made, and until
 * when it can be cancelled without penalty. All values are minutes.
 *
 * <p>The database enforces the same rules (ck_organization_policies_notice); validating here lets
 * the API answer with the field at fault instead of a constraint name.
 */
public record OperationPolicy(int bookingNoticeMinutesMin,
                              int bookingNoticeMinutesMax,
                              int freeCancellationWindowMinutes) {

    public OperationPolicy {
        Map<String, String> errors = new LinkedHashMap<>();
        if (bookingNoticeMinutesMin < 0) {
            errors.put("bookingNoticeMinutesMin", "must not be negative");
        }
        if (bookingNoticeMinutesMax < 0) {
            errors.put("bookingNoticeMinutesMax", "must not be negative");
        }
        if (freeCancellationWindowMinutes < 0) {
            errors.put("freeCancellationWindowMinutes", "must not be negative");
        }
        if (bookingNoticeMinutesMin > bookingNoticeMinutesMax) {
            errors.put("bookingNoticeMinutesMin", "must not be greater than bookingNoticeMinutesMax");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid operation parameters", errors);
        }
    }
}
