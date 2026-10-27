package com.codefactory.bookingplatform.organization.api;

import com.codefactory.bookingplatform.organization.api.dto.AdminProviderDtos.ProviderPage;
import com.codefactory.bookingplatform.organization.application.ApproveProviderUseCase;
import com.codefactory.bookingplatform.organization.application.ChangeProviderStatusUseCase;
import com.codefactory.bookingplatform.organization.application.ListProvidersUseCase;
import com.codefactory.bookingplatform.organization.domain.model.IdentificationType;
import com.codefactory.bookingplatform.organization.domain.model.OrganizationStatus;
import com.codefactory.bookingplatform.organization.domain.model.ProviderFilter;
import com.codefactory.bookingplatform.organization.domain.model.ProviderSummary;
import com.codefactory.bookingplatform.organization.domain.port.ProviderListing;
import com.codefactory.bookingplatform.shared.listing.ResultPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-003 provider listing and export, from the controller down to the listing port (the port is
 * the SQL; its filters, order and pagination are verified against Postgres in AdminUsersIT).
 * What is checked here is the Java side: the request parameters become the filter, the page keeps
 * its total, and the export asks for every match, not one page, and writes one CSV row per
 * provider in the header order, formulas neutralised.
 */
class AdminOrganizationControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant REGISTERED = Instant.parse("2026-10-20T14:00:00Z");
    private static final Instant APPROVED = Instant.parse("2026-10-21T15:30:00Z");

    private ProviderListing listing;
    private AdminOrganizationController controller;

    @BeforeEach
    void setUp() {
        listing = mock(ProviderListing.class);
        controller = new AdminOrganizationController(mock(ApproveProviderUseCase.class),
                mock(ChangeProviderStatusUseCase.class), new ListProvidersUseCase(listing));
    }

    private static ProviderSummary summary(String name, String cities, OrganizationStatus status, Instant approvedAt) {
        return new ProviderSummary(ID, name, IdentificationType.NIT, "900123456-1", "Barbería",
                "contacto@barberia.com", "6041112233", cities, status, REGISTERED, approvedAt);
    }

    @Test
    @DisplayName("HU-003: the listing passes every filter, the sort and the page to the query, and the answer keeps the total")
    void listingTranslatesTheParameters() {
        // Arrange
        ProviderFilter expected = new ProviderFilter(OrganizationStatus.ACTIVE, CATEGORY, "Medellín",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), ProviderFilter.Sort.NAME, false, 2, 10);
        when(listing.find(expected)).thenReturn(new ResultPage<>(
                List.of(summary("Barbería El Poblado", "Medellín", OrganizationStatus.ACTIVE, APPROVED)), 21, 2, 10));

        // Act
        ProviderPage page = controller.list(OrganizationStatus.ACTIVE, CATEGORY, " Medellín ",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Name", "ASC", 2, 10);

        // Assert
        assertEquals(21, page.total());
        assertEquals(2, page.page());
        assertEquals(10, page.size());
        assertEquals(List.of("Barbería El Poblado"), page.items().stream().map(row -> row.name()).toList());
    }

    @Test
    @DisplayName("HU-003 \"Exportar el listado filtrado\": the export asks for every match with the same filter, never a single page")
    void exportTakesEveryPage() {
        // Arrange
        ProviderFilter sameFilter = new ProviderFilter(OrganizationStatus.SUSPENDED, null, null, null, null,
                ProviderFilter.Sort.REGISTERED, true, 0, 1);
        when(listing.findAll(sameFilter)).thenReturn(List.of(
                summary("Uno", "Cali", OrganizationStatus.SUSPENDED, APPROVED),
                summary("Dos", "Cali", OrganizationStatus.SUSPENDED, APPROVED)));

        // Act
        ResponseEntity<String> response = controller.export(OrganizationStatus.SUSPENDED, null, null, null, null,
                "registered", "desc");

        // Assert
        verify(listing).findAll(sameFilter);
        verify(listing, never()).find(any());
        assertEquals(3, response.getBody().split("\r\n").length, "header plus one line per provider");
        assertEquals("attachment; filename=\"proveedores.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("HU-003: each CSV row follows the header; a missing approval or city is an empty cell, a formula is neutralised")
    void exportRowsFollowTheHeader() {
        // Arrange
        when(listing.findAll(any())).thenReturn(List.of(
                summary("=HYPERLINK(\"http://x\")", null, OrganizationStatus.PENDING_APPROVAL, null)));

        // Act
        ResponseEntity<String> response = controller.export(null, null, null, null, null, "registered", "desc");

        // Assert
        assertEquals("﻿id,nombre,tipo_identificacion,numero_identificacion,categoria,correo_contacto,"
                + "telefono_contacto,ciudades,estado,registrado,aprobado\r\n"
                + "11111111-1111-1111-1111-111111111111,\"'=HYPERLINK(\"\"http://x\"\")\",NIT,900123456-1,Barbería,"
                + "contacto@barberia.com,6041112233,,PENDING_APPROVAL,2026-10-20T14:00:00Z,\r\n", response.getBody());
    }
}
