package com.codefactory.bookingplatform.identity.application;

import com.codefactory.bookingplatform.auth.application.UserProvisioning;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.identity.domain.model.Client;
import com.codefactory.bookingplatform.identity.domain.port.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * HU-001: confirm the client email using the one-time token sent by the
 * identity provider. Moves the client from PENDING_VERIFICATION to ACTIVE,
 * enabling the invariant "only verified clients can confirm bookings".
 *
 * <p>Provider accounts (HU-002) verify their email through the same link and have no client
 * profile: for them the confirmation at the identity provider is all there is to do.
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
        Optional<Client> profile = clientRepository.findById(confirmedUser.userId());
        if (profile.isEmpty()) {
            log.info("User {} confirmed email; it has no client profile", confirmedUser.userId());
            return new RegistrationOutcome(confirmedUser.userId(), confirmedUser.email(), null);
        }
        Client client = profile.get();
        client.verifyEmail();
        clientRepository.save(client);
        log.info("Client {} confirmed email and moved to {}", client.getId(), client.getStatus());
        return new RegistrationOutcome(client.getId(), client.getEmail(), client.getStatus());
    }
}
