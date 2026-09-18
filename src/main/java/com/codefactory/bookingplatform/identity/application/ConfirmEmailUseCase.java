package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-001: confirm the client email using the one-time token sent by the
 * identity provider. Moves the client from PENDING_VERIFICATION to ACTIVE,
 * enabling the invariant "only verified clients can confirm bookings".
 */
@Service
public class ConfirmEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmEmailUseCase.class);

    private final ClientRepository clientRepository;
    private final UserProvisioning userProvisioning;

    public ConfirmEmailUseCase(ClientRepository clientRepository, UserProvisioning userProvisioning) {
        this.clientRepository = clientRepository;
        this.userProvisioning = userProvisioning;
    }

    @Transactional
    public RegistrationOutcome confirm(String tokenHash) {
        ConfirmedUser confirmedUser = userProvisioning.confirmEmail(tokenHash);
        Client client = clientRepository.findById(confirmedUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Client profile not found for the confirmed user"));
        client.verifyEmail();
        clientRepository.save(client);
        log.info("Client {} confirmed email and moved to {}", client.getId(), client.getStatus());
        return new RegistrationOutcome(client.getId(), client.getEmail(), client.getStatus());
    }
}
