package com.codefactory.bookingplatform.booking;

import com.codefactory.bookingplatform.support.BookingFixture;
import com.codefactory.bookingplatform.support.JwtIntegrationTestBase;
import com.codefactory.bookingplatform.support.ProviderFixture;
import com.codefactory.bookingplatform.support.ProviderFixture.Provider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingCreationIT extends JwtIntegrationTestBase {
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final LocalDate day = LocalDate.now(ZONE).plusDays(2);
    private final Instant start = day.atTime(10, 0).atZone(ZONE).toInstant();
    private final List<UUID> scopes = new ArrayList<>();

    @Autowired private JdbcTemplate jdbc;

    record Offer(Provider provider, UUID service, UUID resource) {}

    @AfterEach
    void cleanReservations() {
        for (UUID scope : scopes) {
            jdbc.update("delete from booking_status_changes where booking_id in (select id from bookings where organization_id=?)", scope);
            jdbc.update("delete from bookings where organization_id=?", scope);
        }
    }

    @Test
    void verifiedClientGetsCodeResourceAndTwoNotices() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var terms = preview(offer, client);

        // Act
        var result = submit(offer, client, terms);

        // Assert
        String response = result.andExpect(status().isCreated()).andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CONFIRMED")).andReturn().getResponse().getContentAsString();
        UUID booking = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat(jdbc.queryForList("select resource_id from booking_resources where booking_id=?", UUID.class, booking))
                .containsExactly(offer.resource());
        assertThat(jdbc.queryForList("select recipient from booking_notices where booking_id=? and kind='CREATED'",
                String.class, booking)).containsExactlyInAnyOrder("CLIENT", "PROVIDER");
    }

    @Test
    void unverifiedClientCannotReserveEvenWhenApprovalIsManual() throws Exception {
        // Arrange
        var offer = offer();
        jdbc.update("update organization_policies set manual_confirmation=true where id=?", offer.provider().policyId());
        var client = client("PENDING_VERIFICATION");
        var terms = preview(offer, client);

        // Act
        var result = submit(offer, client, terms);

        // Assert
        result.andExpect(status().isForbidden());
        assertThat(count(offer)).isZero();
    }

    @Test
    void lastSeatHasOneWinnerAndFreshAvailabilityIsEmpty() throws Exception {
        // Arrange
        var offer = offer();
        var first = client("ACTIVE");
        var second = client("ACTIVE");
        var terms = preview(offer, first);
        var gate = new CountDownLatch(1);
        List<Callable<Integer>> requests = List.of(contender(gate, offer, first, terms), contender(gate, offer, second, terms));

        // Act
        List<Integer> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = requests.stream().map(executor::submit).toList();
            gate.countDown();
            for (var future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
        }

        // Assert
        assertThat(results).containsExactlyInAnyOrder(201, 409);
        assertThat(count(offer)).isOne();
        availability(offer, first).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void simultaneousPinnedAgendasSupplyIndependentAppointments() throws Exception {
        // Arrange
        var offer = offer();
        jdbc.update("update agendas set resource_id=? where location_id=?", offer.resource(), offer.provider().locationId());
        UUID secondResource = UUID.randomUUID();
        jdbc.update("""
                insert into resources (id, resource_type_id, location_id, organization_id, name)
                select ?, resource_type_id, location_id, organization_id, 'Second chair' from resources where id=?
                """, secondResource, offer.resource());
        UUID secondAgenda = UUID.randomUUID();
        jdbc.update("""
                insert into agendas (id, organization_id, location_id, resource_id, name, slot_minutes, valid_from)
                select ?, organization_id, location_id, ?, 'Second agenda', slot_minutes, valid_from
                  from agendas where resource_id=?
                """, secondAgenda, secondResource, offer.resource());
        linkAgenda(secondAgenda, offer.service(), offer.provider().locationId());
        var first = client("ACTIVE");
        var second = client("ACTIVE");
        var terms = preview(offer, first);
        submit(offer, first, terms).andExpect(status().isCreated());

        // Act
        var remaining = availability(offer, second);
        var booked = submit(offer, second, terms);
        var exhausted = availability(offer, second);

        // Assert
        remaining.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        booked.andExpect(status().isCreated());
        exhausted.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(jdbc.queryForList("select resource_id from booking_resources where location_id=?", UUID.class,
                offer.provider().locationId())).containsExactlyInAnyOrder(offer.resource(), secondResource);
        assertThat(jdbc.queryForObject("select count(distinct session_id) from bookings where service_id=?",
                Integer.class, offer.service())).isEqualTo(2);
    }

    @Test
    void clientOverlapIsRejectedAcrossProviders() throws Exception {
        // Arrange
        var first = offer();
        var second = offer();
        var client = client("ACTIVE");
        submit(first, client, preview(first, client)).andExpect(status().isCreated());

        // Act
        var result = submit(second, client, preview(second, client));

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("CLIENT_BOOKING_OVERLAP"));
        assertThat(count(second)).isZero();
    }

    @Test
    void manualApprovalHoldsCapacityAndRequiresAuthorizedMember() throws Exception {
        // Arrange
        var offer = offer();
        var other = offer();
        var client = client("ACTIVE");
        jdbc.update("update organization_policies set manual_confirmation=true where id=?", offer.provider().policyId());
        String response = submit(offer, client, preview(offer, client)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED")).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        var fixtures = new ProviderFixture(jdbc);
        UUID staff = fixtures.addMember(offer.provider().organizationId(), "STAFF");
        UUID manager = fixtures.addMember(offer.provider().organizationId(), "MANAGER");

        // Act
        var outsider = approve(offer, id, other.provider().ownerId());
        var wrongBooking = approve(other, id, other.provider().ownerId());
        var denied = approve(offer, id, staff);
        var accepted = approve(offer, id, manager);

        // Assert
        outsider.andExpect(status().isNotFound());
        wrongBooking.andExpect(status().isNotFound());
        denied.andExpect(status().isForbidden());
        accepted.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("select count(*) from booking_status_changes where booking_id=?",
                Integer.class, UUID.fromString(id))).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyFourRecentNoShowsAtThisProviderRequireApproval(boolean pinned) throws Exception {
        // Arrange
        var offer = offer();
        if (pinned) {
            jdbc.update("update agendas set resource_id=? where location_id=?", offer.resource(), offer.provider().locationId());
        }
        var other = offer();
        var three = client("ACTIVE");
        var four = client("ACTIVE");
        noShows(offer, three, 3, 2);
        noShows(offer, three, 1, 91);
        noShows(other, three, 1, 2);
        noShows(offer, four, 4, 2);
        jdbc.update("update services set customer_capacity=2 where id=?", offer.service());
        jdbc.update("update resources set capacity=2 where id=?", offer.resource());
        var terms = preview(offer, three);

        // Act
        var automatic = submit(offer, three, terms);
        var manual = submit(offer, four, terms);

        // Assert
        automatic.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        manual.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RESERVED"));
        assertThat(jdbc.queryForObject("select count(distinct session_id) from bookings where service_id=? and starts_at=?",
                Integer.class, offer.service(), start.atZone(ZONE).toOffsetDateTime())).isOne();
    }

    @Test
    void priceChangeRequiresFreshPreview() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var terms = preview(offer, client);
        jdbc.update("update services set price=price+1 where id=?", offer.service());

        // Act
        var result = submit(offer, client, terms);

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("BOOKING_TERMS_CHANGED"));
        assertThat(count(offer)).isZero();
    }

    @Test
    void closedAgendaDateOffersNoSlotsAndRejectsBooking() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        var terms = preview(offer, client);
        jdbc.update("insert into agenda_exceptions (agenda_id,exception_date,type,reason) values (?,?,'CLOSED','Festivo')",
                agendaOf(offer), day);

        // Act
        var result = submit(offer, client, terms);

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SLOT_UNAVAILABLE"));
        availability(offer, client).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(count(offer)).isZero();
    }

    @Test
    void specialHoursReplaceWeeklyBlocks() throws Exception {
        // Arrange
        var offer = offer();
        var client = client("ACTIVE");
        jdbc.update("""
                insert into agenda_exceptions (agenda_id,exception_date,type,special_start,special_end)
                values (?,?,'SPECIAL_HOURS','14:00','14:30')
                """, agendaOf(offer), day);

        // Act
        var result = availability(offer, client);

        // Assert: the weekly 10:00 slot is gone and only the special window is offered
        result.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startsAt").value(startsWith(day + "T14:00")));
    }

    private UUID agendaOf(Offer offer) {
        return jdbc.queryForObject("select agenda_id from agenda_services where service_id=?", UUID.class, offer.service());
    }

    private Callable<Integer> contender(CountDownLatch gate, Offer offer, UUID client, String terms) {
        return () -> {
            gate.await();
            var result = submit(offer, client, terms).andReturn().getResponse();
            if (result.getStatus() == 409) {
                assertThat(result.getContentAsString()).contains("SLOT_UNAVAILABLE");
            }
            return result.getStatus();
        };
    }

    private Offer offer() {
        var providers = new ProviderFixture(jdbc);
        var provider = providers.activeProvider();
        scopes.add(provider.organizationId());
        var service = providers.activeService(provider);
        var resource = new BookingFixture(jdbc).resource(provider);
        jdbc.update("""
                insert into service_resource_requirements (service_id,resource_type_id,organization_id)
                select ?,resource_type_id,organization_id from resources where id=?
                """, service, resource);
        UUID agenda = UUID.randomUUID();
        jdbc.update("insert into agendas (id,organization_id,location_id,name,slot_minutes,valid_from) values (?,?,?,'Agenda',30,?)",
                agenda, provider.organizationId(), provider.locationId(), day);
        linkAgenda(agenda, service, provider.locationId());
        return new Offer(provider, service, resource);
    }

    private void linkAgenda(UUID agenda, UUID service, UUID location) {
        jdbc.update("insert into agenda_services values (?,?,?)", agenda, service, location);
        jdbc.update("insert into schedules (agenda_id,day_of_week,start_time,end_time) values (?,?,'10:00','10:30')",
                agenda, day.getDayOfWeek().getValue());
    }

    private UUID client(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into clients values (?, 'Cliente', ?, '1990-01-01', ?, '3001112233', 'Cali', 'EMAIL', ?,
                                            localtimestamp, localtimestamp)
                """, id, id.toString().substring(0, 23), id + "@example.com", status);
        return id;
    }

    private void noShows(Offer offer, UUID client, int count, int daysAgo) {
        jdbc.update("""
                insert into bookings (client_id,organization_id,service_id,location_id,policy_id,session_id,
                                      starts_at,ends_at,total_price,status)
                select ?,?,?,?,?,gen_random_uuid(),now()-(?+n)*interval '1 day',
                       now()-(?+n)*interval '1 day'+interval '30 minutes',1000,'NO_SHOW'
                  from generate_series(0,?-1) n
                """, client, offer.provider().organizationId(), offer.service(), offer.provider().locationId(),
                offer.provider().policyId(), daysAgo, daysAgo, count);
    }

    private ResultActions availability(Offer offer, UUID client) throws Exception {
        return mockMvc.perform(get("/api/v1/booking/availability").header("Authorization", token(client, "CLIENT"))
                .param("serviceId", offer.service().toString()).param("locationId", offer.provider().locationId().toString())
                .param("date", day.toString()));
    }

    private String preview(Offer offer, UUID client) throws Exception {
        var result = mockMvc.perform(get("/api/v1/booking/preview").header("Authorization", token(client, "CLIENT"))
                .param("serviceId", offer.service().toString()).param("locationId", offer.provider().locationId().toString())
                .param("startsAt", start.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("COP")).andExpect(jsonPath("$.policyVersion").value(1));
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.termsVersion");
    }

    private ResultActions submit(Offer offer, UUID client, String terms) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", token(client, "CLIENT"))
                .contentType("application/json").content("""
                        {"serviceId":"%s","locationId":"%s","startsAt":"%s","termsVersion":"%s"}
                        """.formatted(offer.service(), offer.provider().locationId(), start, terms)));
    }

    private ResultActions approve(Offer offer, String id, UUID actor) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{org}/bookings/{id}/confirm", offer.provider().organizationId(), id)
                .header("Authorization", token(actor, "PROVIDER")));
    }

    private int count(Offer offer) {
        return jdbc.queryForObject("select count(*) from bookings where service_id=?", Integer.class, offer.service());
    }

    private static String token(UUID user, String role) {
        return "Bearer " + jwks().accessToken(user, user + "@example.com", role);
    }
}
