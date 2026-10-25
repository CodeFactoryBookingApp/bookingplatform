package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import org.springframework.stereotype.Service;

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

    public EmailNotifications(EmailSender sender) {
        this.sender = sender;
    }

    public void send(String to, String subject, String body) {
        sender.send(new EmailMessage(to, subject, body));
    }
}
