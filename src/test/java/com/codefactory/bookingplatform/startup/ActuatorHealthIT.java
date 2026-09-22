package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the actuator actually exposes, since Render polls it to decide whether a
 * deploy is alive. Three things are pinned here: which endpoints answer without
 * a token, how much they reveal, and what the readiness group is made of.
 */
class ActuatorHealthIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Nested
    @DisplayName("Public health endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("/actuator/health answers 200 UP without any token")
        void healthIsPublicAndUp() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("/actuator/health hides its components, so the database host never leaks to an anonymous caller")
        void healthShowsNoDetails() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(content().json(
                            "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}", true));
        }

        @ParameterizedTest(name = "{0} answers 200 without a token")
        @ValueSource(strings = {"/actuator/health/readiness", "/actuator/health/liveness"})
        @DisplayName("Both availability probes are public and up, readiness being the one Render polls")
        void probesArePublic(String path) throws Exception {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("/actuator/info is public as well")
        void infoIsPublic() throws Exception {
            mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Everything else on the actuator stays closed")
    class ClosedEndpoints {

        @ParameterizedTest(name = "{0} is refused with 401 to an anonymous caller")
        @ValueSource(strings = {"/actuator", "/actuator/beans", "/actuator/env", "/actuator/metrics",
                "/actuator/configprops", "/actuator/loggers", "/actuator/threaddump"})
        @DisplayName("The unexposed endpoints are behind authentication, not merely unmapped")
        void unexposedEndpointsRequireAuthentication(String path) throws Exception {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
        }
    }

    @Nested
    @DisplayName("Composition of the health groups")
    class HealthGroups {

        @Test
        @DisplayName("The liveness and readiness groups exist, which is what enables /actuator/health/<group>")
        void probeGroupsExist() {
            HealthEndpointGroups groups = context.getBean(HealthEndpointGroups.class);

            assertTrue(groups.getNames().contains("liveness"));
            assertTrue(groups.getNames().contains("readiness"));
        }

        @Test
        @DisplayName("RISK: the readiness group does not include db, so Render sees UP even with the database down")
        void readinessIgnoresTheDatabase() {
            HealthEndpointGroup readiness = context.getBean(HealthEndpointGroups.class).get("readiness");

            assertNotNull(readiness);
            assertFalse(readiness.isMember("db"),
                    "healthCheckPath is /actuator/health/readiness, which only reflects the application "
                            + "availability state, never the datasource");
        }

        @Test
        @DisplayName("The database contributor is registered under db and is the JDBC one")
        void databaseContributorIsRegistered() {
            HealthIndicator indicator = (HealthIndicator) context.getBean("dbHealthContributor");

            assertInstanceOf(DataSourceHealthIndicator.class, indicator);
            assertEquals(Status.UP, indicator.health().getStatus());
        }
    }
}
