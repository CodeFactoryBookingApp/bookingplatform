package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.service.PasswordPolicy;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Password reset with one-time recovery token (HU-021).
 */
@Service
public class PasswordResetUseCase {

    private final IdentityProviderPort identityProvider;

    public PasswordResetUseCase(IdentityProviderPort identityProvider) {
        this.identityProvider = identityProvider;
    }

    public void resetPassword(String tokenHash, String newPassword) {
        List<String> violations = PasswordPolicy.violations(newPassword);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK,
                    ErrorCode.PASSWORD_TOO_WEAK.defaultMessage(),
                    Map.of("violations", String.join("; ", violations)));
        }
        try {
            identityProvider.resetPasswordWithToken(tokenHash, newPassword);
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.TOKEN_INVALID || ex.error() == UpstreamAuthError.TOKEN_EXPIRED) {
                throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
            }
            throw mapUpstream(ex);
        }
    }

    /**
     * Same translation as {@code LoginUseCase} and {@code LogoutUseCase}: a raw
     * {@link UpstreamAuthException} has no handler and would reach the caller as a 500.
     */
    private BusinessException mapUpstream(UpstreamAuthException ex) {
        if (ex.error() == UpstreamAuthError.RATE_LIMITED) {
            return new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR, ex.getMessage());
    }
}
