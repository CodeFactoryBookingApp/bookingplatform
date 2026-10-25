package com.codefactory.bookingplatform.identity.domain.model;

/**
 * Another client already holds the email or the document. Raised by the repository when the
 * unique constraint rejects the write, which is what happens when two registrations race past
 * the {@code existsBy...} checks at the same time.
 */
public class DuplicateClientException extends RuntimeException {

    public enum Field { EMAIL, DOCUMENT }

    private final Field field;

    public DuplicateClientException(Field field, Throwable cause) {
        super("Duplicate client " + field, cause);
        this.field = field;
    }

    public Field field() {
        return field;
    }
}
