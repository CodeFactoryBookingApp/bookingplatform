package com.codefactory.bookingplatform.notification.domain.port;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;

/**
 * Port to whatever delivers email: an SMTP server, or the in-app inbox when none is configured (ADR-0007).
 */
public interface EmailSender {

    /** Delivers the message or throws; it never fails silently. */
    void send(EmailMessage message);
}
