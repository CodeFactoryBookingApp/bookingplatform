package com.codefactory.bookingplatform.scheduling.domain.model;

/**
 * The database refused an agenda: another one of the provider has the same name, or another
 * agenda of the same location or resource is valid in an overlapping period (V8).
 */
public class AgendaConflictException extends RuntimeException {

    public enum Reason { DUPLICATE_NAME, OVERLAPPING_PERIOD }

    private final Reason reason;

    public AgendaConflictException(Reason reason, Throwable cause) {
        super("Agenda conflict: " + reason, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
