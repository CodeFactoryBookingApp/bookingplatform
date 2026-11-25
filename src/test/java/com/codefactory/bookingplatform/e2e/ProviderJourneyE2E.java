package com.codefactory.bookingplatform.e2e;

import com.codefactory.bookingplatform.e2e.support.Accounts;
import com.codefactory.bookingplatform.e2e.support.Api;
import com.codefactory.bookingplatform.e2e.support.Session;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

/**
 * S5, a provider's onboarding against the real stack: registration, email confirmation, the catalog
 * setup (resource type, resource, service, agenda) and admin approval, which is what makes the
 * service appear in the public catalog.
 */
class ProviderJourneyE2E {

    @Test
    @DisplayName("S5 onboarding: the provider confirms the email and sets up its catalog, stays unlisted until the admin approves and then gets listed")
    void providerOnboardsAndPublishesAService() {
        // Arrange
        String email = Accounts.uniqueEmail("provider");
        String organizationId = Accounts.registerProvider(email, Accounts.PASSWORD).then()
                .statusCode(201)
                .body("email", equalTo(email))
                .body("status", equalTo("PENDING_APPROVAL"))
                .extract().path("organizationId");
        Api.login(email, Accounts.PASSWORD).then()
                .statusCode(403)
                .body("errorCode", equalTo("EMAIL_NOT_CONFIRMED"));
        Accounts.verifyEmail(email).then().statusCode(200);
        Session provider = Accounts.loginSession(email, Accounts.PASSWORD);
        Api.as(provider.accessToken()).get("/api/v1/auth/me").then()
                .statusCode(200)
                .body("role", equalTo("PROVIDER"));

        String token = provider.accessToken();
        String locationId = Accounts.firstLocationId(token, organizationId);
        String resourceTypeId = Accounts.createResourceType(token, organizationId).then()
                .statusCode(201)
                .body("name", equalTo("Room"))
                .extract().path("id");
        Accounts.createResource(token, organizationId, resourceTypeId, locationId).then()
                .statusCode(201)
                .body("name", equalTo("Room 1"));
        String serviceName = "E2E Service " + UUID.randomUUID();
        String serviceId = Accounts.createService(token, organizationId, serviceName, resourceTypeId, locationId)
                .then().statusCode(201)
                .body("status", equalTo("INACTIVE"))
                .extract().path("id");
        Accounts.activateService(token, organizationId, serviceId).then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
        Accounts.createAgenda(token, organizationId, locationId, serviceId).then().statusCode(201);
        // Fully set up but not approved yet: the catalog must not list it.
        Api.anonymous().queryParam("q", serviceName).get("/api/v1/catalog/services").then()
                .statusCode(200)
                .body("items", empty());

        // Act
        Accounts.approveOrganization(Accounts.adminSession().accessToken(), organizationId).then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
        Response catalog = Api.anonymous().queryParam("q", serviceName).get("/api/v1/catalog/services");

        // Assert
        catalog.then()
                .statusCode(200)
                .body("items.find { it.serviceId == '" + serviceId + "' }.name", equalTo(serviceName));
    }
}
