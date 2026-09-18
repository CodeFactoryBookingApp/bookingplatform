package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.identity.domain.service.AgePolicy;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * HU-001: client self-registration.
 * Validates business rules, provisions the credential at the identity provider
 * (through the auth module facade) and stores the client profile.
 */
@Service
public class RegisterClientUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterClientUseCase.class);

    private final ClientRepository clientRepository;
    private final UserProvisioning userProvisioning;
    private final Clock clock;

    public RegisterClientUseCase(ClientRepository clientRepository, UserProvisioning userProvisioning, Clock clock) {
        this.clientRepository = clientRepository;
        this.userProvisioning = userProvisioning;
        this.clock = clock;
    }

    @Transactional
    public RegistrationOutcome register(RegisterClientCommand command) {
        LocalDate today = LocalDate.now(clock);
        if (!AgePolicy.isAdult(command.birthDate(), today)) {
            throw new BusinessException(ErrorCode.MINOR_NOT_ALLOWED);
        }
        String normalizedEmail = command.email().toLowerCase(Locale.ROOT);
        if (clientRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String normalizedDocument = command.document().trim().toUpperCase(Locale.ROOT);
        if (clientRepository.existsByDocument(normalizedDocument)) {
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT);
        }

        UUID userId = userProvisioning.provisionClientUser(normalizedEmail, command.password());

        Client client = Client.pendingVerification(
                userId,
                command.fullName().trim(),
                normalizedDocument,
                command.birthDate(),
                normalizedEmail,
                command.phone().trim(),
                command.city().trim(),
                command.notificationChannel());
        try {
            Client saved = clientRepository.save(client);
            log.info("Client {} registered in status {}", saved.getId(), saved.getStatus());
            return new RegistrationOutcome(saved.getId(), saved.getEmail(), saved.getStatus());
        } catch (RuntimeException ex) {
            userProvisioning.deprovisionUser(userId);
            throw ex;
        }
    }
}
