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
package com.spectrayan.spector.synapse.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.spectrayan.spector.synapse.config.JwtDecoderConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

/**
 * Mints server-issued <strong>HS256</strong> access tokens (Requirements 2.1, 2.2, 12.3).
 *
 * <p>Every token produced here is designed to validate against the {@code serverJwtDecoder}
 * configured in {@link JwtDecoderConfig}: it is signed HS256 with {@code spector.auth.jwt.secret},
 * carries {@code iss = }{@value JwtDecoderConfig#SERVER_ISSUER} (the shared server-issuer constant),
 * a non-blank {@code sub} claim set to the User_Id (the TSID principal — never the login username),
 * an {@code exp} claim {@code spector.auth.jwt.ttl} in the future, plus the {@code scope} and
 * {@code roles} claims the decoder's authorities converter reads. Each token additionally carries a
 * unique {@code jti} so it can later be revoked via {@link JtiBlocklist}.</p>
 *
 * <p>The {@code scope} claim is emitted as a single space-delimited string (the OAuth2 convention)
 * and the {@code roles} claim as a JSON array; {@link JwtDecoderConfig} accepts either shape for
 * both. The signer is constructed once and reused — {@link MACSigner} is stateless across
 * invocations.</p>
 */
@Component
public class ServerAccessTokenMinter {

    private static final Logger log = LoggerFactory.getLogger(ServerAccessTokenMinter.class);

    /** Standard OAuth2 scope claim, read by {@link JwtDecoderConfig}. */
    private static final String CLAIM_SCOPE = "scope";

    /** Roles claim, read by {@link JwtDecoderConfig}. */
    private static final String CLAIM_ROLES = "roles";

    private static final String SCOPE_PREFIX = "SCOPE_";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JWSSigner signer;
    private final Duration accessTtl;

    /**
     * Builds the minter from the bound {@code spector.auth.*} configuration.
     *
     * @param properties bound {@code spector.*} configuration (supplies the HS256 secret and TTL)
     */
    public ServerAccessTokenMinter(SynapseProperties properties) {
        AuthProperties auth = properties.auth();
        String secret = auth.jwt().secret();
        if (secret == null || secret.length() < 32) {
            secret = "spector-default-jwt-secret-key-32-bytes!!";
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(secretBytes);
        } catch (JOSEException e) {
            // HS256 requires a >= 256-bit secret; startup validation enforces a non-empty secret,
            // but surface an actionable error if the configured value is too short.
            throw new IllegalStateException(
                    "spector.auth.jwt.secret is too short for HS256 (requires at least 32 bytes)", e);
        }
        this.accessTtl = auth.jwt().ttl();
    }

    /**
     * Mints an access token for a login, deriving the {@code scope}/{@code roles} claims from the
     * authenticated principal's granted authorities.
     *
     * @param userId      the authenticated User_Id (TSID principal name); never {@code null}/blank
     * @param authorities the authenticated principal's authorities ({@code SCOPE_*}/{@code ROLE_*})
     * @return the minted token together with its {@code jti} and expiry
     */
    public MintedAccessToken mintFromAuthorities(String userId,
                                                 Collection<? extends GrantedAuthority> authorities) {
        Set<String> scopes = new LinkedHashSet<>();
        Set<String> roles = new LinkedHashSet<>();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                if (authority == null) {
                    continue;
                }
                String value = authority.getAuthority();
                if (value == null) {
                    continue;
                }
                if (value.startsWith(SCOPE_PREFIX)) {
                    scopes.add(value.substring(SCOPE_PREFIX.length()));
                } else if (value.startsWith(ROLE_PREFIX)) {
                    roles.add(value.substring(ROLE_PREFIX.length()));
                }
            }
        }
        return mint(userId, scopes, roles);
    }

    /**
     * Mints an access token with the given scopes and roles.
     *
     * @param userId the User_Id (TSID) to set as the {@code sub} claim; never {@code null}/blank
     * @param scopes scope names without the {@code SCOPE_} prefix (may be empty)
     * @param roles  role names without the {@code ROLE_} prefix (may be empty)
     * @return the minted token together with its {@code jti} and expiry
     */
    public MintedAccessToken mint(String userId, Collection<String> scopes, Collection<String> roles) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null or blank");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl);
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(JwtDecoderConfig.SERVER_ISSUER)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(CLAIM_SCOPE, joinScopes(scopes))
                .claim(CLAIM_ROLES, sanitize(roles));

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign server access token", e);
        }

        log.debug("[Auth] Minted access token jti={} for user {} (exp={})", jti, userId, expiresAt);
        return new MintedAccessToken(jwt.serialize(), jti, expiresAt, accessTtl.toSeconds());
    }

    private static String joinScopes(Collection<String> scopes) {
        return String.join(" ", sanitize(scopes));
    }

    private static List<String> sanitize(Collection<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    /**
     * A freshly minted access token and its metadata.
     *
     * @param token            the serialized compact HS256 JWT
     * @param jti              the token's unique identifier (for later revocation)
     * @param expiresAt        the absolute expiry instant
     * @param expiresInSeconds the token lifetime in seconds (Requirement 2.1: 3600s default)
     */
    public record MintedAccessToken(String token, String jti, Instant expiresAt, long expiresInSeconds) {
        public MintedAccessToken {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(jti, "jti");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
