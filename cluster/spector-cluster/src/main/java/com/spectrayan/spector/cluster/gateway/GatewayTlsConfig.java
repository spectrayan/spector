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
package com.spectrayan.spector.cluster.gateway;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

import java.nio.file.Path;
import java.util.Locale;

/**
 * TLS and mutual TLS (mTLS) configuration for internal gateway-to-owner-node HTTP transport hops.
 */
public record GatewayTlsConfig(
        boolean enabled,
        String scheme,
        int defaultPort,
        Path caCertPath,
        Path clientCertPath,
        Path clientKeyPath,
        Path trustStorePath,
        char[] trustStorePassword,
        Path keyStorePath,
        char[] keyStorePassword,
        boolean hostnameVerificationEnabled
) {

    public static final String DEFAULT_SCHEME = "http";
    public static final String HTTPS_SCHEME = "https";
    public static final int DEFAULT_PORT = 7070;

    public static GatewayTlsConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether any custom trust or client identity material is configured.
     */
    public boolean hasCustomTlsMaterial() {
        return caCertPath != null
                || clientCertPath != null
                || clientKeyPath != null
                || trustStorePath != null
                || keyStorePath != null;
    }

    /**
     * Resolves the effective URL scheme ({@code "http"} or {@code "https"}).
     *
     * <p>When {@link #enabled()} or custom TLS material is configured and {@code scheme}
     * is left at the default {@code "http"}, the effective scheme upgrades to {@code "https"}.</p>
     */
    public String effectiveScheme() {
        String normalized = normalizeAndValidateScheme(scheme);
        if ((enabled || hasCustomTlsMaterial()) && DEFAULT_SCHEME.equals(normalized)) {
            return HTTPS_SCHEME;
        }
        return normalized;
    }

    /**
     * Returns {@code true} when TLS is active for gateway-to-owner hops.
     */
    public boolean isTlsActive() {
        return enabled || hasCustomTlsMaterial() || HTTPS_SCHEME.equals(normalizeAndValidateScheme(scheme));
    }

    /**
     * Validates all TLS/mTLS configuration invariants and fails closed on misconfiguration.
     */
    public void validate() {
        normalizeAndValidateScheme(scheme);
        if (defaultPort <= 0 || defaultPort > 65535) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.default-port",
                    defaultPort
            );
        }
        if ((clientCertPath == null) != (clientKeyPath == null)) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "both client-cert-path and client-key-path must be provided together for mutual TLS"
            );
        }
        if (caCertPath != null && trustStorePath != null) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "configure either ca-cert-path or trust-store-path, not both"
            );
        }
        if (clientCertPath != null && keyStorePath != null) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "configure either client-cert-path/client-key-path or key-store-path, not both"
            );
        }
        if (isTlsActive()) {
            GatewayTlsFactory.createSslContext(this);
        }
    }

    public static String normalizeAndValidateScheme(String rawScheme) {
        if (rawScheme == null || rawScheme.isBlank()) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.scheme",
                    String.valueOf(rawScheme)
            );
        }
        String normalized = rawScheme.trim().toLowerCase(Locale.ROOT);
        if (!DEFAULT_SCHEME.equals(normalized) && !HTTPS_SCHEME.equals(normalized)) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.scheme",
                    rawScheme
            );
        }
        return normalized;
    }

    public static final class Builder {
        private boolean enabled = false;
        private String scheme = DEFAULT_SCHEME;
        private int defaultPort = DEFAULT_PORT;
        private Path caCertPath;
        private Path clientCertPath;
        private Path clientKeyPath;
        private Path trustStorePath;
        private char[] trustStorePassword;
        private Path keyStorePath;
        private char[] keyStorePassword;
        private boolean hostnameVerificationEnabled = true;

        private Builder() {}

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder defaultPort(int defaultPort) {
            this.defaultPort = defaultPort;
            return this;
        }

        public Builder caCertPath(Path caCertPath) {
            this.caCertPath = caCertPath;
            return this;
        }

        public Builder clientCertPath(Path clientCertPath) {
            this.clientCertPath = clientCertPath;
            return this;
        }

        public Builder clientKeyPath(Path clientKeyPath) {
            this.clientKeyPath = clientKeyPath;
            return this;
        }

        public Builder trustStorePath(Path trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder trustStorePassword(char[] trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder keyStorePath(Path keyStorePath) {
            this.keyStorePath = keyStorePath;
            return this;
        }

        public Builder keyStorePassword(char[] keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
            return this;
        }

        public Builder hostnameVerificationEnabled(boolean hostnameVerificationEnabled) {
            this.hostnameVerificationEnabled = hostnameVerificationEnabled;
            return this;
        }

        public GatewayTlsConfig build() {
            return new GatewayTlsConfig(
                    enabled,
                    scheme,
                    defaultPort,
                    caCertPath,
                    clientCertPath,
                    clientKeyPath,
                    trustStorePath,
                    trustStorePassword != null ? trustStorePassword.clone() : null,
                    keyStorePath,
                    keyStorePassword != null ? keyStorePassword.clone() : null,
                    hostnameVerificationEnabled
            );
        }
    }
}
