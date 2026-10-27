package com.codefactory.bookingplatform.identity.api;

import com.codefactory.bookingplatform.identity.application.ChangeClientStatusUseCase;
import com.codefactory.bookingplatform.identity.application.ListClientsUseCase;
import com.codefactory.bookingplatform.identity.domain.model.ClientFilter;
import com.codefactory.bookingplatform.identity.domain.model.ClientStatus;
import com.codefactory.bookingplatform.identity.domain.model.ClientSummary;
import com.codefactory.bookingplatform.identity.domain.model.NotificationChannel;
import com.codefactory.bookingplatform.identity.domain.port.ClientListing;
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
 * HU-003 client listing and export, from the controller down to the listing port. The port is the
 * SQL (filters without accents, order, pagination), verified against Postgres in AdminUsersIT. Here:
 * the parameters become the filter, the page keeps its total, the export asks for every match and
 * writes each client in the header order, formulas neutralised.
 */
class AdminClientControllerTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant REGISTERED = Instant.parse("2026-10-20T14:00:00Z");

    private ClientListing listing;
    private AdminClientController controller;

    @BeforeEach
    void setUp() {
        listing = mock(ClientListing.class);
        controller = new AdminClientController(new ListClientsUseCase(listing), mock(ChangeClientStatusUseCase.class));
    }

    private static ClientSummary summary(String fullName, String phone) {
        return new ClientSummary(ID, fullName, "1017", "ana@example.com", phone, "Medellín",
                NotificationChannel.EMAIL, ClientStatus.ACTIVE, REGISTERED);
    }

    @Test
    @DisplayName("HU-003: the listing passes every filter, the sort and the page to the query, and the answer keeps the total")
    void listingTranslatesTheParameters() {
        // Arrange
        ClientFilter expected = new ClientFilter(ClientStatus.ACTIVE, "Medellín", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), ClientFilter.Sort.EMAIL, true, 1, 5);
        when(listing.find(expected)).thenReturn(new ResultPage<>(List.of(summary("Ana Pérez", "3001112233")), 6, 1, 5));

        // Act
        AdminClientController.ClientPage page = controller.list(ClientStatus.ACTIVE, " Medellín ",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "email", "desc", 1, 5);

        // Assert
        assertEquals(6, page.total());
        assertEquals(1, page.page());
        assertEquals(5, page.size());
        assertEquals(List.of(new AdminClientController.ClientRow(ID, "Ana Pérez", "1017", "ana@example.com", "3001112233",
                "Medellín", NotificationChannel.EMAIL, ClientStatus.ACTIVE, REGISTERED)), page.items());
    }

    @Test
    @DisplayName("HU-003 \"Exportar el listado filtrado\": the export asks for every match with the same filter, never a single page")
    void exportTakesEveryPage() {
        // Arrange
        ClientFilter sameFilter = new ClientFilter(ClientStatus.SUSPENDED, "Cali", null, null,
                ClientFilter.Sort.NAME, false, 0, 1);
        when(listing.findAll(sameFilter)).thenReturn(List.of(summary("Ana", "3001112233"), summary("Bea", "3001112244")));

        // Act
        ResponseEntity<String> response = controller.export(ClientStatus.SUSPENDED, "Cali", null, null, "name", "asc");

        // Assert
        verify(listing).findAll(sameFilter);
        verify(listing, never()).find(any());
        assertEquals(3, response.getBody().split("\r\n").length, "header plus one line per client");
        assertEquals("attachment; filename=\"clientes.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
    }

    @Test
    @DisplayName("HU-003: each CSV row follows the header, and a phone starting with + or a name with = is neutralised")
    void exportRowsFollowTheHeader() {
        // Arrange
        when(listing.findAll(any())).thenReturn(List.of(summary("=1+1", "+573001112233")));

        // Act
        ResponseEntity<String> response = controller.export(null, null, null, null, "registered", "desc");

        // Assert
        assertEquals("﻿id,nombre,documento,correo,telefono,ciudad,canal_notificacion,estado,registrado\r\n"
                + "44444444-4444-4444-4444-444444444444,'=1+1,1017,ana@example.com,'+573001112233,Medellín,"
                + "EMAIL,ACTIVE,2026-10-20T14:00:00Z\r\n", response.getBody());
    }
}
