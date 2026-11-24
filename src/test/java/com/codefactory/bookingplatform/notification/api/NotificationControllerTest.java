package com.codefactory.bookingplatform.notification.api;

import com.codefactory.bookingplatform.notification.application.EmailInbox;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-0007, reading the in-app inbox: whose emails a user gets is decided by their own token (the
 * account id of the subject plus its email claim), never by anything the request could choose, and
 * the order the inbox returns (newest first) reaches the response untouched. The real {@link EmailInbox} is used; only the
 * store is a double (its case-insensitive, newest-first query runs against PostgreSQL in
 * ProviderOnboardingIT).
 *
 * <p>Technique: equivalence partitioning on the token (with email / without email / blank email).
 */
class NotificationControllerTest {

    private SentEmailRepository emails;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        emails = mock(SentEmailRepository.class);
        controller = new NotificationController(new EmailInbox(emails));
    }

    private static final UUID ACCOUNT = UUID.randomUUID();

    private static JwtAuthenticationToken tokenOf(String email) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").subject(ACCOUNT.toString());
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
        when(emails.findByRecipient(ACCOUNT, "ana@example.com")).thenReturn(List.of(newest, oldest));

        // Act
        List<NotificationController.EmailResponse> found = controller.myEmails(tokenOf("ana@example.com"));

        // Assert
        verify(emails).findByRecipient(ACCOUNT, "ana@example.com");
        assertEquals(List.of(
                new NotificationController.EmailResponse(newest.id(), "Aprobado", "Tu proveedor fue aprobado",
                        newest.sentAt()),
                new NotificationController.EmailResponse(oldest.id(), "Bienvenida", "Hola", oldest.sentAt())), found);
    }

    @ParameterizedTest(name = "email claim = [{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("ADR-0007 / HU-015: a token without a usable email still reads the notices of its own account, and no address")
    void tokenWithoutEmailReadsOnlyItsAccount(String email) {
        // Act
        controller.myEmails(tokenOf(email));

        // Assert
        verify(emails).findByRecipient(ACCOUNT, null);
    }
}
