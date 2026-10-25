package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SentEmailJpaRepository extends JpaRepository<SentEmailEntity, UUID> {

    List<SentEmailEntity> findByRecipientIgnoreCaseOrderBySentAtDesc(String recipient);
}
