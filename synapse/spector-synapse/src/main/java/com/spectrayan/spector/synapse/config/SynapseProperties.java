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

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for Spector Synapse.
 *
 * <p>All properties are bound from {@code spector.*} in {@code application.yml}
 * and can be overridden via environment variables (e.g., {@code SPECTOR_PORT=8080}).</p>
 *
 * @param port        HTTP port for the Synapse server (default: 7070)
 * @param apiKey      API key for authenticating REST requests
 * @param dataDir     directory for persistent data (H2 database, indexes)
 * @param ollama      Ollama LLM provider configuration
 * @param memory      cognitive memory engine configuration
 * @param cors        CORS configuration for the Cortex UI
 * @param auth        multi-user authentication configuration ({@code spector.auth.*})
 */
@ConfigurationProperties(prefix = "spector")
public record SynapseProperties(
        int port,
        String apiKey,
        String dataDir,
        OllamaProperties ollama,
        MemoryProperties memory,
        CorsProperties cors,
        AuthProperties auth
) {

    /**
     * Provides defaults for unset properties.
     */
    public SynapseProperties {
        if (port <= 0) port = 7070;
        if (apiKey == null || apiKey.isBlank()) apiKey = "spector-dev-key";
        if (dataDir == null || dataDir.isBlank()) dataDir = "./spector-data";
        if (ollama == null) ollama = new OllamaProperties(null, null, null);
        if (memory == null) memory = new MemoryProperties(0, 0, null);

        if (cors == null) cors = new CorsProperties(null);
        if (auth == null) auth = new AuthProperties(false, null, null, null, null, null, null, null);
    }

    /**
     * Ollama LLM provider settings.
     *
     * @param baseUrl  Ollama server URL (default: http://localhost:11434)
     * @param model    default model for chat (default: llama3.2)
     * @param embedModel  model for embeddings (default: nomic-embed-text)
     */
    public record OllamaProperties(String baseUrl, String model, String embedModel) {
        public OllamaProperties {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:11434";
            if (model == null || model.isBlank()) model = "llama3.2";
            if (embedModel == null || embedModel.isBlank()) embedModel = "nomic-embed-text";
        }
    }

    /**
     * Memory engine settings.
     *
     * @param maxMemories   maximum number of memories (0 = unlimited)
     * @param dimensions    embedding vector dimensions (0 = auto-detect from provider)
     * @param consolidation consolidation settings
     */
    public record MemoryProperties(int maxMemories, int dimensions, ConsolidationProperties consolidation) {
        public MemoryProperties {
            if (maxMemories < 0) maxMemories = 0;
            if (dimensions < 0) dimensions = 0;
            if (consolidation == null) consolidation = new ConsolidationProperties(21600000L); // 6 hours
        }

        public MemoryProperties(int maxMemories, int dimensions) {
            this(maxMemories, dimensions, new ConsolidationProperties(21600000L));
        }
    }


    /**
     * Consolidation settings.
     *
     * @param interval consolidation run interval in milliseconds (default: 21600000 ms = 6 hours)
     */
    public record ConsolidationProperties(long interval) {
        public ConsolidationProperties {
            if (interval <= 0) interval = 21600000L;
        }
    }


    /**
     * CORS settings for the Cortex UI.
     *
     * @param allowedOrigins  comma-separated origins (default: http://localhost:4200)
     */
    public record CorsProperties(String allowedOrigins) {
        public CorsProperties {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                allowedOrigins = "http://localhost:4200";
            }
        }
    }

    /**
     * Multi-user authentication settings, bound from {@code spector.auth.*}.
     *
     * <p>The feature is <strong>off by default</strong> ({@code enabled=false}), preserving the
     * legacy single shared memory and single shared-key {@code ROLE_API} behavior. Secrets
     * ({@code jwt.secret}, {@code default-admin.password}) are sourced from environment variables
     * via {@code ${env:VAR}} placeholders and MUST NOT be committed as literals. There are
     * <strong>no tenant-scoped keys</strong> — Spector OSS is single-tenant, multi-user.</p>
     *
     * <p>Only documented defaults are applied here. Strict startup validation (empty required
     * secrets while enabled, out-of-range numeric values) is enforced separately during startup.</p>
     *
     * @param enabled      master switch; {@code false} preserves legacy single-user behavior (default: false)
     * @param jwt          server-issued HS256 token settings
     * @param refresh      refresh-token settings
     * @param oidc         external OIDC (RS256/JWKS) settings; disabled when {@code jwks-url} is empty
     * @param defaultAdmin seed administrator account settings
     * @param pbkdf2       password-encoder iteration settings (default: 310000)
     * @param lockout      account-lockout settings (default: 5 attempts / 15 minutes)
     * @param publicPaths  request paths that bypass authentication (default: /actuator/health, /api/docs)
     */
    public record AuthProperties(
            boolean enabled,
            JwtProperties jwt,
            RefreshProperties refresh,
            OidcProperties oidc,
            DefaultAdminProperties defaultAdmin,
            Pbkdf2Properties pbkdf2,
            LockoutProperties lockout,
            List<String> publicPaths
    ) {
        public AuthProperties {
            if (jwt == null) jwt = new JwtProperties(null, null);
            if (refresh == null) refresh = new RefreshProperties(null);
            if (oidc == null) oidc = new OidcProperties(null, null);
            if (defaultAdmin == null) defaultAdmin = new DefaultAdminProperties(null);
            if (pbkdf2 == null) pbkdf2 = new Pbkdf2Properties(0);
            if (lockout == null) lockout = new LockoutProperties(0, 0);
            if (publicPaths == null || publicPaths.isEmpty()) {
                publicPaths = List.of("/actuator/health", "/api/docs");
            } else {
                publicPaths = List.copyOf(publicPaths);
            }
        }
    }

    /**
     * Server-issued HS256 JWT settings.
     *
     * @param secret HS256 signing/validation secret; supplied via {@code ${env:SPECTOR_AUTH_JWT_SECRET}}
     * @param ttl    access-token lifetime (default: 1 hour)
     */
    public record JwtProperties(String secret, Duration ttl) {
        public JwtProperties {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) ttl = Duration.ofHours(1);
        }
    }

    /**
     * Refresh-token settings.
     *
     * @param ttl refresh-token lifetime (default: 30 days)
     */
    public record RefreshProperties(Duration ttl) {
        public RefreshProperties {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) ttl = Duration.ofDays(30);
        }
    }

    /**
     * External OIDC (RS256 via JWKS) settings. RS256 validation is enabled only when
     * {@code jwksUrl} is non-empty.
     *
     * @param jwksUrl external IdP JWKS endpoint (default: empty — OIDC disabled)
     * @param issuer  expected {@code iss} claim for external OIDC tokens (default: empty)
     */
    public record OidcProperties(String jwksUrl, String issuer) {
        public OidcProperties {
            if (jwksUrl == null) jwksUrl = "";
            if (issuer == null) issuer = "";
        }
    }

    /**
     * Seed administrator account settings.
     *
     * @param password seed admin password; supplied via {@code ${env:SPECTOR_ADMIN_PASSWORD}}
     */
    public record DefaultAdminProperties(String password) {
    }

    /**
     * {@code Pbkdf2PasswordEncoder} iteration settings.
     *
     * @param iterations PBKDF2 iteration count (default: 310000, OWASP-2024 baseline)
     */
    public record Pbkdf2Properties(int iterations) {
        public Pbkdf2Properties {
            if (iterations < 1) iterations = 310_000;
        }
    }

    /**
     * Account-lockout settings.
     *
     * @param maxAttempts consecutive failures before lockout (default: 5)
     * @param minutes     lockout duration in minutes (default: 15)
     */
    public record LockoutProperties(int maxAttempts, int minutes) {
        public LockoutProperties {
            if (maxAttempts < 1) maxAttempts = 5;
            if (minutes < 1) minutes = 15;
        }
    }
}
