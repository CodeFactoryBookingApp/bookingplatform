package com.codefactory.bookingplatform.e2e;

import com.codefactory.bookingplatform.e2e.support.Accounts;
import com.codefactory.bookingplatform.e2e.support.Api;
import com.codefactory.bookingplatform.e2e.support.PublishedService;
import com.codefactory.bookingplatform.e2e.support.Session;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * S4, the client's side of the platform against the real stack: sign up and confirm the email, find a
 * service in the catalog, book it, see it in My bookings and cancel it.
 *
 * <p>Each test builds its own users and provider (see {@link Accounts}), so none of them depends on
 * another one having run. The steps before the last one are the Arrange, driven through the same HTTP
 * API and guarded with their expected status; the last step is the Act and its effects the Assert.
 */
class ClientJourneyE2E {

    @Test
    @DisplayName("S4 registration: a client cannot log in until the email is confirmed, then logs in as CLIENT")
    void clientRegistersConfirmsTheEmailAndLogsIn() {
        // Arrange
        String email = Accounts.uniqueEmail("client");
        Accounts.registerClient(email, Accounts.PASSWORD).then()
                .statusCode(201)
                .body("email", equalTo(email))
                .body("status", equalTo("PENDING_VERIFICATION"));
        Api.login(email, Accounts.PASSWORD).then()
                .statusCode(403)
                .body("errorCode", equalTo("EMAIL_NOT_CONFIRMED"));
        Accounts.verifyEmail(email).then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));

        // Act
        Response login = Api.login(email, Accounts.PASSWORD);

        // Assert
        String accessToken = login.then()
                .statusCode(200)
                .body("tokenType", equalToIgnoringCase("Bearer"))
                .body("refreshToken", not(empty()))
                .extract().path("accessToken");
        Api.as(accessToken).get("/api/v1/auth/me").then()
                .statusCode(200)
                .body("email", equalTo(email))
                .body("role", equalTo("CLIENT"));
    }

    @Test
    @DisplayName("S4 catalog: the published service is listed, its availability needs a session and its preview carries the terms")
    void clientFindsTheServiceAndItsAvailability() {
        // Arrange
        PublishedService service = Accounts.publishedService();
        Session client = Accounts.verifiedClientSession();
        Api.anonymous().queryParam("q", service.serviceName()).get("/api/v1/catalog/services").then()
                .statusCode(200)
                .body("items.find { it.serviceId == '" + service.serviceId() + "' }.name",
                        equalTo(service.serviceName()));
        Map<String, String> availabilityQuery = Map.of("serviceId", service.serviceId(),
                "locationId", service.locationId(), "date", bookableDate());
        Api.anonymous().queryParams(availabilityQuery).get("/api/v1/booking/availability").then().statusCode(401);
        JsonPath slots = Api.as(client.accessToken()).queryParams(availabilityQuery)
                .get("/api/v1/booking/availability")
                .then().statusCode(200).body("$", not(empty()))
                .extract().jsonPath();

        // Act
        Response preview = Api.as(client.accessToken())
                .queryParams("serviceId", service.serviceId(), "locationId", service.locationId(),
                        "startsAt", slots.getString("[0].startsAt"))
                .get("/api/v1/booking/preview");

        // Assert
        preview.then()
                .statusCode(200)
                .body("termsVersion", not(empty()))
                .body("freeCancellationWindowMinutes", equalTo(60))
                .body("price", not(empty()));
    }

    @Test
    @DisplayName("S4 booking: the client books a slot, the same slot is then taken, My bookings lists it and cancelling moves it to history")
    void clientBooksSeesAndCancelsTheBooking() {
        // Arrange
        PublishedService service = Accounts.publishedService();
        Session client = Accounts.verifiedClientSession();
        String startsAt = Api.as(client.accessToken())
                .queryParams("serviceId", service.serviceId(), "locationId", service.locationId(),
                        "date", bookableDate())
                .get("/api/v1/booking/availability")
                .then().statusCode(200).extract().path("[0].startsAt");
        String termsVersion = Api.as(client.accessToken())
                .queryParams("serviceId", service.serviceId(), "locationId", service.locationId(),
                        "startsAt", startsAt)
                .get("/api/v1/booking/preview")
                .then().statusCode(200).extract().path("termsVersion");
        Map<String, String> booking = Map.of("serviceId", service.serviceId(), "locationId", service.locationId(),
                "startsAt", startsAt, "termsVersion", termsVersion);
        String bookingId = Api.as(client.accessToken()).body(booking).post("/api/v1/bookings").then()
                .statusCode(201)
                .body("status", equalTo("CONFIRMED"))
                .body("code", not(empty()))
                .extract().path("id");
        Api.as(client.accessToken()).body(booking).post("/api/v1/bookings").then()
                .statusCode(409)
                .body("errorCode", equalTo("SLOT_UNAVAILABLE"));
        Api.as(client.accessToken()).get("/api/v1/bookings").then()
                .statusCode(200)
                .body("upcoming", hasSize(1))
                .body("upcoming[0].id", equalTo(bookingId))
                .body("upcoming[0].status", equalTo("CONFIRMED"))
                .body("upcoming[0].serviceName", equalTo(service.serviceName()));

        // Act
        Response cancel = Api.as(client.accessToken()).body(Map.of("confirm", true, "reason", "Plans changed"))
                .post("/api/v1/bookings/{id}/cancel", bookingId);

        // Assert
        cancel.then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("lateCancellation", equalTo(false));
        Api.as(client.accessToken()).get("/api/v1/bookings").then()
                .statusCode(200)
                .body("upcoming", empty())
                .body("history.find { it.id == '" + bookingId + "' }.status", equalTo("CANCELLED"));
    }

    /** Three days ahead, so cancelling later is never a late cancellation. */
    private static String bookableDate() {
        return LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3).toString();
    }
}
