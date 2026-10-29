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
package com.spectrayan.spector.synapse.config.routing;

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;

/**
 * Configuration properties for the Cell Routing Cache, Pub/Sub Invalidation, and Gateway Resilience
 * (ADR-0034 §8, §15.6, Phase 2, Req R1, R3, R5, R8).
 *
 * <p>Prefix: {@code spector.routing.*}</p>
 */
public class RoutingProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private RedisProperties redis = new RedisProperties();
    private CaffeineProperties caffeine = new CaffeineProperties();
    private GatewayProperties gateway = new GatewayProperties();

    public RoutingProperties() {}

    public RedisProperties getRedis() {
        return redis;
    }

    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    public CaffeineProperties getCaffeine() {
        return caffeine;
    }

    public void setCaffeine(CaffeineProperties caffeine) {
        this.caffeine = caffeine;
    }

    public GatewayProperties getGateway() {
        return gateway;
    }

    public void setGateway(GatewayProperties gateway) {
        this.gateway = gateway;
    }

    public static class RedisProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_ENABLED;
        private String uri = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_URI;
        private long timeoutMs = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_TIMEOUT_MS;
        private long ttlSeconds = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_TTL_SECONDS;

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

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class CaffeineProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private long ttlSeconds = SpectorPropertyConstants.DEFAULT_ROUTING_CAFFEINE_TTL_SECONDS;
        private long maxSize = SpectorPropertyConstants.DEFAULT_ROUTING_CAFFEINE_MAX_SIZE;

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

    public static class GatewayProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private int retryMax = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_RETRY_MAX;
        private String scheme = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_SCHEME;
        private int defaultPort = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_DEFAULT_PORT;
        private java.time.Duration ownerTimeout = java.time.Duration.ofSeconds(10);
        private TlsProperties tls = new TlsProperties();

        public int getRetryMax() {
            return retryMax;
        }

        public void setRetryMax(int retryMax) {
            this.retryMax = retryMax;
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

        public java.time.Duration getOwnerTimeout() {
            return ownerTimeout;
        }

        public void setOwnerTimeout(java.time.Duration ownerTimeout) {
            this.ownerTimeout = ownerTimeout;
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

    public static class TlsProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_ENABLED;
        private String caCertPath = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_CA_CERT_PATH;
        private String clientCertPath = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_CLIENT_CERT_PATH;
        private String clientKeyPath = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_CLIENT_KEY_PATH;
        private String trustStorePath = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_TRUST_STORE_PATH;
        private String trustStorePassword = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_TRUST_STORE_PASSWORD;
        private String keyStorePath = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_KEY_STORE_PATH;
        private String keyStorePassword = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_KEY_STORE_PASSWORD;
        private boolean hostnameVerificationEnabled =
                SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_TLS_HOSTNAME_VERIFICATION_ENABLED;

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
}
