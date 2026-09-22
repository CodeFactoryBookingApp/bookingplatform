package com.codefactory.bookingplatform.startup;

import com.codefactory.bookingplatform.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application running for real: an embedded Tomcat on a random port, a
 * PostgreSQL in Docker behind it and plain HTTP requests from outside the
 * container. Everything else in this package inspects the context; this class
 * checks that the process actually serves traffic, which is the question the
 * demo answers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationStartsIT extends PostgresIntegrationTestBase {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("The embedded server is listening on a real port")
    void serverIsListening() {
        assertTrue(port > 0);
    }

    @Test
    @DisplayName("GET /actuator/health answers 200 UP over real HTTP")
    void healthAnswersOverHttp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    @DisplayName("GET /actuator/health/readiness, the path render.yaml polls, answers 200 over real HTTP")
    void readinessAnswersOverHttp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    @DisplayName("The OpenAPI contract is served publicly, so Swagger UI works without a token")
    void openApiContractIsPublic() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Booking Platform API"), response.body());
    }

    @Test
    @DisplayName("A public endpoint is reachable without a token: a malformed registration gets 400, not 401")
    void publicEndpointAnswersWithoutToken() throws Exception {
        HttpResponse<String> response = postJson("/api/v1/registrations", "{}");

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("VALIDATION_ERROR"), response.body());
    }

    @Test
    @DisplayName("A protected endpoint answers 401 AUTH_REQUIRED when no token is sent")
    void protectedEndpointRequiresAToken() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/me");

        assertEquals(401, response.statusCode(), response.body());
        assertTrue(response.body().contains("AUTH_REQUIRED"), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/problem+json"), response.headers().toString());
    }

    @Test
    @DisplayName("Every error response carries the X-Trace-Id header used to correlate the logs")
    void errorsCarryATraceId() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/me");

        assertTrue(response.headers().firstValue("X-Trace-Id").isPresent(), response.headers().toString());
    }
}
