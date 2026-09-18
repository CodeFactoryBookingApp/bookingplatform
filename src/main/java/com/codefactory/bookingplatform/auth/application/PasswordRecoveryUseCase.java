package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Password recovery request (HU-021). Always answers success for unknown emails
 * to avoid user enumeration; the one-time link is sent by the identity provider.
 */
@Service
public class PasswordRecoveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryUseCase.class);

    private final IdentityProviderPort identityProvider;

    public PasswordRecoveryUseCase(IdentityProviderPort identityProvider) {
        this.identityProvider = identityProvider;
    }

    public void requestRecovery(String email) {
        try {
            identityProvider.sendPasswordRecovery(email);
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.USER_NOT_FOUND) {
                log.debug("Recovery requested for unknown email; ignored to avoid user enumeration");
                return;
            }
            throw ex;
        }
    }
}
