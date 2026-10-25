package com.codefactory.bookingplatform.organization.application;

import com.codefactory.bookingplatform.notification.application.EmailNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * HU-002 "recibo una notificación por correo". Runs after the approval commits; a failure to send
 * is logged and does not touch the approval, which already stands.
 */
@Component
public class ProviderApprovalMailer {

    static final String SUBJECT = "Tu negocio fue aprobado en la plataforma de reservas";

    private static final Logger log = LoggerFactory.getLogger(ProviderApprovalMailer.class);

    private final EmailNotifications notifications;

    public ProviderApprovalMailer(EmailNotifications notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApproved(ProviderApproved event) {
        try {
            notifications.send(event.contactEmail(), SUBJECT, body(event));
        } catch (RuntimeException ex) {
            log.error("Approval email for provider {} could not be sent", event.organizationId(), ex);
        }
    }

    static String body(ProviderApproved event) {
        return """
                Hola,

                Tu negocio "%s" fue aprobado. Desde ahora es visible para los clientes \
                y puede recibir reservas en cuanto publiques tus servicios.

                Equipo de la plataforma de reservas
                """.formatted(event.name());
    }
}
