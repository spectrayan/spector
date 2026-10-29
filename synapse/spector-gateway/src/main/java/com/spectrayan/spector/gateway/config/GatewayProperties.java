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
package com.spectrayan.spector.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the dedicated gateway router (ADR-0031, ADR-0081 §8 Phase 2).
 */
@ConfigurationProperties(prefix = "spector")
public class GatewayProperties {

    private CellProperties cell = new CellProperties();
    private RoutingProperties routing = new RoutingProperties();
    private AuthProperties auth = new AuthProperties();
    private CorsProperties cors = new CorsProperties();
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    public CellProperties getCell() {
        return cell;
    }

    public void setCell(CellProperties cell) {
        this.cell = cell;
    }

    public RoutingProperties getRouting() {
        return routing;
    }

    public void setRouting(RoutingProperties routing) {
        this.routing = routing;
    }

    public AuthProperties getAuth() {
        return auth;
    }

    public void setAuth(AuthProperties auth) {
        this.auth = auth;
    }

    public CorsProperties getCors() {
        return cors;
    }

    public void setCors(CorsProperties cors) {
        if (cors != null) this.cors = cors;
    }

    public IdempotencyProperties getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyProperties idempotency) {
        if (idempotency != null) this.idempotency = idempotency;
    }


    public static class CellProperties {
        private String id = "default";
        private String role = "gateway";
        private String nodeId = "gateway-1";
        private String ringMembers = "";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getRingMembers() {
            return ringMembers;
        }

        public void setRingMembers(String ringMembers) {
            this.ringMembers = ringMembers;
        }
    }

    public static class RoutingProperties {
        private CaffeineProperties caffeine = new CaffeineProperties();
        private RedisProperties redis = new RedisProperties();
        private GatewayRoutingProperties gateway = new GatewayRoutingProperties();

        public CaffeineProperties getCaffeine() {
            return caffeine;
        }

        public void setCaffeine(CaffeineProperties caffeine) {
            this.caffeine = caffeine;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public void setRedis(RedisProperties redis) {
            this.redis = redis;
        }

        public GatewayRoutingProperties getGateway() {
            return gateway;
        }

        public void setGateway(GatewayRoutingProperties gateway) {
            this.gateway = gateway;
        }
    }

    public static class CaffeineProperties {
        private long ttlSeconds = 60;
        private long maxSize = 1000;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class RedisProperties {
        private boolean enabled = false;
        private String uri = "redis://localhost:6379";
        private long ttlSeconds = 30;
        private long timeoutMs = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class GatewayRoutingProperties {
        private int retryMax = 2;
        private int maxBufferedBodyBytes = 10 * 1024 * 1024; // 10 MiB
        private Duration ownerTimeout = Duration.ofSeconds(10);

        public int getRetryMax() {
            return retryMax;
        }

        public void setRetryMax(int retryMax) {
            this.retryMax = retryMax;
        }

        public int getMaxBufferedBodyBytes() {
            return maxBufferedBodyBytes;
        }

        public void setMaxBufferedBodyBytes(int maxBufferedBodyBytes) {
            this.maxBufferedBodyBytes = maxBufferedBodyBytes;
        }

        public Duration getOwnerTimeout() {
            return ownerTimeout;
        }

        public void setOwnerTimeout(Duration ownerTimeout) {
            this.ownerTimeout = ownerTimeout;
        }

        /** Idle-between-reads timeout for SSE / streaming responses (default 60 s). */
        private Duration sseIdleTimeout = Duration.ofSeconds(60);
        private String scheme = "http";
        private int defaultPort = 7070;
        private TlsProperties tls = new TlsProperties();

        public Duration getSseIdleTimeout() {
            return sseIdleTimeout;
        }

        public void setSseIdleTimeout(Duration sseIdleTimeout) {
            this.sseIdleTimeout = sseIdleTimeout;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public int getDefaultPort() {
            return defaultPort;
        }

        public void setDefaultPort(int defaultPort) {
            this.defaultPort = defaultPort;
        }

        public TlsProperties getTls() {
            return tls;
        }

        public void setTls(TlsProperties tls) {
            if (tls != null) this.tls = tls;
        }

        public com.spectrayan.spector.cluster.gateway.GatewayTlsConfig toTlsConfig() {
            return com.spectrayan.spector.cluster.gateway.GatewayTlsConfig.builder()
                    .enabled(tls.isEnabled())
                    .scheme(scheme)
                    .defaultPort(defaultPort)
                    .caCertPath(tls.getCaCertPath() != null && !tls.getCaCertPath().isBlank() ? java.nio.file.Path.of(tls.getCaCertPath()) : null)
                    .clientCertPath(tls.getClientCertPath() != null && !tls.getClientCertPath().isBlank() ? java.nio.file.Path.of(tls.getClientCertPath()) : null)
                    .clientKeyPath(tls.getClientKeyPath() != null && !tls.getClientKeyPath().isBlank() ? java.nio.file.Path.of(tls.getClientKeyPath()) : null)
                    .trustStorePath(tls.getTrustStorePath() != null && !tls.getTrustStorePath().isBlank() ? java.nio.file.Path.of(tls.getTrustStorePath()) : null)
                    .trustStorePassword(tls.getTrustStorePassword() != null ? tls.getTrustStorePassword().toCharArray() : null)
                    .keyStorePath(tls.getKeyStorePath() != null && !tls.getKeyStorePath().isBlank() ? java.nio.file.Path.of(tls.getKeyStorePath()) : null)
                    .keyStorePassword(tls.getKeyStorePassword() != null ? tls.getKeyStorePassword().toCharArray() : null)
                    .hostnameVerificationEnabled(tls.isHostnameVerificationEnabled())
                    .build();
        }
    }

    public static class TlsProperties {
        private boolean enabled = false;
        private String caCertPath = "";
        private String clientCertPath = "";
        private String clientKeyPath = "";
        private String trustStorePath = "";
        private String trustStorePassword = "";
        private String keyStorePath = "";
        private String keyStorePassword = "";
        private boolean hostnameVerificationEnabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getCaCertPath() { return caCertPath; }
        public void setCaCertPath(String caCertPath) { this.caCertPath = caCertPath; }
        public String getClientCertPath() { return clientCertPath; }
        public void setClientCertPath(String clientCertPath) { this.clientCertPath = clientCertPath; }
        public String getClientKeyPath() { return clientKeyPath; }
        public void setClientKeyPath(String clientKeyPath) { this.clientKeyPath = clientKeyPath; }
        public String getTrustStorePath() { return trustStorePath; }
        public void setTrustStorePath(String trustStorePath) { this.trustStorePath = trustStorePath; }
        public String getTrustStorePassword() { return trustStorePassword; }
        public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
        public String getKeyStorePath() { return keyStorePath; }
        public void setKeyStorePath(String keyStorePath) { this.keyStorePath = keyStorePath; }
        public String getKeyStorePassword() { return keyStorePassword; }
        public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
        public boolean isHostnameVerificationEnabled() { return hostnameVerificationEnabled; }
        public void setHostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
            this.hostnameVerificationEnabled = hostnameVerificationEnabled;
        }
    }

    public static class AuthProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * CORS configuration for the gateway edge. Mirrors synapse CorsProperties pattern.
     */
    public static class CorsProperties {
        private String allowedOrigins = "http://localhost:4200,http://localhost:3000";
        private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";
        private String allowedHeaders = "Content-Type,Authorization,X-API-Key,X-Spector-Namespace,X-Spector-Tenant,Idempotency-Key";
        private String exposedHeaders = "Content-Type";
        private boolean allowCredentials = true;
        private long maxAge = 3600;

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) {
            if (allowedOrigins != null && !allowedOrigins.isBlank()) {
                this.allowedOrigins = allowedOrigins;
            }
        }

        public String getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }

        public String getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(String allowedHeaders) { this.allowedHeaders = allowedHeaders; }

        public String getExposedHeaders() { return exposedHeaders; }
        public void setExposedHeaders(String exposedHeaders) { this.exposedHeaders = exposedHeaders; }

        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }

        public long getMaxAge() { return maxAge; }
        public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
    }

    public static class IdempotencyProperties {
        private boolean enabled = true;
        private long ttlSeconds = 86_400L;
        private int maxEntries = 16_384;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }
}
