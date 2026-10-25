package com.codefactory.bookingplatform.notification.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sent_emails")
@Getter
@Setter
@NoArgsConstructor
public class SentEmailEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient", nullable = false, length = 160, updatable = false)
    private String recipient;

    @Column(name = "subject", nullable = false, length = 200, updatable = false)
    private String subject;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    /** Filled by the database default. */
    @Column(name = "sent_at", insertable = false, updatable = false)
    private Instant sentAt;
}
