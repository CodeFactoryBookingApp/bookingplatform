package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.ConfirmedUser;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.service.PasswordPolicy;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserProvisioningService implements UserProvisioning {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    private final IdentityProviderPort identityProvider;

    public UserProvisioningService(IdentityProviderPort identityProvider) {
        this.identityProvider = identityProvider;
    }

    @Override
    public UUID provisionClientUser(String email, String password) {
        List<String> violations = PasswordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK,
                    ErrorCode.PASSWORD_TOO_WEAK.defaultMessage(),
                    java.util.Map.of("violations", String.join("; ", violations)));
        }
        try {
            return identityProvider.createUser(email, password, AppRole.CLIENT);
        } catch (UpstreamAuthException ex) {
            throw mapUpstream(ex);
        }
    }

    @Override
    public void deprovisionUser(UUID userId) {
        try {
            identityProvider.deleteUser(userId);
            log.info("Compensated provisioning: auth user {} deleted", userId);
        } catch (RuntimeException ex) {
            log.error("Compensation failed: could not delete auth user {}. Manual cleanup required.", userId, ex);
        }
    }

    @Override
    public void resendSignupVerification(String email) {
        try {
            identityProvider.resendSignupVerification(email);
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.USER_NOT_FOUND) {
                log.debug("Resend requested for unknown email; ignored to avoid user enumeration");
                return;
            }
            throw mapUpstream(ex);
        }
    }

    @Override
    public ConfirmedUser confirmEmail(String tokenHash) {
        try {
            return identityProvider.verifyEmailToken(tokenHash);
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.TOKEN_INVALID || ex.error() == UpstreamAuthError.TOKEN_EXPIRED) {
                throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
            }
            throw mapUpstream(ex);
        }
    }

    private BusinessException mapUpstream(UpstreamAuthException ex) {
        return switch (ex.error()) {
            case USER_ALREADY_EXISTS -> new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            case RATE_LIMITED -> new BusinessException(ErrorCode.RATE_LIMITED);
            case USER_NOT_FOUND -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            case TOKEN_INVALID, TOKEN_EXPIRED -> new BusinessException(ErrorCode.VERIFICATION_TOKEN_INVALID);
            case INVALID_CREDENTIALS -> new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            case EMAIL_NOT_CONFIRMED -> new BusinessException(ErrorCode.EMAIL_NOT_CONFIRMED);
            case UNAVAILABLE -> new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR, ex.getMessage());
        };
    }
}
