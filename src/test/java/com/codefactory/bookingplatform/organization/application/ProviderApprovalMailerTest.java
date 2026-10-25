package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.notification.application.EmailNotifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** HU-002 "recibo una notificación por correo", once the approval has committed. */
class ProviderApprovalMailerTest {

    private static final ProviderApproved EVENT =
            new ProviderApproved(UUID.randomUUID(), "Barbería El Poblado", "contacto@barberia.com");

    private EmailNotifications notifications;
    private ProviderApprovalMailer mailer;

    @BeforeEach
    void setUp() {
        notifications = mock(EmailNotifications.class);
        mailer = new ProviderApprovalMailer(notifications);
    }

    @Test
    @DisplayName("HU-002: the approval email goes to the contact address of the provider")
    void emailsTheContactAddress() {
        // Act
        mailer.onApproved(EVENT);

        // Assert
        verify(notifications).send(eq("contacto@barberia.com"), eq(ProviderApprovalMailer.SUBJECT), anyString());
    }

    @Test
    @DisplayName("The body names the approved business")
    void bodyNamesTheBusiness() {
        // Act
        String body = ProviderApprovalMailer.body(EVENT);

        // Assert
        assertTrue(body.contains("\"Barbería El Poblado\" fue aprobado"), body);
    }

    @Test
    @DisplayName("A mail server that fails does not propagate: the approval already stands")
    void sendingFailureIsSwallowed() {
        // Arrange
        doThrow(new IllegalStateException("smtp down"))
                .when(notifications).send(anyString(), anyString(), anyString());

        // Act
        Executable act = () -> mailer.onApproved(EVENT);

        // Assert
        assertDoesNotThrow(act);
    }
}
