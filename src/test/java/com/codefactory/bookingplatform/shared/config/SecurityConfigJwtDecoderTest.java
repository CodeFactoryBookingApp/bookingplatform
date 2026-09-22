package com.codefactory.bookingplatform.shared.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The JWT decoder is the single gate that turns a bearer string into an authenticated caller, so it
 * is exercised against a real JWKS endpoint and real signatures rather than against a mock.
 *
 * <p>Four rules are pinned here, and all four are invisible to a test that only checks that the
 * bean is a {@code NimbusJwtDecoder}: the two signature algorithms Supabase issues (ES256 today,
 * RS256 for legacy projects) are both accepted, anything else is refused, expired tokens are
 * refused, and tokens minted by a different issuer are refused.</p>
 */
class SecurityConfigJwtDecoderTest {

    private static final String ISSUER = "https://demo.supabase.co/auth/v1";
    private static final String SUBJECT = "11111111-2222-3333-4444-555555555555";

    private static HttpServer jwksServer;
    private static ECKey ecKey;
    private static RSAKey rsaKey;
    private static String jwksUri;

    /**
     * Built per test on purpose. A decoder built once in {@code @BeforeAll} is only ever attributed
     * to whichever test happens to run first, which hides the bean factory from any per-test
     * analysis; building it here means every rule below exercises the factory itself.
     */
    private final JwtDecoder decoder = new SecurityConfig(
            new SecurityProperties(ISSUER, jwksUri), new tools.jackson.databind.ObjectMapper()).jwtDecoder();

    @BeforeAll
    static void startJwksEndpoint() throws Exception {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
        rsaKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        byte[] jwks = new JWKSet(java.util.List.of(ecKey.toPublicJWK(), rsaKey.toPublicJWK()))
                .toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();

        jwksUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks.json";
    }

    @AfterAll
    static void stopJwksEndpoint() {
        jwksServer.stop(0);
    }

    private static JWTClaimsSet claims(String issuer, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .claim("email", "ana.perez@example.com")
                .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String signedWithEc(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKey.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new ECDSASigner(ecKey));
        return jwt.serialize();
    }

    private static String signedWithRsa(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("An ES256 token signed by the project keys is accepted: that is what Supabase issues today")
    void es256TokenIsAccepted() throws Exception {
        Jwt jwt = decoder.decode(signedWithEc(claims(ISSUER, Instant.now().plusSeconds(3600))));

        assertEquals(SUBJECT, jwt.getSubject());
        assertEquals("ana.perez@example.com", jwt.getClaimAsString("email"));
    }

    @Test
    @DisplayName("An RS256 token signed by the project keys is accepted: legacy Supabase projects still sign that way")
    void rs256TokenIsAccepted() throws Exception {
        Jwt jwt = decoder.decode(signedWithRsa(claims(ISSUER, Instant.now().plusSeconds(3600))));

        assertEquals(SUBJECT, jwt.getSubject());
    }

    @Test
    @DisplayName("An expired token is refused, so a leaked token stops working when it lapses")
    void expiredTokenIsRefused() throws Exception {
        String expired = signedWithEc(claims(ISSUER, Instant.now().minusSeconds(600)));

        assertThrows(JwtException.class, () -> decoder.decode(expired));
    }

    @Test
    @DisplayName("An expired RS256 token is refused as well, so the second algorithm is validated too")
    void expiredRsaTokenIsRefused() throws Exception {
        String expired = signedWithRsa(claims(ISSUER, Instant.now().minusSeconds(600)));

        assertThrows(JwtException.class, () -> decoder.decode(expired));
    }

    @Test
    @DisplayName("SECURITY: a token minted by another issuer is refused even if the signature checks out")
    void foreignIssuerIsRefused() throws Exception {
        String foreign = signedWithEc(claims("https://attacker.example.com/auth/v1", Instant.now().plusSeconds(3600)));

        assertThrows(JwtException.class, () -> decoder.decode(foreign));
    }

    @Test
    @DisplayName("SECURITY: a token with no issuer claim at all is refused")
    void missingIssuerIsRefused() throws Exception {
        String noIssuer = signedWithEc(claims(null, Instant.now().plusSeconds(3600)));

        assertThrows(JwtException.class, () -> decoder.decode(noIssuer));
    }

    @Test
    @DisplayName("SECURITY: an HS256 token is refused, so a caller cannot sign with a guessed shared secret")
    void symmetricallySignedTokenIsRefused() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
                claims(ISSUER, Instant.now().plusSeconds(3600)));
        jwt.sign(new MACSigner("0123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8)));
        String symmetric = jwt.serialize();

        assertThrows(JwtException.class, () -> decoder.decode(symmetric));
    }
}
