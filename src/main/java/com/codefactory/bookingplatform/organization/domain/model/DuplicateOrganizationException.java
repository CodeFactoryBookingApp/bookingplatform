package com.codefactory.bookingplatform.organization.domain.model;

/**
 * Another provider already holds the identification. Raised by the repository when the unique
 * constraint rejects the write, which is what happens when two registrations race past the check.
 */
public class DuplicateOrganizationException extends RuntimeException {

    public DuplicateOrganizationException(Throwable cause) {
        super("Duplicate provider identification", cause);
    }
}
