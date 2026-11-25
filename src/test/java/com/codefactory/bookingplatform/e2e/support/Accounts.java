package com.codefactory.bookingplatform.e2e.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;

/**
 * Builds the users and the catalog data a scenario starts from, through the same public API the app
 * uses (and the GoTrue admin API for the admin, which no endpoint can create). Every call makes
 * brand-new records with unique emails, so a test never depends on what another one left behind.
 *
 * <p>The {@code Response}-returning methods are single steps for tests that assert them; the
 * {@code ...Session}/{@link #publishedService()} methods are the guarded Arrange that chains them.
 */
public final class Accounts {

    public static final String PASSWORD = "Xxxx123!x";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private Accounts() {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@e2e.test";
    }

    public static String today() {
        return LocalDate.now(BOGOTA).toString();
    }

    // --- client ---

    public static Response registerClient(String email, String password) {
        return Api.anonymous().body(Map.of(
                "fullName", "E2E Client",
                "document", "E2E-" + UUID.randomUUID().toString().substring(0, 12),
                "birthDate", "1990-01-01",
                "email", email,
                "phone", "3001234567",
                "city", "Bogotá",
                "notificationChannel", "EMAIL",
                "password", password)).post("/api/v1/registrations");
    }

    /** Confirms the email through the link Supabase Auth sent, as the user would. */
    public static Response verifyEmail(String email) {
        String tokenHash = MailpitClient.awaitToken(email, MailpitClient.CONFIRM_EMAIL_SUBJECT);
        return Api.anonymous().body(Map.of("tokenHash", tokenHash)).post("/api/v1/registrations/email-verifications");
    }

    /** A client that registered, confirmed its email and logged in. */
    public static Session verifiedClientSession() {
        String email = uniqueEmail("client");
        registerClient(email, PASSWORD).then().statusCode(201);
        verifyEmail(email).then().statusCode(200);
        return loginSession(email, PASSWORD);
    }

    // --- admin ---

