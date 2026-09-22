package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
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
            throw mapUpstream(ex);
        }
    }

    /**
     * Translates the upstream failure the same way {@code LoginUseCase} and
     * {@code LogoutUseCase} do. Without it the raw {@link UpstreamAuthException} reached
     * the generic handler and the caller got a 500: on a provider rate limit the answer
     * changed from 202 to 500, which is itself an enumeration signal.
     */
    private BusinessException mapUpstream(UpstreamAuthException ex) {
        if (ex.error() == UpstreamAuthError.RATE_LIMITED) {
            return new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR, ex.getMessage());
    }
}
