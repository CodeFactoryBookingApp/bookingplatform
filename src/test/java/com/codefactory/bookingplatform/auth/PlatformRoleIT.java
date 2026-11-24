package com.codefactory.bookingplatform.auth;

import com.codefactory.bookingplatform.auth.domain.model.AppRole;
import com.codefactory.bookingplatform.auth.domain.model.PlatformUser;
import com.codefactory.bookingplatform.auth.domain.model.RoleChange;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthError;
import com.codefactory.bookingplatform.auth.domain.model.UpstreamAuthException;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformRoleIT extends JwtIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void changingRoleRejectsOldAndRefreshedSessionsAndRecordsAudit() throws Exception {
        // Arrange
        UUID admin = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        Instant beforeChange = Instant.now().minusSeconds(120);
        String oldToken = jwks().accessToken(user, "user@example.com", "CLIENT");
        doReturn(active(user, AppRole.CLIENT)).when(identityProviderPort).getPlatformUser(user);
        AtomicReference<RoleChange> written = new AtomicReference<>();
        doAnswer(call -> {
            written.set(call.getArgument(0));
            return null;
        }).when(identityProviderPort).changeRole(any());
        me(oldToken).andExpect(status().isOk());

        // Act
        change(admin, user, AppRole.PROVIDER).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PROVIDER"));

        // Assert
        UUID revision = written.get().revision();
        me(oldToken).andExpect(status().isUnauthorized());
        me(jwks().accessToken(user, "PROVIDER", revision, beforeChange))
                .andExpect(status().isUnauthorized());
        me(jwks().accessToken(user, "PROVIDER", revision, Instant.now().plusSeconds(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PROVIDER"));
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, AppRole.ADMIN))
                        .param("actorId", admin.toString()).param("action", "PLATFORM_ROLE_CHANGED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].resourceId").value(user.toString()));
    }

    @Test
    void lastAdministratorCannotBeRemovedAndNonAdminCannotChangeRoles() throws Exception {
        // Arrange
        UUID admin = UUID.randomUUID();
        doReturn(active(admin, AppRole.ADMIN)).when(identityProviderPort).getPlatformUser(admin);
        when(identityProviderPort.listPlatformUsers(1, 50)).thenReturn(List.of(active(admin, AppRole.ADMIN)));

        // Act
        var asAdmin = change(admin, admin, AppRole.CLIENT);
        var asProvider = mockMvc.perform(patch("/api/v1/admin/users/{id}/role", admin)
                        .header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), AppRole.PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CLIENT\"}"));

        // Assert
        asAdmin.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("LAST_ADMINISTRATOR"));
        asProvider.andExpect(status().isForbidden());
        verify(identityProviderPort, never()).changeRole(any());
    }

    @Test
    void concurrentDemotionsLeaveAtLeastOneActiveAdministrator() throws Exception {
        // Arrange
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var users = new ConcurrentHashMap<UUID, PlatformUser>();
        users.put(first, active(first, AppRole.ADMIN));
        users.put(second, active(second, AppRole.ADMIN));
        doAnswer(call -> users.get(call.getArgument(0))).when(identityProviderPort).getPlatformUser(any());
        when(identityProviderPort.listPlatformUsers(1, 50)).thenAnswer(call -> List.copyOf(users.values()));
        doAnswer(call -> {
            RoleChange update = call.getArgument(0);
            users.put(update.userId(), active(update.userId(), update.role()));
            return null;
        }).when(identityProviderPort).changeRole(any());

        CountDownLatch start = new CountDownLatch(1);
        List<Integer> responses;

        // Act
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> demoteAfter(start, first));
            var b = workers.submit(() -> demoteAfter(start, second));
            start.countDown();
            responses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }

        // Assert
        assertThat(responses).containsExactlyInAnyOrder(200, 409);
        assertThat(users.values().stream().filter(user -> user.role() == AppRole.ADMIN).count()).isEqualTo(1);
    }

    @Test
    void ambiguousProviderFailureKeepsSessionsFencedUntilRetryCompletes() throws Exception {
        // Arrange
        UUID admin = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        doReturn(active(user, AppRole.CLIENT)).when(identityProviderPort).getPlatformUser(user);
        doReturn(active(other, AppRole.CLIENT)).when(identityProviderPort).getPlatformUser(other);
        AtomicReference<RoleChange> attempt = new AtomicReference<>();
        doAnswer(call -> {
            RoleChange change = call.getArgument(0);
            if (change.userId().equals(user) && attempt.getAndSet(change) == null) {
                throw new UpstreamAuthException(UpstreamAuthError.UNAVAILABLE, "Connection lost");
            }
            return null;
        }).when(identityProviderPort).changeRole(any());

        try {
            // Act
            var failed = change(admin, user, AppRole.PROVIDER);
            UUID revision = attempt.get().revision();
            var oldSession = me(jwks().accessToken(user, "user@example.com", "CLIENT"));
            var unrelated = change(admin, other, AppRole.PROVIDER);
            var retried = change(admin, user, AppRole.PROVIDER);

            // Assert
            failed.andExpect(status().isBadGateway());
            oldSession.andExpect(status().isUnauthorized());
            unrelated.andExpect(status().isOk());
            retried.andExpect(status().isOk());
            assertThat(attempt.get().revision()).isEqualTo(revision);
        } finally {
            jdbc.update("delete from platform_role_changes where user_id = ?", user);
        }
    }

    private int demoteAfter(CountDownLatch start, UUID admin) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return change(admin, admin, AppRole.CLIENT).andReturn().getResponse().getStatus();
    }

    private ResultActions change(UUID actor, UUID target, AppRole role) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/{id}/role", target)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor, AppRole.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role.name() + "\"}"));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static PlatformUser active(UUID id, AppRole role) {
        return new PlatformUser(id, role, Instant.EPOCH, null, null);
    }

    private static String bearer(UUID id, AppRole role) {
        return "Bearer " + jwks().accessToken(id, "user@example.com", role.name());
    }
}
