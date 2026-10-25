package com.codefactory.bookingplatform.notification.application;

import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Reading the in-app inbox: each user sees only the emails addressed to them (ADR-0007). */
@Service
public class EmailInbox {

    private final SentEmailRepository emails;

    public EmailInbox(SentEmailRepository emails) {
        this.emails = emails;
    }

    public List<SentEmail> addressedTo(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return emails.findByRecipient(email.trim());
    }
}
