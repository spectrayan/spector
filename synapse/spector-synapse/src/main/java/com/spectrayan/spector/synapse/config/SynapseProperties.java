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

import com.spectrayan.spector.config.AuthConfig;
import com.spectrayan.spector.config.CorsConfig;
import com.spectrayan.spector.config.MemoryConfig;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.List;

/**
 * Externalized configuration for Spector Synapse server application.
 *
 * <p>Extends {@link SpectorConfigProperties} (Tier 2 Spring Starter properties)
 * to inherit {@code memory}, {@code embedding}, {@code metrics}, and {@code client}
 * configurations with zero field duplication.</p>
 *
 * <p>Adds Synapse server application settings: {@code port}, {@code apiKey},
 * {@code dataDir}, {@code cors}, and {@code auth}.</p>
 */
@Primary
@ConfigurationProperties(prefix = "spector")
public class SynapseProperties extends SpectorConfigProperties {

    private int port = 7070;
    private String apiKey = "spector-dev-key";
    private String dataDir = "./spector-data";
    private OllamaProperties ollama = new OllamaProperties(null, null, null);
    private CorsProperties cors = new CorsProperties();
    private AuthProperties auth = new AuthProperties();

    public SynapseProperties() {}

    public SynapseProperties(
            int port,
            String apiKey,
            String dataDir,
            OllamaProperties ollama,
            MemoryConfig memory,
            CorsConfig cors,
            AuthConfig auth
    ) {
        if (port > 0) this.port = port;
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (dataDir != null && !dataDir.isBlank()) this.dataDir = dataDir;
        if (ollama != null) this.ollama = ollama;
        if (memory != null) setMemory(memory);
        if (cors != null) {
            this.cors = (cors instanceof CorsProperties cp) ? cp : new CorsProperties(cors.getAllowedOrigins());
        }
        if (auth != null) {
            if (auth instanceof AuthProperties ap) {
                this.auth = ap;
            } else {
                this.auth = new AuthProperties();
                this.auth.setEnabled(auth.isEnabled());
            }
        }
    }

    public SynapseProperties(
            int port,
            String apiKey,
            String dataDir,
            OllamaProperties ollama,
            MemoryProperties memory,
            CorsProperties cors,
            AuthProperties auth
    ) {
        this(port, apiKey, dataDir, ollama, (MemoryConfig) memory, (CorsConfig) cors, (AuthConfig) auth);
    }

