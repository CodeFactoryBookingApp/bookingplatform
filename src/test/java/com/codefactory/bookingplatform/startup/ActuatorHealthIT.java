package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the actuator actually exposes, since Render polls it to decide whether a
 * deploy is alive. Two things are pinned here: which endpoints answer without
 * a token and how much they reveal. That readiness stays UP with the database
 * down is a known defect (BUG-012) and is not pinned as expected behaviour.
 */
class ActuatorHealthIT extends PostgresIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Public health endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("/actuator/health hides its components, so the database host never leaks to an anonymous caller")
        void healthShowsNoDetails() throws Exception {
            // Act
            ResultActions result = mockMvc.perform(get("/actuator/health"));

            // Assert
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(content().json(
                            "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}", true));
        }

        @ParameterizedTest(name = "{0} answers 200 without a token")
        @ValueSource(strings = {"/actuator/health/readiness", "/actuator/health/liveness"})
        @DisplayName("Both availability probes are public and up, readiness being the one Render polls")
        void probesArePublic(String path) throws Exception {
            // Act
            ResultActions result = mockMvc.perform(get(path));

            // Assert
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("/actuator/info is public as well")
        void infoIsPublic() throws Exception {
            // Act
            ResultActions result = mockMvc.perform(get("/actuator/info"));

            // Assert
            result.andExpect(status().isOk());
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
            // Act
            ResultActions result = mockMvc.perform(get(path));

            // Assert
            result.andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
        }
    }
}
