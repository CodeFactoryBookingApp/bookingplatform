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
 * Logout use case (HU-021): revokes the active session (refresh token) at the
 * identity provider. Access tokens are short lived by configuration (ADR-003).
 */
@Service
public class LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);

    private final IdentityProviderPort identityProvider;

    public LogoutUseCase(IdentityProviderPort identityProvider) {
        this.identityProvider = identityProvider;
    }

    public void logout(String accessToken) {
        try {
            identityProvider.signOut(accessToken);
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.TOKEN_INVALID || ex.error() == UpstreamAuthError.TOKEN_EXPIRED) {
                log.debug("Logout with already invalid session; treated as success");
                return;
            }
            throw new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR, ex.getMessage());
        }
    }
}
