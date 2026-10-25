package com.codefactory.bookingplatform.notification.domain.port;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;

import java.util.List;

/** The in-app inbox: where emails go when there is no SMTP server (ADR-0007). */
public interface SentEmailRepository {

    void save(EmailMessage message);

    /** The emails addressed to {@code recipient}, newest first; the address is matched ignoring case. */
    List<SentEmail> findByRecipient(String recipient);
}
