/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.JwtProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.OidcProperties;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for {@link JwtDecoderConfig}: the server HS256 decoder, the external OIDC RS256
 * decoder, the scope/role authorities converter, and issuer-based routing.
 *
 * <p>Tokens are constructed directly with Nimbus ({@link SignedJWT} + {@link MACSigner}/
 * {@link RSASSASigner}) so the tests exercise the production decoders without depending on the
 * token-minting side. Decoders are built by instantiating {@link JwtDecoderConfig} and invoking
 * its bean factory methods with a hand-built {@link AuthProperties}.</p>
 *
 * <p><strong>OIDC RS256 coverage.</strong> A full JWKS harness <em>is</em> exercised: an in-process
 * {@link HttpServer} serves an in-memory RSA JWKS so the JWKS-backed decoder resolves the signing
 * key exactly as in production. This lets the tests assert valid-token acceptance, wrong-issuer and
 * expired rejection, and unreachable-JWKS rejection. The only coverage limitation is timing
 * precision: Requirement 4.5's exact 5-second fetch bound is validated behaviorally (an unreachable
 * endpoint surfaces as an invalid-token error) rather than by measuring the timeout duration, and
 * Requirement 3.1/4.2's 200 ms / clock-skew latency envelope is validated by correctness of the
 * skew boundary rather than by wall-clock latency assertions.</p>
 */
class JwtDecoderConfigTest {

    /** A >= 32-byte secret, the minimum required for HS256. */
    private static final String SECRET = "test-secret-that-is-long-enough-0123456789";

    /** A different >= 32-byte secret used to forge invalid-signature tokens. */
    private static final String WRONG_SECRET = "another-secret-that-is-also-long-enough-9876";

    private static final String SUB = "USER0000000AB";

    private final JwtDecoderConfig config = new JwtDecoderConfig();

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    private static AuthProperties serverAuth() {
        return new AuthProperties(
                true,
                new JwtProperties(SECRET, Duration.ofHours(1)),
                null, null, null, null, null, null);
    }

    /** Builds a server HS256 decoder from a fresh {@link JwtDecoderConfig}. */
    private JwtDecoder serverDecoder() {
        return config.serverJwtDecoder(serverAuth());
    }

