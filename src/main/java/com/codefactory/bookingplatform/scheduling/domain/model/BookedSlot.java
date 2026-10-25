package com.codefactory.bookingplatform.scheduling.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A booking that still has to happen in an agenda: what a change of the agenda must not leave stranded. */
public record BookedSlot(UUID bookingId, Instant startsAt, Instant endsAt) {
}
