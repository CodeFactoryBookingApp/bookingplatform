package com.codefactory.bookingplatform.notification.api;

import com.codefactory.bookingplatform.notification.application.EmailInbox;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ADR-0007, reading the in-app inbox: whose emails a user gets is decided by the email claim of
 * their own token, never by anything the request could choose, and the order the inbox returns
 * (newest first) reaches the response untouched. The real {@link EmailInbox} is used; only the
 * store is a double (its case-insensitive, newest-first query runs against PostgreSQL in
 * ProviderOnboardingIT).
 *
 * <p>Technique: equivalence partitioning on the token (with email / without email).
 */
class NotificationControllerTest {

    private SentEmailRepository emails;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        emails = mock(SentEmailRepository.class);
        controller = new NotificationController(new EmailInbox(emails));
    }

    private static JwtAuthenticationToken tokenOf(String email) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").subject(UUID.randomUUID().toString());
        if (email != null) {
            jwt.claim("email", email);
        }
        return new JwtAuthenticationToken(jwt.build());
    }

    @Test
    @DisplayName("ADR-0007: a user gets the emails addressed to the email of their token, in the order of the inbox")
    void inboxIsTheOneOfTheTokenEmail() {
        // Arrange
        SentEmail newest = new SentEmail(UUID.randomUUID(), "ana@example.com", "Aprobado", "Tu proveedor fue aprobado",
                Instant.parse("2026-10-26T15:00:00Z"));
        SentEmail oldest = new SentEmail(UUID.randomUUID(), "ana@example.com", "Bienvenida", "Hola",
                Instant.parse("2026-10-20T15:00:00Z"));
        when(emails.findByRecipient("ana@example.com")).thenReturn(List.of(newest, oldest));

        // Act
        List<NotificationController.EmailResponse> found = controller.myEmails(tokenOf("ana@example.com"));

        // Assert
        verify(emails).findByRecipient("ana@example.com");
        assertEquals(List.of(
                new NotificationController.EmailResponse(newest.id(), "Aprobado", "Tu proveedor fue aprobado",
                        newest.sentAt()),
                new NotificationController.EmailResponse(oldest.id(), "Bienvenida", "Hola", oldest.sentAt())), found);
    }

    @Test
    @DisplayName("ADR-0007: a token without an email claim sees an empty inbox, and nobody's emails are read")
    void tokenWithoutEmailSeesNothing() {
        // Act
        List<NotificationController.EmailResponse> found = controller.myEmails(tokenOf(null));

        // Assert
        assertEquals(List.of(), found);
        verifyNoInteractions(emails);
    }
}
