package com.codefactory.bookingplatform.notification.infrastructure.config;

import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.domain.port.SentEmailRepository;
import com.codefactory.bookingplatform.notification.infrastructure.mail.InboxEmailSender;
import com.codefactory.bookingplatform.notification.infrastructure.mail.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Spring Boot creates a {@link JavaMailSender} only when {@code spring.mail.host} is set. With it,
 * email goes over SMTP; without it, to the in-app inbox (ADR-0007). Render's free plan blocks
 * outbound SMTP, so production uses the inbox.
 */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
                                   SentEmailRepository inbox,
                                   @Value("${app.mail.from:}") String from,
                                   @Value("${spring.mail.username:}") String username) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        if (smtp == null) {
            return new InboxEmailSender(inbox);
        }
        return new SmtpEmailSender(smtp, from.isBlank() ? username : from);
    }
}
