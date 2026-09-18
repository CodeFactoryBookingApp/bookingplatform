package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * HU-001: resend the verification email with a valid link.
 * Unknown emails are silently accepted to avoid user enumeration.
 */
@Service
public class ResendVerificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendVerificationUseCase.class);

    private final ClientRepository clientRepository;
    private final UserProvisioning userProvisioning;

    public ResendVerificationUseCase(ClientRepository clientRepository, UserProvisioning userProvisioning) {
        this.clientRepository = clientRepository;
        this.userProvisioning = userProvisioning;
    }

    public void resend(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        clientRepository.findByEmail(normalizedEmail).ifPresentOrElse(
                client -> {
                    userProvisioning.resendSignupVerification(normalizedEmail);
                    log.info("Verification email resent for client {}", client.getId());
                },
                () -> log.debug("Verification resend requested for unknown email; ignored")
        );
    }
}
