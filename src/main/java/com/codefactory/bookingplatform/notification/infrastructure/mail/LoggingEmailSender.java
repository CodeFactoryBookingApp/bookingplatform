package com.codefactory.bookingplatform.notification.infrastructure.mail;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used when no SMTP server is configured: local runs, tests and any deployment without
 * {@code SPRING_MAIL_HOST}. Recipient and subject are logged so the flow can still be followed;
 * the body is not, because later emails will carry links and personal data. Nothing leaves the
 * process.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.warn("No SMTP server configured; email not sent. To: {} | Subject: {}",
                message.to(), message.subject());
    }
}
