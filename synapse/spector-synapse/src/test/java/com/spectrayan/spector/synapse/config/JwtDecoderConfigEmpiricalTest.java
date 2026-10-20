/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.spectrayan.spector.config.properties.AuthProperties;
import com.spectrayan.spector.config.properties.AuthProperties.JwtProperties;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Empirical Verification: JwtDecoderConfig Dual-Issuer & Adversarial Boundary Tests")
class JwtDecoderConfigEmpiricalTest {

    private static final String SECRET = "empirical-verification-secret-key-32-bytes!";
    private static final String WRONG_SECRET = "wrong-forged-secret-key-must-be-32-bytes!!";
    private static final String SUB = "USER_TSID_01948123";

    private final JwtDecoderConfig config = new JwtDecoderConfig();

    private static AuthProperties serverAuth() {
        return new AuthProperties(
                true,
                new JwtProperties(SECRET, Duration.ofHours(1)),
                null, null, null, null, null, null);
    }

    private JwtDecoder serverDecoder() {
        return config.serverJwtDecoder(serverAuth());
    }

    private static String mintHs256(String secret, String issuer, String subject,
                                    Instant expiry, Map<String, Object> extraClaims) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (issuer != null) {
            claims.issuer(issuer);
        }
        if (subject != null) {
            claims.subject(subject);
        }
        claims.issueTime(Date.from(expiry.minus(1, ChronoUnit.HOURS)));
        claims.expirationTime(Date.from(expiry));
        if (extraClaims != null) {
            extraClaims.forEach(claims::claim);
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private static Instant future() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    private static Instant past() {
        return Instant.now().minus(1, ChronoUnit.HOURS);
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtDecoderConfig.SERVER_ISSUER, JwtDecoderConfig.LEGACY_SERVER_ISSUER})
    @DisplayName("Validates tokens from both primary spector and legacy spector-synapse issuers")
    void validatesBothIssuersSuccessfully(String issuer) throws Exception {
        String token = mintHs256(SECRET, issuer, SUB, future(),
                Map.of("scope", "memory:read memory:write", "roles", List.of("USER", "RESEARCHER")));

        Jwt decoded = serverDecoder().decode(token);

        assertThat(decoded.getSubject()).isEqualTo(SUB);
        assertThat(decoded.getClaimAsString("iss")).isEqualTo(issuer);
        assertThat(decoded.getClaimAsString("scope")).isEqualTo("memory:read memory:write");
        assertThat(decoded.<List<String>>getClaim("roles")).containsExactly("USER", "RESEARCHER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cortex", "spector-cortex", "unknown-service", "https://auth.external.com", ""})
    @DisplayName("Strictly rejects tokens with unregistered or obsolete issuers")
    void rejectsInvalidIssuers(String invalidIssuer) throws Exception {
        String token = mintHs256(SECRET, invalidIssuer, SUB, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("The 'iss' claim is not valid");
    }

    @Test
    @DisplayName("Rejects token with missing issuer claim")
    void rejectsMissingIssuer() throws Exception {
        String token = mintHs256(SECRET, null, SUB, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("The 'iss' claim is not valid");
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtDecoderConfig.SERVER_ISSUER, JwtDecoderConfig.LEGACY_SERVER_ISSUER})
    @DisplayName("Rejects expired tokens for both issuers")
    void rejectsExpiredTokensForBothIssuers(String issuer) throws Exception {
        String token = mintHs256(SECRET, issuer, SUB, past(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Jwt expired");
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtDecoderConfig.SERVER_ISSUER, JwtDecoderConfig.LEGACY_SERVER_ISSUER})
    @DisplayName("Rejects tokens forged with incorrect signature secret")
    void rejectsForgedSignature(String issuer) throws Exception {
        String token = mintHs256(WRONG_SECRET, issuer, SUB, future(), Map.of());

        assertThatThrownBy(() -> serverDecoder().decode(token))
                .isInstanceOf(BadJwtException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtDecoderConfig.SERVER_ISSUER, JwtDecoderConfig.LEGACY_SERVER_ISSUER})
    @DisplayName("Rejects tokens with missing or blank subject claim")
    void rejectsMissingOrBlankSubject(String issuer) throws Exception {
        String tokenMissingSub = mintHs256(SECRET, issuer, null, future(), Map.of());
        assertThatThrownBy(() -> serverDecoder().decode(tokenMissingSub))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("The required 'sub' claim is missing");

        String tokenBlankSub = mintHs256(SECRET, issuer, "   ", future(), Map.of());
        assertThatThrownBy(() -> serverDecoder().decode(tokenBlankSub))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("The required 'sub' claim is missing");
    }

    @ParameterizedTest
    @ValueSource(strings = {JwtDecoderConfig.SERVER_ISSUER, JwtDecoderConfig.LEGACY_SERVER_ISSUER})
    @DisplayName("Resolver routes both spector and spector-synapse tokens to server manager and authenticates")
    void routingResolvesAndAuthenticatesBothIssuers(String issuer) throws Exception {
        AuthenticationManagerResolver<HttpServletRequest> resolver =
                config.jwtAuthenticationManagerResolver(serverAuth(), serverDecoder(), Optional.empty());

        String token = mintHs256(SECRET, issuer, SUB, future(),
                Map.of("scope", "memory:read", "roles", List.of("ADMIN")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthenticationManager manager = resolver.resolve(request);
        assertThat(manager).as("Manager resolved for issuer %s", issuer).isNotNull();

        Authentication auth = manager.authenticate(new BearerTokenAuthenticationToken(token));
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo(SUB);
        assertThat(auth.getAuthorities()).extracting("authority")
                .contains("SCOPE_memory:read", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Resolver rejects requests bearing tokens from unmapped issuers (e.g. cortex)")
    void routingRejectsUnmappedIssuer() throws Exception {
        AuthenticationManagerResolver<HttpServletRequest> resolver =
                config.jwtAuthenticationManagerResolver(serverAuth(), serverDecoder(), Optional.empty());

        String token = mintHs256(SECRET, "cortex", SUB, future(), Map.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthenticationManager manager = resolver.resolve(request);
        assertThat(manager).isNotNull();

        assertThatThrownBy(() -> manager.authenticate(new BearerTokenAuthenticationToken(token)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
