package com.codefactory.bookingplatform.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    /**
     * Declared as {@link LinkedHashMap} — not as {@code Map} — because the field is
     * serialised with the exception: a plain {@code Map} is not a serialisable type,
     * which is what {@code javac -Xlint:serial} reports. Marking it {@code transient}
     * would silence the same warning but drop the details on deserialisation, so it is
     * the option that changes behaviour. The published view stays unmodifiable, and the
     * copy stays defensive; only the declared type changed.
     */
    private final LinkedHashMap<String, String> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = new LinkedHashMap<>(details);
    }

    public static BusinessException of(ErrorCode errorCode, String message, String detailKey, String detailValue) {
        return new BusinessException(errorCode, message, Map.of(detailKey, detailValue));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, String> details() {
        return Collections.unmodifiableMap(details);
    }
}
