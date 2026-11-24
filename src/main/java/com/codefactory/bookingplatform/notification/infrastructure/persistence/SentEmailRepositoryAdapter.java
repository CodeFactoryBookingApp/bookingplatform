package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class SentEmailRepositoryAdapter implements SentEmailRepository {

    private final SentEmailJpaRepository emails;
    private final JdbcClient jdbc;

    public SentEmailRepositoryAdapter(SentEmailJpaRepository emails, JdbcClient jdbc) {
        this.emails = emails;
        this.jdbc = jdbc;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveNotice(UUID noticeId, UUID recipientId, EmailMessage message, Instant at) {
        jdbc.sql("""
                insert into sent_emails (id, recipient_user_id, recipient, subject, body, sent_at)
                values (:id, :recipientId, :recipient, :subject, :body, :at)
                on conflict (id) do nothing
                """).param("id", noticeId).param("recipientId", recipientId).param("recipient", message.to())
                .param("subject", message.subject())
                .param("body", message.body()).param("at", at.atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentEmail> findByRecipient(UUID recipientId, String address) {
        return emails.inbox(recipientId, address).stream()
                .map(email -> new SentEmail(email.getId(), email.getRecipient(), email.getSubject(),
                        email.getBody(), email.getSentAt()))
                .toList();
    }
}
