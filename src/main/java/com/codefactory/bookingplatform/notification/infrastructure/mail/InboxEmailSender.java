package com.codefactory.bookingplatform.notification.infrastructure.mail;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP server is configured, which is the case on Render's free plan: it blocks
 * outbound SMTP. The email is stored in the in-app inbox, where its recipient reads it through
 * the API (ADR-0007).
 */
public class InboxEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(InboxEmailSender.class);

    private final SentEmailRepository inbox;

    public InboxEmailSender(SentEmailRepository inbox) {
        this.inbox = inbox;
    }

    @Override
    public void send(EmailMessage message) {
        inbox.save(message);
        log.info("Email '{}' stored in the in-app inbox", message.subject());
    }
}