    public int getPort() { return port; }
    public void setPort(int port) { if (port > 0) this.port = port; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { if (apiKey != null) this.apiKey = apiKey; }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { if (dataDir != null) this.dataDir = dataDir; }

    public OllamaProperties getOllama() { return ollama; }
    public void setOllama(OllamaProperties ollama) { if (ollama != null) this.ollama = ollama; }

    public CorsProperties getCors() { return cors; }
    public void setCors(CorsProperties cors) { if (cors != null) this.cors = cors; }

    public AuthProperties getAuth() { return auth; }
    public void setAuth(AuthProperties auth) { if (auth != null) this.auth = auth; }

    // Record-style accessors for backward compatibility across existing call sites
    public int port() { return getPort(); }
    public String apiKey() { return getApiKey(); }
    public String dataDir() { return getDataDir(); }
    public OllamaProperties ollama() { return getOllama(); }
    public MemoryConfig memory() { return getMemory(); }
    public CorsProperties cors() { return getCors(); }
    public AuthProperties auth() { return getAuth(); }

    /**
     * Ollama LLM provider settings.
     */
    public record OllamaProperties(String baseUrl, String model, String embedModel) {
        public OllamaProperties {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:11434";
            if (model == null || model.isBlank()) model = "llama3.2";
            if (embedModel == null || embedModel.isBlank()) embedModel = "nomic-embed-text";
        }
    }

    // ─────────────── Inner Class Aliases for Backward Compatibility ───────────────

    public static class MemoryProperties extends MemoryConfig {
        public MemoryProperties() {}
        public MemoryProperties(int maxMemories, int dimensions) {
            setMaxMemories(maxMemories);
            setDimensions(dimensions);
        }
        public MemoryProperties(int maxMemories, int dimensions, ConsolidationProperties consolidation) {
            setMaxMemories(maxMemories);
            setDimensions(dimensions);
            if (consolidation != null) setConsolidation(consolidation);
        }
    }

    public static class ConsolidationProperties extends com.spectrayan.spector.config.ConsolidationConfig {
        public ConsolidationProperties() {}
        public ConsolidationProperties(long interval) { super(interval); }
    }

    public static class CorsProperties extends CorsConfig {
        public CorsProperties() {}
        public CorsProperties(String allowedOrigins) { super(allowedOrigins); }
    }

    public static class AuthProperties extends AuthConfig {
        private JwtProperties jwt = new JwtProperties();
        private RefreshProperties refresh = new RefreshProperties();
        private OidcProperties oidc = new OidcProperties();
        private DefaultAdminProperties defaultAdmin = new DefaultAdminProperties();
        private Pbkdf2Properties pbkdf2 = new Pbkdf2Properties();
        private LockoutProperties lockout = new LockoutProperties();

        public AuthProperties() {}

        public AuthProperties(boolean enabled, JwtProperties jwt, RefreshProperties refresh,
                              OidcProperties oidc, DefaultAdminProperties defaultAdmin,
                              Pbkdf2Properties pbkdf2, LockoutProperties lockout,
                              List<String> publicPaths) {
            setEnabled(enabled);
            if (jwt != null) this.jwt = jwt;
            if (refresh != null) this.refresh = refresh;
            if (oidc != null) this.oidc = oidc;
            if (defaultAdmin != null) this.defaultAdmin = defaultAdmin;
            if (pbkdf2 != null) this.pbkdf2 = pbkdf2;
            if (lockout != null) this.lockout = lockout;
            if (publicPaths != null) setPublicPaths(publicPaths);
        }

        public JwtProperties getJwt() { return jwt; }
        public void setJwt(JwtProperties jwt) { this.jwt = jwt; }

        public RefreshProperties getRefresh() { return refresh; }
        public void setRefresh(RefreshProperties refresh) { this.refresh = refresh; }

        public OidcProperties getOidc() { return oidc; }
        public void setOidc(OidcProperties oidc) { this.oidc = oidc; }

        public DefaultAdminProperties getDefaultAdmin() { return defaultAdmin; }
        public void setDefaultAdmin(DefaultAdminProperties defaultAdmin) { this.defaultAdmin = defaultAdmin; }

        public Pbkdf2Properties getPbkdf2() { return pbkdf2; }
        public void setPbkdf2(Pbkdf2Properties pbkdf2) { this.pbkdf2 = pbkdf2; }

        public LockoutProperties getLockout() { return lockout; }
        public void setLockout(LockoutProperties lockout) { this.lockout = lockout; }

        public JwtProperties jwt() { return getJwt(); }
        public RefreshProperties refresh() { return getRefresh(); }
        public OidcProperties oidc() { return getOidc(); }
        public DefaultAdminProperties defaultAdmin() { return getDefaultAdmin(); }
        public Pbkdf2Properties pbkdf2() { return getPbkdf2(); }
        public LockoutProperties lockout() { return getLockout(); }
    }

    public static class JwtProperties extends AuthConfig.JwtConfig {
        public JwtProperties() {}
        public JwtProperties(String secret, Duration ttl) { super(secret, ttl); }
    }

    public static class RefreshProperties extends AuthConfig.RefreshConfig {
        public RefreshProperties() {}
        public RefreshProperties(Duration ttl) { super(ttl); }
    }

    public static class OidcProperties extends AuthConfig.OidcConfig {
        public OidcProperties() {}
        public OidcProperties(String jwksUrl, String issuer) { super(jwksUrl, issuer); }
    }

    public static class DefaultAdminProperties extends AuthConfig.DefaultAdminConfig {
        public DefaultAdminProperties() {}
        public DefaultAdminProperties(String password) { super(password); }
    }

    public static class Pbkdf2Properties extends AuthConfig.Pbkdf2Config {
        public Pbkdf2Properties() {}
        public Pbkdf2Properties(int iterations) { super(iterations); }
    }

    public static class LockoutProperties extends AuthConfig.LockoutConfig {
        public LockoutProperties() {}
        public LockoutProperties(int maxAttempts, int minutes) { super(maxAttempts, minutes); }
    }
}
