package com.codefactory.bookingplatform.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal JWKS/JWT toolbox for the integration tests (HU-021).
 *
 * <p>The production {@code SecurityConfig.jwtDecoder()} validates the signature against the
 * JWKS published by Supabase and then checks {@code exp}/{@code nbf} and {@code iss}. Tests that
 * want to exercise that decoder for real need two keys: one published in the JWK set (so tokens
 * signed with it verify) and one kept out of it (so tokens signed with it fail the signature
 * check exactly like a forged token would).
 */
public final class TestJwks {

    public static final String KEY_ID = "bookingplatform-test-key";

    private final RSAKey publishedKey;
    private final RSAKey strangerKey;
    private final String issuer;

    public TestJwks(String issuer) {
        this.issuer = issuer;
        try {
            this.publishedKey = new RSAKeyGenerator(2048)
                    .keyID(KEY_ID)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            this.strangerKey = new RSAKeyGenerator(2048)
                    .keyID(KEY_ID) // same kid on purpose: only the signature tells them apart
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not generate the test RSA keys", ex);
        }
    }

    public String issuer() {
        return issuer;
    }

    /** The public JWK set, exactly as the identity provider would publish it. */
    public String jwkSetJson() {
        return new JWKSet(publishedKey.toPublicJWK()).toString();
    }

    /** A well formed, currently valid access token for the given user. */
    public String accessToken(UUID userId, String email, String role) {
        return sign(publishedKey, claims(userId, email, role, issuer, Instant.now().minusSeconds(5),
                Instant.now().plus(Duration.ofMinutes(15))));
    }

    /** Valid signature and issuer, but already expired: must fail {@code JwtTimestampValidator}. */
    public String expiredAccessToken(UUID userId, String email, String role) {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        return sign(publishedKey, claims(userId, email, role, issuer, issuedAt, issuedAt.plus(Duration.ofMinutes(15))));
    }

    /** Valid signature, but minted by a different issuer: must fail {@code JwtIssuerValidator}. */
    public String tokenFromAnotherIssuer(UUID userId, String email, String role) {
        return sign(publishedKey, claims(userId, email, role, "https://evil.example.com/auth/v1",
                Instant.now().minusSeconds(5), Instant.now().plus(Duration.ofMinutes(15))));
    }

    /** Right claims and right {@code kid}, signed with a key that is not in the JWK set. */
    public String tokenWithForgedSignature(UUID userId, String email, String role) {
        return sign(strangerKey, claims(userId, email, role, issuer, Instant.now().minusSeconds(5),
                Instant.now().plus(Duration.ofMinutes(15))));
    }

    private static JWTClaimsSet claims(UUID userId, String email, String role, String issuer,
                                       Instant issuedAt, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience("authenticated")
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("email", email)
                .claim("role", "authenticated")
                .claim("app_metadata", Map.of("role", role))
                .claim("user_metadata", Map.of("role", "ADMIN")) // must be ignored by the converter
                .build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign the test token", ex);
        }
    }
}
