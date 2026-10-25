package com.codefactory.bookingplatform.notification;

import com.codefactory.bookingplatform.notification.application.EmailNotifications;
import com.codefactory.bookingplatform.notification.domain.model.EmailMessage;
import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.infrastructure.config.MailConfig;
import com.codefactory.bookingplatform.notification.infrastructure.mail.LoggingEmailSender;
import com.codefactory.bookingplatform.notification.infrastructure.mail.SmtpEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The notification module (ADR-0007): the message rules, the facade, the SMTP adapter and the
 * choice between SMTP and the log.
 */
class NotificationModuleTest {

    private static final EmailMessage MESSAGE = new EmailMessage("ana@example.com", "Asunto", "Cuerpo");

    @Nested
    @DisplayName("EmailMessage")
    class Message {

        @ParameterizedTest(name = "[{index}] to=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("A message without a recipient is refused")
        void recipientIsRequired(String to) {
            // Act
            Executable act = () -> new EmailMessage(to, "Asunto", "Cuerpo");

            // Assert
            assertThrows(IllegalArgumentException.class, act);
        }

        @ParameterizedTest(name = "[{index}] subject=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = "  ")
        @DisplayName("A message without a subject is refused")
        void subjectIsRequired(String subject) {
            // Act
            Executable act = () -> new EmailMessage("ana@example.com", subject, "Cuerpo");

            // Assert
            assertThrows(IllegalArgumentException.class, act);
        }

        @Test
        @DisplayName("An empty body is allowed; a missing one is not")
        void bodyMayBeEmptyButNotNull() {
            // Act
            Executable act = () -> new EmailMessage("ana@example.com", "Asunto", null);

            // Assert
            assertThrows(NullPointerException.class, act);
            assertDoesNotThrow(() -> new EmailMessage("ana@example.com", "Asunto", ""));
        }
    }

    @Test
    @DisplayName("The facade hands exactly the requested message to the sender")
    void facadeDelegatesToTheSender() {
        // Arrange
        EmailSender sender = mock(EmailSender.class);

        // Act
        new EmailNotifications(sender).send("ana@example.com", "Asunto", "Cuerpo");

        // Assert
        verify(sender).send(MESSAGE);
    }

    @Test
    @DisplayName("SMTP: the message goes out from the configured sender address with its recipient, subject and body")
    void smtpSenderBuildsTheMail() {
        // Arrange
        JavaMailSender mailSender = mock(JavaMailSender.class);

        // Act
        new SmtpEmailSender(mailSender, "plataforma@example.com").send(MESSAGE);

        // Assert
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals("plataforma@example.com", mail.getValue().getFrom());
        assertArrayEquals(new String[]{"ana@example.com"}, mail.getValue().getTo());
        assertEquals("Asunto", mail.getValue().getSubject());
        assertEquals("Cuerpo", mail.getValue().getText());
    }

    @Test
    @DisplayName("Without SMTP the email is only logged, and that never fails")
    void loggingSenderNeverFails() {
        // Act
        Executable act = () -> new LoggingEmailSender().send(MESSAGE);

        // Assert
        assertDoesNotThrow(act);
    }

    @Nested
    @DisplayName("MailConfig chooses the sender")
    class SenderChoice {

        @SuppressWarnings("unchecked")
        private ObjectProvider<JavaMailSender> provider(JavaMailSender mailSender) {
            ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(mailSender);
            return provider;
        }

        @Test
        @DisplayName("No SMTP server configured: the log sender")
        void withoutSmtpLogs() {
            // Act
            EmailSender sender = new MailConfig().emailSender(provider(null), "", "");

            // Assert
            assertInstanceOf(LoggingEmailSender.class, sender);
        }

        @Test
        @DisplayName("SMTP configured without APP_MAIL_FROM: sends from the SMTP username, as Gmail requires")
        void withSmtpSendsFromTheUsername() {
            // Arrange
            JavaMailSender mailSender = mock(JavaMailSender.class);

            // Act
            new MailConfig().emailSender(provider(mailSender), "", "cuenta@gmail.com").send(MESSAGE);

            // Assert
            ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mail.capture());
            assertEquals("cuenta@gmail.com", mail.getValue().getFrom());
        }

        @Test
        @DisplayName("SMTP configured with APP_MAIL_FROM: that address wins over the username")
        void withSmtpAndFromSendsFromIt() {
            // Arrange
            JavaMailSender mailSender = mock(JavaMailSender.class);

            // Act
            new MailConfig().emailSender(provider(mailSender), "reservas@example.com", "cuenta@gmail.com")
                    .send(MESSAGE);

            // Assert
            ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mail.capture());
            assertEquals("reservas@example.com", mail.getValue().getFrom());
        }
    }
}
