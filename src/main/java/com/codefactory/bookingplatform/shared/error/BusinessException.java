package com.codefactory.bookingplatform.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static BusinessException of(ErrorCode errorCode, String message, String detailKey, String detailValue) {
        return new BusinessException(errorCode, message, Map.of(detailKey, detailValue));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, String> details() {
        return details;
    }
}
