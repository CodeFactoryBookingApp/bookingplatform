package com.codefactory.bookingplatform.booking;

import com.codefactory.bookingplatform.shared.audit.AuditActor;
import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.BookingFixture.Appointment;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingQueriesIT extends JwtIntegrationTestBase {

    private static final String SEARCH = "/api/v1/organizations/{id}/bookings";
    private static final String REPORT = "/api/v1/organizations/{id}/reports/service-demand";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuditActor audit;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProviderFixture providers;
    private BookingFixture bookings;

    @BeforeEach
    void prepareRepositories() {
        providers = new ProviderFixture(jdbc);
        bookings = new BookingFixture(jdbc);
    }

    @AfterEach
    void removeBookings() {
        bookings.clean();
    }

    @Test
    void combinedSearchFiltersAndSummaryUseAllMatchingRowsBeforePagination() throws Exception {
        // Arrange
        Provider provider = providers.activeProvider();
        UUID service = providers.activeService(provider);
        UUID location = provider.locationId();
        UUID resource = bookings.resource(provider);
        UUID additionalResource = bookings.resource(provider);
        Instant from = Instant.parse("2026-11-24T13:00:00Z");
        var first = bookings.create(provider, service, new Appointment(from, from, "CONFIRMED", 100));
        var second = bookings.create(provider, service, new Appointment(from.plusSeconds(3600), from, "CONFIRMED", 200));
        var withoutResource = bookings.create(provider, service,
                new Appointment(from.plusSeconds(7200), from, "CONFIRMED", 900));
        var cancelled = bookings.create(provider, service,
                new Appointment(from.plusSeconds(10800), from, "CANCELLED", 250));
        bookings.assign(first.id(), resource);
        bookings.assign(first.id(), additionalResource);
        bookings.assign(second.id(), resource);
        bookings.assign(cancelled.id(), resource);
        var outsideRange = bookings.create(provider, service,
                new Appointment(from.minusSeconds(3600), from, "CONFIRMED", 700));
        bookings.assign(outsideRange.id(), resource);

        // Act
        var page = mockMvc.perform(search(get(SEARCH, provider.organizationId()), provider, service, location,
                resource, from).param("size", "1"));
        var export = mockMvc.perform(search(get(SEARCH + "/export", provider.organizationId()), provider, service,
                location, resource, from));

        // Assert
        page.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(second.code()))
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.byStatus.CONFIRMED").value(2))
                .andExpect(jsonPath("$.summary.amountByCurrency.COP").value(300.0))
                .andExpect(jsonPath("$.summary.cancellationRatePercent").value(0));
        String csv = export.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains(first.code(), second.code())
                .doesNotContain(withoutResource.code(), cancelled.code(), outsideRange.code());
    }

    @Test
    void exactCodeAndClientFiltersCannotRevealAnotherProvidersBookings() throws Exception {
        // Arrange
        Provider provider = providers.activeProvider();
        Provider outsider = providers.activeProvider();
        UUID service = providers.activeService(provider);
        Instant time = Instant.parse("2026-11-25T14:00:00Z");
        var booking = bookings.create(provider, service, new Appointment(time, time, "RESERVED", 100));

        // Act
        var authorized = mockMvc.perform(get(SEARCH + "/by-code/{code}", provider.organizationId(), booking.code())
                .header("Authorization", owner(provider)));
        var forbidden = mockMvc.perform(get(SEARCH + "/by-code/{code}", provider.organizationId(), booking.code())
                .header("Authorization", owner(outsider)));
        var unseenCode = mockMvc.perform(get(SEARCH + "/by-code/{code}", outsider.organizationId(), booking.code())
                .header("Authorization", owner(outsider)));
        var unseenClient = mockMvc.perform(get(SEARCH, outsider.organizationId()).header("Authorization", owner(outsider))
                .param("clientId", booking.clientId().toString()));

        // Assert
        authorized.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(booking.id().toString()));
        forbidden.andExpect(status().isNotFound());
        unseenCode.andExpect(status().isNotFound());
        unseenClient.andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(0));
    }

    @Test
    void demandReportRanksServicesComparesLocalPeriodsAndPreservesHistoricCurrencies() throws Exception {
        // Arrange
        Provider provider = providers.activeProvider();
        UUID primary = providers.service(provider, "Consulta", null, 100, "ACTIVE");
        UUID secondary = providers.service(provider, "Seguimiento", null, 100, "ACTIVE");
        Instant morning = Instant.parse("2026-11-16T14:00:00Z");
        Instant firstCurrentDay = Instant.parse("2026-11-15T05:00:00Z");
        Instant lastPreviousDay = firstCurrentDay.minusSeconds(1);
        bookings.create(provider, primary, new Appointment(morning.minusSeconds(7 * 86400), lastPreviousDay, "COMPLETED", 80));
        bookings.create(provider, primary, new Appointment(morning, firstCurrentDay, "COMPLETED", 100));
        bookings.create(provider, primary, new Appointment(morning, firstCurrentDay, "CANCELLED", 150));
        bookings.create(provider, primary, new Appointment(morning, firstCurrentDay, "NO_SHOW", 160));
        jdbc.update("insert into currencies (code, name) values ('USD', 'Dolar') on conflict (code) do nothing");
        jdbc.update("update services set currency_id = (select id from currencies where code = 'USD') where id = ?", primary);
        bookings.create(provider, primary, new Appointment(morning, firstCurrentDay, "CONFIRMED", 200));
        bookings.create(provider, secondary, new Appointment(morning, firstCurrentDay, "CONFIRMED", 300));
        UUID manager = providers.addMember(provider.organizationId(), "MANAGER");
        UUID staff = providers.addMember(provider.organizationId(), "STAFF");

        // Act
        var result = mockMvc.perform(period(get(REPORT, provider.organizationId()), manager));
        var export = mockMvc.perform(period(get(REPORT + "/export", provider.organizationId()), manager));
        var staffRequest = mockMvc.perform(period(get(REPORT, provider.organizationId()), staff));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previousPeriod.from").value("2026-11-08"))
                .andExpect(jsonPath("$.services[0].id").value(primary.toString()))
                .andExpect(jsonPath("$.services[0].current.created").value(4))
                .andExpect(jsonPath("$.services[0].current.completed").value(1))
                .andExpect(jsonPath("$.services[0].current.cancelled").value(1))
                .andExpect(jsonPath("$.services[0].current.noShow").value(1))
                .andExpect(jsonPath("$.services[0].current.cancellationRatePercent").value(25.0))
                .andExpect(jsonPath("$.services[0].highCancellation").value(true))
                .andExpect(jsonPath("$.services[0].current.estimatedRevenueByCurrency.COP").value(100.0))
                .andExpect(jsonPath("$.services[0].current.estimatedRevenueByCurrency.USD").value(200.0))
                .andExpect(jsonPath("$.services[0].variation.createdPercent").value(300.0))
                .andExpect(jsonPath("$.services[0].variation.completionRatePercent").value(-75.0))
                .andExpect(jsonPath("$.services[0].variation.estimatedRevenuePercent.COP").value(25.0))
                .andExpect(jsonPath("$.services[1].id").value(secondary.toString()))
                .andExpect(jsonPath("$.demand[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.demand[0].hour").value(9))
                .andExpect(jsonPath("$.demand[0].bookings").value(5));
        staffRequest.andExpect(status().isForbidden());
        assertThat(export.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("ranking,actual,Consulta,posicion", "demanda,actual,,reservas,,1,9,5",
                        "metricas,actual,Consulta,ingreso_estimado,USD");
    }

    @Test
    void bookingResourceAndAttendanceChangesShareACommittedAuditTrail() throws Exception {
        // Arrange
        Provider provider = providers.activeProvider();
        UUID service = providers.activeService(provider);
        UUID resource = bookings.resource(provider);
        UUID staff = providers.addMember(provider.organizationId(), "STAFF");
        Instant at = Instant.parse("2026-11-18T14:00:00Z");
        var appointment = bookings.create(provider, service, new Appointment(at, at, "CONFIRMED", 100));

        // Act
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            audit.identify(staff, "STAFF");
            bookings.assign(appointment.id(), resource);
            jdbc.update("update bookings set status = 'NO_SHOW' where id = ?", appointment.id());
        });
        var result = mockMvc.perform(get("/api/v1/organizations/{id}/audit-events", provider.organizationId())
                .header("Authorization", owner(provider)).param("actorId", staff.toString()));

        // Assert
        String content = result.andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(content).contains("BOOKING_RESOURCE_ASSIGNED", "BOOKING_NO_SHOW", "STAFF",
                appointment.id().toString(), resource.toString());
    }

    private static MockHttpServletRequestBuilder search(MockHttpServletRequestBuilder request, Provider provider,
                                                       UUID service, UUID location, UUID resource, Instant start) {
        return request.header("Authorization", owner(provider)).param("serviceId", service.toString())
                .param("locationId", location.toString()).param("resourceId", resource.toString())
                .param("status", "CONFIRMED").param("from", start.toString())
                .param("to", start.plusSeconds(86400).toString());
    }

    private static MockHttpServletRequestBuilder period(MockHttpServletRequestBuilder request, UUID user) {
        return request.header("Authorization", "Bearer " + jwks().accessToken(user, "staff@example.com", "PROVIDER"))
                .param("from", "2026-11-15").param("to", "2026-11-22");
    }

    private static String owner(Provider provider) {
        return "Bearer " + jwks().accessToken(provider.ownerId(), provider.ownerEmail(), "PROVIDER");
    }
}
