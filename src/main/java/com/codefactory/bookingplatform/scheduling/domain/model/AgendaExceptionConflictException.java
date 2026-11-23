package com.codefactory.bookingplatform.scheduling.domain.model;

/** The database refused an agenda exception: the agenda already has one for that date (V14). */
public class AgendaExceptionConflictException extends RuntimeException {

    public AgendaExceptionConflictException(Throwable cause) {
        super("Agenda exception conflict: duplicate date", cause);
    }
}