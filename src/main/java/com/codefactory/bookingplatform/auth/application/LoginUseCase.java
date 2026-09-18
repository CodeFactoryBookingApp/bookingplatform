package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AuthTokens;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.LoginAttemptRepository;
import com.codefactory.bookingplatform.auth.domain.service.LoginLockPolicy;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Login use case (HU-021). Enforces the failed-attempt lock policy locally
 * before delegating credential verification to the identity provider.
 */
@Service
public class LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCase.class);

    private final IdentityProviderPort identityProvider;
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginLockPolicy loginLockPolicy;
    private final Clock clock;

    public LoginUseCase(IdentityProviderPort identityProvider,
                        LoginAttemptRepository loginAttemptRepository,
                        LoginLockPolicy loginLockPolicy,
                        Clock clock) {
        this.identityProvider = identityProvider;
        this.loginAttemptRepository = loginAttemptRepository;
        this.loginLockPolicy = loginLockPolicy;
        this.clock = clock;
    }

    public AuthTokens login(String email, String password) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        List<Instant> recentFailures = loginAttemptRepository
                .findFailuresSince(normalizedEmail, now.minus(loginLockPolicy.lockWindow()));
        if (loginLockPolicy.isBlocked(recentFailures, now)) {
            log.warn("Login blocked for {} after {} failed attempts", normalizedEmail, recentFailures.size());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    ErrorCode.ACCOUNT_LOCKED.defaultMessage(),
                    Map.of("retryAfterMinutes", String.valueOf(loginLockPolicy.lockWindow().toMinutes())));
        }
        try {
            AuthTokens tokens = identityProvider.requestPasswordToken(normalizedEmail, password);
            loginAttemptRepository.recordAttempt(normalizedEmail, true, clock.instant());
            return tokens;
        } catch (UpstreamAuthException ex) {
            if (ex.error() == UpstreamAuthError.INVALID_CREDENTIALS || ex.error() == UpstreamAuthError.EMAIL_NOT_CONFIRMED) {
                loginAttemptRepository.recordAttempt(normalizedEmail, false, clock.instant());
            }
            throw mapUpstream(ex);
        }
    }

    private BusinessException mapUpstream(UpstreamAuthException ex) {
        return switch (ex.error()) {
            case INVALID_CREDENTIALS -> new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            case EMAIL_NOT_CONFIRMED -> new BusinessException(ErrorCode.EMAIL_NOT_CONFIRMED);
            case RATE_LIMITED -> new BusinessException(ErrorCode.RATE_LIMITED);
            default -> new BusinessException(ErrorCode.UPSTREAM_AUTH_ERROR, ex.getMessage());
        };
    }
}
