package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PlatformAccountProtection {

    private final IdentityProviderPort identityProvider;
    private final PlatformRoleChanges changes;

    public PlatformAccountProtection(IdentityProviderPort identityProvider, PlatformRoleChanges changes) {
        this.identityProvider = identityProvider;
        this.changes = changes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireClientSuspensionAllowed(UUID clientId) {
        changes.lock();
        if (changes.find(clientId).filter(change -> change.pending() && change.role() == AppRole.ADMIN).isPresent()) {
            throw new BusinessException(ErrorCode.ROLE_CHANGE_PENDING);
        }
        try {
            if (identityProvider.getPlatformUser(clientId).role() == AppRole.ADMIN) {
                throw new BusinessException(ErrorCode.ADMINISTRATOR_ACCOUNT_PROTECTED);
            }
        } catch (UpstreamAuthException ex) {
            if (ex.error() != UpstreamAuthError.USER_NOT_FOUND) {
                throw new BusinessException(ex.error() == UpstreamAuthError.RATE_LIMITED
                        ? ErrorCode.RATE_LIMITED : ErrorCode.UPSTREAM_AUTH_ERROR);
            }
        }
    }
}
