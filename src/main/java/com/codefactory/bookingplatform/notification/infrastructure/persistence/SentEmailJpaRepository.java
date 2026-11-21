package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SentEmailJpaRepository extends JpaRepository<SentEmailEntity, UUID> {

    @Query("""
            select email from SentEmailEntity email
             where email.recipientUserId = :recipientId
                or (email.recipientUserId is null and lower(email.recipient) = lower(:recipient))
             order by email.sentAt desc, email.id desc
            """)
    List<SentEmailEntity> inbox(UUID recipientId, String recipient);
}
