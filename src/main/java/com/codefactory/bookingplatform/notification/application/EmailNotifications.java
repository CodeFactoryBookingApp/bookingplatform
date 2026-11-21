package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Public facade of the notification module: other modules send email through this class only.
 *
 * <p>Sending is synchronous and failures propagate. A caller whose business operation must not
 * depend on the email, which is almost every caller, sends it after its transaction commits and
 * catches the failure (see {@code ProviderApprovalMailer}).
 */
@Service
public class EmailNotifications {

    private final EmailSender sender;
    private final SentEmailRepository inbox;

    public EmailNotifications(EmailSender sender, SentEmailRepository inbox) {
        this.sender = sender;
        this.inbox = inbox;
    }

    public void send(String to, String subject, String body) {
        sender.send(new EmailMessage(to, subject, body));
    }

    public void storeBookingNotice(UUID id, UUID recipientId, String to, String subject, String body, Instant at) {
        inbox.saveNotice(id, recipientId, new EmailMessage(to, subject, body), at);
    }
}
