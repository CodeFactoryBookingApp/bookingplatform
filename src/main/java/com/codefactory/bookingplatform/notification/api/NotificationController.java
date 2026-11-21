package com.codefactory.bookingplatform.notification.api;

import com.codefactory.bookingplatform.notification.application.EmailInbox;
import com.codefactory.bookingplatform.notification.domain.model.SentEmail;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app inbox of the emails the platform sends (ADR-0007)")
public class NotificationController {

    private final EmailInbox inbox;

    public NotificationController(EmailInbox inbox) {
        this.inbox = inbox;
    }

    public record EmailResponse(UUID id, String subject, String body, Instant sentAt) {

        static EmailResponse from(SentEmail email) {
            return new EmailResponse(email.id(), email.subject(), email.body(), email.sentAt());
        }
    }

    @GetMapping("/emails")
    @Operation(summary = "My emails",
            description = "Booking notices for the signed-in account and platform emails to its address, newest first.")
    public List<EmailResponse> myEmails(JwtAuthenticationToken authentication) {
        UUID accountId = AuthenticatedUser.from(authentication).id();
        String email = authentication.getToken().getClaimAsString("email");
        return inbox.addressedTo(accountId, email).stream()
                .map(EmailResponse::from)
                .toList();
    }
}
