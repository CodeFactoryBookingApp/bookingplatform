package com.codefactory.bookingplatform.notification.domain.port;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The in-app inbox: where emails go when there is no SMTP server (ADR-0007). */
public interface SentEmailRepository {

    void save(EmailMessage message);

    void saveNotice(UUID noticeId, UUID recipientId, EmailMessage message, Instant at);

    /** Account notices and legacy address-based emails, newest first. */
    List<SentEmail> findByRecipient(UUID recipientId, String email);
}
