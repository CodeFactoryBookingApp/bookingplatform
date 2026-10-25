package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class SentEmailRepositoryAdapter implements SentEmailRepository {

    private final SentEmailJpaRepository emails;

    public SentEmailRepositoryAdapter(SentEmailJpaRepository emails) {
        this.emails = emails;
    }

    /**
     * Emails are sent from AFTER_COMMIT listeners. At that point the business transaction has
     * committed but is still bound to the thread, and a write that joined it would never be
     * committed; hence a transaction of its own.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(EmailMessage message) {
        SentEmailEntity entity = new SentEmailEntity();
        entity.setId(UUID.randomUUID());
        entity.setRecipient(message.to());
        entity.setSubject(message.subject());
        entity.setBody(message.body());
        emails.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentEmail> findByRecipient(String recipient) {
        return emails.findByRecipientIgnoreCaseOrderBySentAtDesc(recipient).stream()
                .map(email -> new SentEmail(email.getId(), email.getRecipient(), email.getSubject(),
                        email.getBody(), email.getSentAt()))
                .toList();
    }
}
