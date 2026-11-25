package com.codefactory.bookingplatform.auth.application;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.auth.domain.port.IdentityProviderPort;
import com.codefactory.bookingplatform.auth.domain.port.PlatformRoleChanges;
import com.codefactory.bookingplatform.shared.error.BusinessException;
import com.codefactory.bookingplatform.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-0009 / HU-022: a client profile cannot be suspended while its account is, or is about to
 * become, a platform administrator. Decision table over "pending change to ADMIN" x "current role"
 * x "provider failure".
 */
class PlatformAccountProtectionTest {

    private static final UUID CLIENT = UUID.randomUUID();

    private IdentityProviderPort identity;
    private PlatformRoleChanges changes;
    private PlatformAccountProtection protection;

    @BeforeEach
    void setUp() {
        identity = mock(IdentityProviderPort.class);
        changes = mock(PlatformRoleChanges.class);
        when(changes.find(CLIENT)).thenReturn(Optional.empty());
        protection = new PlatformAccountProtection(identity, changes);
    }

    private void currentRole(AppRole role) {
        when(identity.getPlatformUser(CLIENT)).thenReturn(new PlatformUser(CLIENT, role, Instant.EPOCH, null, null));
    }

    @Test
    @DisplayName("ADR-0009: a pending promotion to ADMIN blocks the suspension (409 ROLE_CHANGE_PENDING) after taking the role-change lock")
    void pendingPromotionBlocksTheSuspension() {
        // Arrange
        when(changes.find(CLIENT)).thenReturn(Optional.of(new RoleChange(CLIENT, UUID.randomUUID(), AppRole.ADMIN, null)));

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> protection.requireClientSuspensionAllowed(CLIENT));

        // Assert
        assertEquals(ErrorCode.ROLE_CHANGE_PENDING, ex.errorCode());
        InOrder order = inOrder(changes);
        order.verify(changes).lock();
        order.verify(changes).find(CLIENT);
        verify(identity, never()).getPlatformUser(any());
    }

    @ParameterizedTest(name = "[{index}] change to {0}, completed={1}, current role {2}")
    @CsvSource({
            "CLIENT,   false, CLIENT",
            "PROVIDER, false, PROVIDER",
            "ADMIN,    true,  CLIENT"
    })
    @DisplayName("ADR-0009: a pending change to another role, or a completed one, does not block by itself; the current role decides")
    void otherChangesDoNotBlockByThemselves(AppRole changedTo, boolean completed, AppRole current) {
        // Arrange
        when(changes.find(CLIENT)).thenReturn(Optional.of(
                new RoleChange(CLIENT, UUID.randomUUID(), changedTo, completed ? Instant.EPOCH : null)));
        currentRole(current);

        // Act
        Executable act = () -> protection.requireClientSuspensionAllowed(CLIENT);

        // Assert
        assertDoesNotThrow(act);
    }

    @Test
    @DisplayName("ADR-0009: an account with the ADMIN role cannot be suspended as a client (409 ADMINISTRATOR_ACCOUNT_PROTECTED)")
    void administratorAccountIsProtected() {
        // Arrange
        currentRole(AppRole.ADMIN);

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> protection.requireClientSuspensionAllowed(CLIENT));

        // Assert
        assertEquals(ErrorCode.ADMINISTRATOR_ACCOUNT_PROTECTED, ex.errorCode());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(value = AppRole.class, names = {"CLIENT", "PROVIDER"})
    @DisplayName("ADR-0009: an account that is not an administrator can be suspended")
    void nonAdministratorCanBeSuspended(AppRole role) {
        // Arrange
        currentRole(role);

        // Act
        Executable act = () -> protection.requireClientSuspensionAllowed(CLIENT);

        // Assert
        assertDoesNotThrow(act);
    }

    @Test
    @DisplayName("ADR-0009: a client without an account in the provider (USER_NOT_FOUND) is not an administrator and can be suspended")
    void clientWithoutProviderAccountCanBeSuspended() {
        // Arrange
        when(identity.getPlatformUser(CLIENT)).thenThrow(
                new UpstreamAuthException(UpstreamAuthError.USER_NOT_FOUND, "missing"));

        // Act
        Executable act = () -> protection.requireClientSuspensionAllowed(CLIENT);

        // Assert
        assertDoesNotThrow(act);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            "RATE_LIMITED, RATE_LIMITED",
            "UNAVAILABLE,  UPSTREAM_AUTH_ERROR",
            "TOKEN_INVALID, UPSTREAM_AUTH_ERROR"
    })
    @DisplayName("ADR-0009: when the provider cannot say the role, the suspension is refused (fail closed) with 429 or 502")
    void unknownRoleFailsClosed(UpstreamAuthError failure, ErrorCode expected) {
        // Arrange
        when(identity.getPlatformUser(CLIENT)).thenThrow(new UpstreamAuthException(failure, "boom"));

        // Act
        BusinessException ex = assertThrows(BusinessException.class,
                () -> protection.requireClientSuspensionAllowed(CLIENT));

        // Assert
        assertEquals(expected, ex.errorCode());
    }
}
