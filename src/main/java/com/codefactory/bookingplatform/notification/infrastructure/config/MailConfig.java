package com.codefactory.bookingplatform.notification.infrastructure.config;

import com.codefactory.bookingplatform.notification.domain.port.EmailSender;
import com.codefactory.bookingplatform.notification.infrastructure.mail.LoggingEmailSender;
import com.codefactory.bookingplatform.notification.infrastructure.mail.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Spring Boot creates a {@link JavaMailSender} only when {@code spring.mail.host} is set. With it,
 * email goes over SMTP; without it, to the log (ADR-0007).
 */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    @Bean
    public EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender,
                            @Value("${app.mail.from:}") String from,
                            @Value("${spring.mail.username:}") String username) {
        JavaMailSender smtp = mailSender.getIfAvailable();
        if (smtp == null) {
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(smtp, from.isBlank() ? username : from);
    }
}
