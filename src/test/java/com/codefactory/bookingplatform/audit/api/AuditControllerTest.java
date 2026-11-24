package com.codefactory.bookingplatform.audit.api;

import com.codefactory.bookingplatform.audit.application.ListAuditEventsUseCase;
import com.codefactory.bookingplatform.audit.domain.model.AuditEntry;
import com.codefactory.bookingplatform.audit.domain.model.AuditFilter;
import com.codefactory.bookingplatform.shared.audit.AuditEvent;
import com.codefactory.bookingplatform.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-023, CSV export of the audit trail: personal identifiers are masked (only the last four
 * characters of the actor, and of the resource when the resource is a user, are kept), the other
 * identifiers are exported whole, missing values are empty cells and columns follow the header.
 * Equivalence partitioning on the resource type (USER / other) and on optional values (present / null).
 * The query and its filters run against PostgreSQL in the IT; here the use case is a double.
 */
class AuditControllerTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID ACTOR = UUID.fromString("11111111-2222-3333-4444-555555556789");
    private static final UUID USER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeee4321");
    private static final UUID SERVICE = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID ORGANIZATION = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private static final UUID LOCATION = UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab");
    private static final UUID ENTRY_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ENTRY_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant AT = Instant.parse("2026-11-21T15:00:00Z");

    private ListAuditEventsUseCase useCase;
    private AuditController controller;
    private JwtAuthenticationToken admin;

    @BeforeEach
    void setUp() {
        useCase = mock(ListAuditEventsUseCase.class);
        controller = new AuditController(useCase);
        admin = new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject(ADMIN.toString()).build(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("HU-023: the CSV masks the actor and a USER resource, keeps other ids whole and leaves missing values empty")
    void exportMasksPersonalIdentifiers() {
        // Arrange
        AuditFilter filter = new AuditFilter(null, null, null, null);
        when(useCase.export(new AuthenticatedUser(ADMIN, true), null, filter)).thenReturn(List.of(
                new AuditEntry(ENTRY_1, new AuditEvent(AT, ACTOR, "ADMIN", "PLATFORM_ROLE_CHANGED", "USER", USER,
                        null, "SUCCESS")),
                new AuditEntry(ENTRY_2, new AuditEvent(AT, null, "SYSTEM", "SERVICE_UPDATED", "SERVICE", SERVICE,
                        ORGANIZATION, "SUCCESS", LOCATION))));

        // Act
        ResponseEntity<String> response = controller.export(null,
                new AuditController.AuditFilterRequest(null, null, null, null), admin);

        // Assert
        assertEquals("﻿"
                + "id,fecha,usuario,rol,accion,tipo_recurso,recurso,organizacion,resultado,recurso_relacionado\r\n"
                + ENTRY_1 + ",2026-11-21T15:00:00Z,****6789,ADMIN,PLATFORM_ROLE_CHANGED,USER,****4321,,SUCCESS,\r\n"
                + ENTRY_2 + ",2026-11-21T15:00:00Z,,SYSTEM,SERVICE_UPDATED,SERVICE," + SERVICE + "," + ORGANIZATION
                + ",SUCCESS," + LOCATION + "\r\n", response.getBody());
    }

    @Test
    @DisplayName("HU-023: the export is a CSV attachment named auditoria.csv, in UTF-8")
    void exportIsACsvAttachment() {
        // Arrange
        when(useCase.export(new AuthenticatedUser(ADMIN, true), ORGANIZATION,
                new AuditFilter(AT, null, null, "BOOKING_CONFIRMED"))).thenReturn(List.of());

        // Act
        ResponseEntity<String> response = controller.export(ORGANIZATION,
                new AuditController.AuditFilterRequest(AT, null, null, "BOOKING_CONFIRMED"), admin);

        // Assert
        assertEquals("attachment; filename=\"auditoria.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("text/csv;charset=UTF-8", String.valueOf(response.getHeaders().getContentType()));
        assertEquals("﻿id,fecha,usuario,rol,accion,tipo_recurso,recurso,organizacion,resultado,recurso_relacionado\r\n",
                response.getBody());
    }
}
