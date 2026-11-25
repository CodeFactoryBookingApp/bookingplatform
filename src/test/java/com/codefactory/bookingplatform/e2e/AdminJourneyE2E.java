package com.codefactory.bookingplatform.e2e;

import com.codefactory.bookingplatform.e2e.support.Accounts;
import com.codefactory.bookingplatform.e2e.support.Api;
import com.codefactory.bookingplatform.e2e.support.Session;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S6, the admin panel against the real stack: the admin approves providers, lists users, reads the
 * audit trail and manages roles, and nobody else gets in.
 */
class AdminJourneyE2E {

    @Test
    @DisplayName("S6 providers: the admin finds the pending provider and approving it moves it to the ACTIVE list")
    void adminApprovesAPendingProvider() {
        // Arrange
        Session admin = Accounts.adminSession();
        String email = Accounts.uniqueEmail("provider");
        String organizationId = Accounts.registerProvider(email, Accounts.PASSWORD).then().statusCode(201)
                .extract().path("organizationId");
        Api.as(admin.accessToken()).queryParams("status", "PENDING_APPROVAL", "size", 100)
                .get("/api/v1/admin/organizations").then()
                .statusCode(200)
                .body("items.id", hasItem(organizationId));

        // Act
        Response approval = Accounts.approveOrganization(admin.accessToken(), organizationId);

        // Assert
        approval.then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
        Api.as(admin.accessToken()).queryParams("status", "ACTIVE", "size", 100)
                .get("/api/v1/admin/organizations").then()
                .statusCode(200)
                .body("items.find { it.id == '" + organizationId + "' }.status", equalTo("ACTIVE"));
    }

    @Test
    @DisplayName("S6 clients: a just-verified client shows up ACTIVE in the admin client list")
    void adminListsAVerifiedClient() {
        // Arrange
        Session admin = Accounts.adminSession();
        Session client = Accounts.verifiedClientSession();

        // Act
        Response clients = Api.as(admin.accessToken()).queryParams("size", 100).get("/api/v1/admin/clients");

        // Assert
        clients.then()
                .statusCode(200)
                .body("items.find { it.email == '" + client.email() + "' }.status", equalTo("ACTIVE"));
    }

    @Test
    @DisplayName("S6 audit: an action done in the test leaves an event in the admin audit trail")
    void auditTrailRecordsTheActionsOfTheTest() {
        // Arrange
        Session admin = Accounts.adminSession();
        String email = Accounts.uniqueEmail("provider");
        String organizationId = Accounts.registerProvider(email, Accounts.PASSWORD).then().statusCode(201)
                .extract().path("organizationId");

        // Act
        Accounts.approveOrganization(admin.accessToken(), organizationId).then().statusCode(200);

        // Assert
        Api.as(admin.accessToken()).queryParams("size", 100).get("/api/v1/admin/audit-events").then()
                .statusCode(200)
                .body("items.organizationId", hasItem(organizationId));
    }

    @Test
    @DisplayName("S6 access: a CLIENT token is forbidden on the admin endpoints")
    void clientCannotUseAdminEndpoints() {
        // Arrange
        Session client = Accounts.verifiedClientSession();
        List<String> adminPaths = List.of("/api/v1/admin/organizations", "/api/v1/admin/clients",
                "/api/v1/admin/audit-events");

        // Act
        List<Integer> statuses = adminPaths.stream()
                .map(path -> Api.as(client.accessToken()).get(path).statusCode())
                .toList();

        // Assert
        assertEquals(List.of(403, 403, 403), statuses, "a CLIENT must get 403 on " + adminPaths);
    }

    @Test
    @DisplayName("S6 roles: after the admin changes a user's role, the old session is rejected and the next login reports the new role on /me")
    void roleChangeShowsUpAfterANewLogin() {
        // Arrange
        Session admin = Accounts.adminSession();
        Session client = Accounts.verifiedClientSession();

        // Act
        Response change = Api.as(admin.accessToken()).body(Map.of("role", "PROVIDER"))
                .patch("/api/v1/admin/users/{id}/role", client.userId());

        // Assert
        change.then()
                .statusCode(200)
                .body("id", equalTo(client.userId()))
                .body("role", equalTo("PROVIDER"));
        // The earlier session is invalidated, and a new one only counts if it starts after the change
        // (the backend compares the authentication time with the change at one-second precision).
        Api.as(client.accessToken()).get("/api/v1/auth/me").then().statusCode(401);
        sleepOneSecond();
        Session relogged = Accounts.loginSession(client.email(), client.password());
        Api.as(relogged.accessToken()).get("/api/v1/auth/me").then()
                .statusCode(200)
                .body("role", equalTo("PROVIDER"));
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(1_100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the role change to settle", e);
        }
    }
}
