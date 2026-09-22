package com.codefactory.bookingplatform.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A throwaway HTTP server that publishes a JWK set, standing in for the Supabase
 * {@code /auth/v1/.well-known/jwks.json} endpoint.
 *
 * <p>It exists so the integration tests can point {@code app.security.jwks-uri} at a real URL and
 * let {@code NimbusJwtDecoder} fetch the keys over HTTP, which is the only way to exercise
 * {@code SecurityConfig.jwtDecoder()} end to end (the Spring Security test post-processors bypass
 * the decoder entirely). Uses the JDK's own {@code com.sun.net.httpserver}; no extra dependency.
 */
public final class LocalJwksServer {

    public static final String JWKS_PATH = "/auth/v1/.well-known/jwks.json";

    private final HttpServer server;
    private final TestJwks jwks;
    private final AtomicInteger requestCount = new AtomicInteger();

    private LocalJwksServer(HttpServer server, TestJwks jwks) {
        this.server = server;
        this.jwks = jwks;
    }

    public static LocalJwksServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1";
            LocalJwksServer instance = new LocalJwksServer(server, new TestJwks(issuer));
            server.createContext(JWKS_PATH, exchange -> {
                instance.requestCount.incrementAndGet();
                byte[] body = instance.jwks.jwkSetJson().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.setExecutor(null);
            server.start();
            return instance;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the local JWKS server", ex);
        }
    }

    public TestJwks jwks() {
        return jwks;
    }

    public String issuer() {
        return jwks.issuer();
    }

    public String jwksUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + JWKS_PATH;
    }

    /** How many times the decoder actually went to the network for the keys. */
    public int requestCount() {
        return requestCount.get();
    }
}
