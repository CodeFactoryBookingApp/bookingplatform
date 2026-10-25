package com.codefactory.bookingplatform.catalog.domain.model;

/**
 * A name already used where it must be unique: a resource type within its provider, or a
 * resource within its location. Raised by the repository when the unique constraint rejects it.
 */
public class DuplicateNameException extends RuntimeException {

    public DuplicateNameException(Throwable cause) {
        super("Duplicate name", cause);
    }
}