    /**
     * Mints an HS256 token signed with {@code secret}. A {@code null} issuer/subject is omitted so
     * the missing-claim paths can be exercised.
     */
    private static String mintHs256(String secret, String issuer, String subject,
                                    Instant expiry, Map<String, Object> extraClaims) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (subject != null) {
            claims.subject(subject);
        }
        claims.issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        claims.expirationTime(Date.from(expiry));
        extraClaims.forEach(claims::claim);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    /** Mints an RS256 token signed with {@code rsaKey}, embedding its key id in the header. */
    private static String mintRs256(RSAKey rsaKey, String issuer, String subject,
                                    Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiry))
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private static Instant future() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    // ── Server HS256: happy path + authorities converter (Reqs 3.1, 3.2) ────────────────────────

    @Test
    @DisplayName("Server HS256: a valid token decodes and the converter yields SCOPE_/ROLE_ authorities with principal=sub")
    void serverDecoderDecodesValidTokenAndConverterMapsAuthorities() throws Exception {
        String token = mintHs256(SECRET, JwtDecoderConfig.SERVER_ISSUER, SUB, future(),
                Map.of("scope", "memory:read memory:write", "roles", List.of("USER", "ADMIN")));

        Jwt decoded = serverDecoder().decode(token);

        assertThat(decoded.getSubject()).isEqualTo(SUB);
        // SERVER_ISSUER ("spector-synapse") is not a URL, so read the raw string claim rather
        // than getIssuer() which coerces to java.net.URL.
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(JwtDecoderConfig.SERVER_ISSUER);

        JwtAuthenticationConverter converter = config.serverJwtAuthenticationConverter();
        Authentication auth = converter.convert(decoded);

        assertThat(auth.getName()).isEqualTo(SUB);
        // The converter unions SCOPE_/ROLE_ authorities; Spring Security additionally attaches a
        // FACTOR_BEARER authority to the resulting token, which we don't assert on.
        assertThat(auth.getAuthorities()).extracting("authority")
                .contains("SCOPE_memory:read", "SCOPE_memory:write", "ROLE_USER", "ROLE_ADMIN");
    }

    // ── Server HS256: invalid signature (Reqs 3.3, 3.5) ─────────────────────────────────────────

    @Test
    @DisplayName("Server HS256: a token signed with the wrong secret fails to decode")
    void serverDecoderRejectsInvalidSignature() throws Exception {
        String token = mintHs256(WRONG_SECRET, JwtDecoderConfig.SERVER_ISSUER, SUB, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    // ── Server HS256: expiry + clock skew (Req 3.4) ─────────────────────────────────────────────

    @Test
    @DisplayName("Server HS256: a token expired beyond the 60s skew is rejected")
    void serverDecoderRejectsExpiredTokenBeyondSkew() throws Exception {
        String token = mintHs256(SECRET, JwtDecoderConfig.SERVER_ISSUER, SUB,
                Instant.now().minus(120, ChronoUnit.SECONDS), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Server HS256: a token expired within the 60s skew is still accepted")
    void serverDecoderAcceptsTokenExpiredWithinSkew() throws Exception {
        String token = mintHs256(SECRET, JwtDecoderConfig.SERVER_ISSUER, SUB,
                Instant.now().minus(30, ChronoUnit.SECONDS), Map.of());

        Jwt decoded = serverDecoder().decode(token);

        assertThat(decoded.getSubject()).isEqualTo(SUB);
    }

    // ── Server HS256: malformed token (Req 3.5) ─────────────────────────────────────────────────

    @Test
    @DisplayName("Server HS256: a malformed token cannot be parsed and is rejected")
    void serverDecoderRejectsMalformedToken() {
        assertThatThrownBy(() -> serverDecoder().decode("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    // ── Server HS256: missing sub (Req 3.6) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Server HS256: a token missing the sub claim is rejected")
    void serverDecoderRejectsMissingSubject() throws Exception {
        String token = mintHs256(SECRET, JwtDecoderConfig.SERVER_ISSUER, null, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    // ── Server HS256: wrong issuer (Req 3.3) ────────────────────────────────────────────────────

    @Test
    @DisplayName("Server HS256: a token with a foreign issuer is rejected")
    void serverDecoderRejectsWrongIssuer() throws Exception {
        String token = mintHs256(SECRET, "https://evil.example.com", SUB, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    // ── Issuer routing (Reqs 4.3, 4.4) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Routing: the SERVER_ISSUER token resolves the server manager and authenticates")
    void routingSelectsServerManagerForServerIssuer() throws Exception {
        AuthenticationManagerResolver<HttpServletRequest> resolver =
                config.jwtAuthenticationManagerResolver(serverAuth(), serverDecoder(), Optional.empty());

        String token = mintHs256(SECRET, JwtDecoderConfig.SERVER_ISSUER, SUB, future(),
                Map.of("scope", "memory:read", "roles", List.of("USER")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthenticationManager manager = resolver.resolve(request);
        assertThat(manager).isNotNull();

        Authentication result = manager.authenticate(new BearerTokenAuthenticationToken(token));
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(SUB);
        // The scope/role converter contributes SCOPE_/ROLE_ authorities; Spring Security also
        // attaches a FACTOR_BEARER authority to the resulting token, which we don't assert on.
        assertThat(result.getAuthorities()).extracting("authority")
                .contains("SCOPE_memory:read", "ROLE_USER");
    }

    @Test
    @DisplayName("Routing: a token with an unregistered issuer is rejected")
    void routingRejectsUnknownIssuer() throws Exception {
        AuthenticationManagerResolver<HttpServletRequest> resolver =
                config.jwtAuthenticationManagerResolver(serverAuth(), serverDecoder(), Optional.empty());

        String token = mintHs256(SECRET, "https://unknown-issuer.example.com", SUB, future(), Map.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        // The issuer-routing resolver defers the issuer lookup to authentication time; an
        // unregistered issuer then fails as an invalid-token error, establishing no session
        // (Requirement 4.4).
        AuthenticationManager manager = resolver.resolve(request);
        assertThat(manager).isNotNull();
        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken(token)))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }

    // ── External OIDC RS256 via a live in-process JWKS (Reqs 4.2, 4.3, 4.4, 4.5, 4.6, 4.7) ──────

    @Nested
    @DisplayName("OIDC RS256 decoder (JWKS-backed)")
    class OidcRs256 {

        private static final String OIDC_ISSUER = "https://idp.example.com/";

        private RSAKey rsaKey;
        private HttpServer jwksServer;
        private String jwksUrl;

        @BeforeEach
        void startJwks() throws Exception {
            rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
            String jwksJson = new JWKSet(rsaKey.toPublicJWK()).toString();

            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwksServer.createContext("/jwks", exchange -> {
                byte[] body = jwksJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            jwksServer.start();
            jwksUrl = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
        }

        @AfterEach
        void stopJwks() {
            if (jwksServer != null) {
                jwksServer.stop(0);
            }
        }

        private AuthProperties oidcAuth(String jwksUrlValue) {
            return new AuthProperties(
                    true,
                    new JwtProperties(SECRET, Duration.ofHours(1)),
                    null,
                    new OidcProperties(jwksUrlValue, OIDC_ISSUER),
                    null, null, null, null);
        }

        private JwtDecoder oidcDecoder(String jwksUrlValue) {
            return config.oidcJwtDecoder(oidcAuth(jwksUrlValue));
        }

        @Test
        @DisplayName("accepts a valid RS256 token whose signing key is served by the JWKS (Reqs 4.2, 4.6)")
        void acceptsValidRs256Token() throws Exception {
            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB, future());

            Jwt decoded = oidcDecoder(jwksUrl).decode(token);

            assertThat(decoded.getSubject()).isEqualTo(SUB);
            assertThat(decoded.getIssuer()).hasToString(OIDC_ISSUER);
        }

        @Test
        @DisplayName("rejects an RS256 token whose issuer is not oidc.issuer (Req 4.4)")
        void rejectsWrongIssuer() throws Exception {
            String token = mintRs256(rsaKey, "https://other-idp.example.com/", SUB, future());

            assertThatThrownBy(() -> oidcDecoder(jwksUrl).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("rejects an RS256 token expired beyond the 60s skew (Req 4.7)")
        void rejectsExpiredToken() throws Exception {
            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB,
                    Instant.now().minus(120, ChronoUnit.SECONDS));

            assertThatThrownBy(() -> oidcDecoder(jwksUrl).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("rejects an RS256 token signed by a key absent from the JWKS (Req 4.6)")
        void rejectsUnknownSigningKey() throws Exception {
            RSAKey foreignKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
            String token = mintRs256(foreignKey, OIDC_ISSUER, SUB, future());

            assertThatThrownBy(() -> oidcDecoder(jwksUrl).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("rejects a token when the JWKS endpoint is unreachable (Req 4.5)")
        void rejectsWhenJwksUnreachable() throws Exception {
            // Bind a server to claim a port, then stop it so the address refuses connections.
            HttpServer dead = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int deadPort = dead.getAddress().getPort();
            dead.start();
            dead.stop(0);
            String unreachable = "http://127.0.0.1:" + deadPort + "/jwks";

            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB, future());

            assertThatThrownBy(() -> oidcDecoder(unreachable).decode(token))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("routing selects the OIDC manager for the OIDC issuer and authenticates (Req 4.3)")
        void routingSelectsOidcManagerForOidcIssuer() throws Exception {
            AuthProperties auth = oidcAuth(jwksUrl);
            AuthenticationManagerResolver<HttpServletRequest> resolver =
                    config.jwtAuthenticationManagerResolver(
                            auth, serverDecoder(), Optional.of(oidcDecoder(jwksUrl)));

            String token = mintRs256(rsaKey, OIDC_ISSUER, SUB, future());

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer " + token);

            AuthenticationManager manager = resolver.resolve(request);
            assertThat(manager).isNotNull();

            Authentication result = manager.authenticate(new BearerTokenAuthenticationToken(token));
            assertThat(result.isAuthenticated()).isTrue();
            assertThat(result.getName()).isEqualTo(SUB);
        }
    }
}