    /** An admin: created straight in GoTrue (there is no sign-up for it) with the ADMIN role, then logged in. */
    public static Session adminSession() {
        String email = uniqueEmail("admin");
        RestAssured.given().baseUri(E2eConfig.SUPABASE_URL)
                .header("apikey", E2eConfig.supabaseSecretKey())
                .header("Authorization", "Bearer " + E2eConfig.supabaseSecretKey())
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", PASSWORD, "email_confirm", true,
                        "app_metadata", Map.of("role", "ADMIN")))
                .post("/auth/v1/admin/users")
                .then().statusCode(200);
        return loginSession(email, PASSWORD);
    }

    // --- provider ---

    public static Response registerProvider(String email, String password) {
        return Api.anonymous().body(Map.ofEntries(
                Map.entry("email", email),
                Map.entry("password", password),
                Map.entry("name", "E2E Provider " + UUID.randomUUID().toString().substring(0, 8)),
                Map.entry("categoryId", firstId("/api/v1/organization-categories")),
                Map.entry("identificationType", "NIT"),
                Map.entry("identificationNumber", UUID.randomUUID().toString().substring(0, 12)),
                Map.entry("contactPhone", "3001112233"),
                Map.entry("locations", List.of(Map.of(
                        "name", "Main branch",
                        "address", "Calle 1 # 2-3",
                        "cityId", firstId("/api/v1/cities")))),
                Map.entry("bookingNoticeMinutesMin", 0),
                Map.entry("bookingNoticeMinutesMax", 43200),
                Map.entry("freeCancellationWindowMinutes", 60)))
                .post("/api/v1/provider-registrations");
    }

    public static Response approveOrganization(String adminToken, String organizationId) {
        return Api.as(adminToken).body(Map.of("reason", "E2E approval"))
                .post("/api/v1/admin/organizations/{id}/approval", organizationId);
    }

    public static Response createResourceType(String providerToken, String organizationId) {
        return Api.as(providerToken).body(Map.of("name", "Room", "description", "A room that hosts a service"))
                .post("/api/v1/organizations/{org}/resource-types", organizationId);
    }

    public static Response createResource(String providerToken, String organizationId, String resourceTypeId,
                                          String locationId) {
        return Api.as(providerToken).body(Map.of(
                        "resourceTypeId", resourceTypeId, "locationId", locationId, "name", "Room 1", "capacity", 1))
                .post("/api/v1/organizations/{org}/resources", organizationId);
    }

    public static Response createService(String providerToken, String organizationId, String serviceName,
                                         String resourceTypeId, String locationId) {
        return Api.as(providerToken).body(Map.of(
                        "details", Map.of("name", serviceName, "description", "Service made by the e2e suite",
                                "durationMinutes", 60, "price", 80000),
                        "locationIds", List.of(locationId),
                        "requirements", List.of(Map.of("resourceTypeId", resourceTypeId, "quantity", 1))))
                .post("/api/v1/organizations/{org}/services", organizationId);
    }

    public static Response activateService(String providerToken, String organizationId, String serviceId) {
        return Api.as(providerToken).post("/api/v1/organizations/{org}/services/{id}/activation",
                organizationId, serviceId);
    }

    /** An agenda that opens the service every day from 08:00 to 18:00 in one-hour slots. */
    public static Response createAgenda(String providerToken, String organizationId, String locationId,
                                        String serviceId) {
        List<Map<String, String>> blocks = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            blocks.add(Map.of("dayOfWeek", day.name(), "startTime", "08:00", "endTime", "18:00"));
        }
        return Api.as(providerToken).body(Map.of(
                        "name", "Daily agenda", "locationId", locationId, "slotMinutes", 60,
                        "validFrom", today(), "serviceIds", List.of(serviceId), "blocks", blocks))
                .post("/api/v1/organizations/{org}/agendas", organizationId);
    }

    public static String firstLocationId(String providerToken, String organizationId) {
        return Api.as(providerToken).get("/api/v1/organizations/{org}", organizationId)
                .then().statusCode(200)
                .extract().path("locations[0].id");
    }

    /**
     * A provider that registered, verified its email, got approved by an admin and published a
     * service (resource type, resource, active service, agenda) that the public catalog lists.
     */
    public static PublishedService publishedService() {
        String email = uniqueEmail("provider");
        String organizationId = registerProvider(email, PASSWORD).then().statusCode(201)
                .body("status", equalTo("PENDING_APPROVAL"))
                .extract().path("organizationId");
        verifyEmail(email).then().statusCode(200);
        Session provider = loginSession(email, PASSWORD);
        approveOrganization(adminSession().accessToken(), organizationId).then().statusCode(200);

        String token = provider.accessToken();
        String locationId = firstLocationId(token, organizationId);
        String resourceTypeId = createResourceType(token, organizationId).then().statusCode(201)
                .extract().path("id");
        createResource(token, organizationId, resourceTypeId, locationId).then().statusCode(201);
        String serviceName = "E2E Service " + UUID.randomUUID();
        String serviceId = createService(token, organizationId, serviceName, resourceTypeId, locationId)
                .then().statusCode(201).extract().path("id");
        activateService(token, organizationId, serviceId).then().statusCode(200);
        createAgenda(token, organizationId, locationId, serviceId).then().statusCode(201);
        return new PublishedService(provider, organizationId, locationId, serviceId, serviceName);
    }

    // --- shared ---

    public static Session loginSession(String email, String password) {
        String accessToken = Api.login(email, password).then().statusCode(200).extract().path("accessToken");
        String userId = Api.as(accessToken).get("/api/v1/auth/me").then().statusCode(200).extract().path("id");
        return new Session(email, password, userId, accessToken);
    }

    private static String firstId(String path) {
        return Api.anonymous().get(path).then().statusCode(200).extract().path("[0].id");
    }
}
