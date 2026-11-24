package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChangePlatformRoleUseCase {

    private static final int PAGE_SIZE = 50;

    private final PlatformRoleChanges changes;
    private final IdentityProviderPort identityProvider;
    private final RoleTokenSessionPolicy sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public ChangePlatformRoleUseCase(PlatformRoleChanges changes, IdentityProviderPort identityProvider,
                                     RoleTokenSessionPolicy sessions, ApplicationEventPublisher events, Clock clock,
                                     PlatformTransactionManager transactionManager) {
        this.changes = changes;
        this.identityProvider = identityProvider;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void change(UUID userId, AppRole role, Jwt actor) {
        try {
            RoleChange change = transactions.execute(status -> prepare(userId, role, actor));
            if (change != null) {
                transactions.executeWithoutResult(status -> finish(change, actor));
            }
        } catch (UpstreamAuthException ex) {
            throw new BusinessException(switch (ex.error()) {
                case USER_NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
                case RATE_LIMITED -> ErrorCode.RATE_LIMITED;
                default -> ErrorCode.UPSTREAM_AUTH_ERROR;
            });
        }
    }

    private RoleChange prepare(UUID userId, AppRole role, Jwt actor) {
        changes.lock();
        requireCurrentSession(actor);
        return changes.find(userId).filter(RoleChange::pending).map(pending -> resume(pending, role))
                .orElseGet(() -> reserve(userId, role));
    }

    private static RoleChange resume(RoleChange pending, AppRole role) {
        if (pending.role() != role) {
            throw new BusinessException(ErrorCode.ROLE_CHANGE_PENDING);
        }
        return pending;
    }

    private RoleChange reserve(UUID userId, AppRole role) {
        PlatformUser user = identityProvider.getPlatformUser(userId);
        if (user.role() == role) {
            return null;
        }
        if (user.role() == AppRole.ADMIN && !hasAnotherAdministrator(userId)) {
            throw new BusinessException(ErrorCode.LAST_ADMINISTRATOR);
        }
        RoleChange change = new RoleChange(userId, UUID.randomUUID(), role, null);
        changes.reserve(change);
        return change;
    }

    private void finish(RoleChange change, Jwt actor) {
        changes.lock();
        if (changes.find(change.userId()).filter(change::equals).isEmpty()) {
            return;
        }
        UUID actorId = UUID.fromString(actor.getSubject());
        // A self-demotion has already fenced the actor's session in prepare().
        if (!actorId.equals(change.userId())) {
            requireCurrentSession(actor);
        }
        identityProvider.changeRole(change);
        Instant now = clock.instant();
        changes.complete(change, now);
        events.publishEvent(new AuditEvent(now, actorId, AppRole.ADMIN.name(), "PLATFORM_ROLE_CHANGED",
                "USER", change.userId(), null, "SUCCESS"));
    }

    private void requireCurrentSession(Jwt actor) {
        if (!sessions.accepts(actor)) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    private boolean hasAnotherAdministrator(UUID excluded) {
        Instant now = clock.instant();
        for (int page = 1; ; page++) {
            List<PlatformUser> users = identityProvider.listPlatformUsers(page, PAGE_SIZE);
            if (users.stream().filter(user -> !excluded.equals(user.id()) && user.isActiveAdministrator(now))
                    .anyMatch(user -> changes.find(user.id())
                            .map(change -> !change.pending() && change.role() == AppRole.ADMIN).orElse(true))) {
                return true;
            }
            if (users.size() < PAGE_SIZE) {
                return false;
            }
        }
    }
}
