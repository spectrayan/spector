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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory for building TLS/mTLS {@link SSLContext}, {@link TrustManagerFactory},
 * and {@link KeyManagerFactory} instances from {@link GatewayTlsConfig}.
 */
public final class GatewayTlsFactory {

    private static final char[] EPHEMERAL_PASSWORD = "spector-ephemeral-tls".toCharArray();
    private static final Pattern PEM_PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----([\\s\\S]*?)-----END (?:[A-Z0-9 ]+ )?PRIVATE KEY-----"
    );
    private static final String[] KEY_ALGORITHMS = {"RSA", "EC", "Ed25519", "RSASSA-PSS"};

    private GatewayTlsFactory() {}

    /**
     * Builds an initialized {@link SSLContext} honoring the CA trust bundle, client certificate
     * material, and hostname verification policy in {@code config}.
     */
    public static SSLContext createSslContext(GatewayTlsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        validateMaterialPaths(config);
        try {
            KeyManagerFactory kmf = createKeyManagerFactory(config);
            TrustManagerFactory tmf = createTrustManagerFactory(config);

            KeyManager[] keyManagers = kmf != null ? kmf.getKeyManagers() : null;
            TrustManager[] trustManagers;
            if (tmf != null) {
                trustManagers = tmf.getTrustManagers();
            } else {
                TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                defaultTmf.init((KeyStore) null);
                trustManagers = defaultTmf.getTrustManagers();
            }

            if (!config.hostnameVerificationEnabled() && trustManagers != null) {
                trustManagers = wrapWithoutEndpointIdentification(trustManagers);
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (SpectorConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_PARSE_FAILED,
                    "gateway TLS initialization failed: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Builds a {@link TrustManagerFactory} from the configured PEM CA bundle or PKCS12/JKS truststore,
     * or returns {@code null} when using the JVM default truststore.
     */
    public static TrustManagerFactory createTrustManagerFactory(GatewayTlsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        validateMaterialPaths(config);
        try {
            KeyStore trustStore;
            if (config.caCertPath() != null) {
                requireReadableFile(config.caCertPath(), "spector.routing.gateway.tls.ca-cert-path");
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs;
                try (InputStream in = Files.newInputStream(config.caCertPath())) {
                    certs = cf.generateCertificates(in);
                }
                if (certs == null || certs.isEmpty()) {
                    throw new SpectorConfigException(
                            ErrorCode.CONFIG_PARSE_FAILED,
                            "no X.509 certificates found in CA bundle: " + config.caCertPath()
                    );
                }
                trustStore = KeyStore.getInstance("PKCS12");
                trustStore.load(null, null);
                int idx = 0;
                for (Certificate cert : certs) {
                    trustStore.setCertificateEntry("ca-" + (idx++), cert);
                }
            } else if (config.trustStorePath() != null) {
                requireReadableFile(config.trustStorePath(), "spector.routing.gateway.tls.trust-store-path");
                trustStore = loadKeyStore(
                        config.trustStorePath(),
                        config.trustStorePassword() != null ? config.trustStorePassword() : new char[0]
                );
            } else {
                return null;
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf;
        } catch (SpectorConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_PARSE_FAILED,
                    "failed to load gateway trust material: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Builds a {@link KeyManagerFactory} from the configured PEM client cert/key or PKCS12/JKS keystore,
     * or returns {@code null} when mutual TLS client authentication is not configured.
     */
    public static KeyManagerFactory createKeyManagerFactory(GatewayTlsConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        validateMaterialPaths(config);
        try {
            if (config.clientCertPath() != null && config.clientKeyPath() != null) {
                requireReadableFile(config.clientCertPath(), "spector.routing.gateway.tls.client-cert-path");
                requireReadableFile(config.clientKeyPath(), "spector.routing.gateway.tls.client-key-path");

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Collection<? extends Certificate> certs;
                try (InputStream in = Files.newInputStream(config.clientCertPath())) {
                    certs = cf.generateCertificates(in);
                }
                if (certs == null || certs.isEmpty()) {
                    throw new SpectorConfigException(
                            ErrorCode.CONFIG_PARSE_FAILED,
                            "no X.509 client certificates found in: " + config.clientCertPath()
                    );
                }
                Certificate[] certChain = certs.toArray(new Certificate[0]);
                PrivateKey privateKey = loadPemPrivateKey(config.clientKeyPath());

                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(null, null);
                keyStore.setKeyEntry("gateway-client", privateKey, EPHEMERAL_PASSWORD, certChain);

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, EPHEMERAL_PASSWORD);
                return kmf;
            } else if (config.keyStorePath() != null) {
                requireReadableFile(config.keyStorePath(), "spector.routing.gateway.tls.key-store-path");
                char[] password = config.keyStorePassword() != null ? config.keyStorePassword() : new char[0];
                KeyStore keyStore = loadKeyStore(config.keyStorePath(), password);
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, password);
                return kmf;
            }
            return null;
        } catch (SpectorConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_PARSE_FAILED,
                    "failed to load gateway client TLS material: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Wraps {@link TrustManager} instances so certificate chain validation still runs against the
     * configured CA truststore while skipping hostname endpoint identification when disabled.
     */
    public static TrustManager[] wrapWithoutEndpointIdentification(TrustManager[] managers) {
        if (managers == null) {
            return null;
        }
        TrustManager[] wrapped = new TrustManager[managers.length];
        for (int i = 0; i < managers.length; i++) {
            if (managers[i] instanceof X509TrustManager x509) {
                wrapped[i] = new ChainOnlyExtendedTrustManager(x509);
            } else {
                wrapped[i] = managers[i];
            }
        }
        return wrapped;
    }

    private static void validateMaterialPaths(GatewayTlsConfig config) {
        if ((config.clientCertPath() == null) != (config.clientKeyPath() == null)) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "both client-cert-path and client-key-path must be provided together for mutual TLS"
            );
        }
        if (config.caCertPath() != null && config.trustStorePath() != null) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "configure either ca-cert-path or trust-store-path, not both"
            );
        }
        if (config.clientCertPath() != null && config.keyStorePath() != null) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_VALUE_INVALID,
                    "spector.routing.gateway.tls",
                    "configure either client-cert-path/client-key-path or key-store-path, not both"
            );
        }
    }

    private static void requireReadableFile(Path path, String propertyName) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_FILE_NOT_FOUND,
                    propertyName + "=" + path
            );
        }
    }

    private static KeyStore loadKeyStore(Path path, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            ks.load(in, password);
        }
        return ks;
    }

    private static PrivateKey loadPemPrivateKey(Path keyPath) throws Exception {
        String pem = Files.readString(keyPath, StandardCharsets.UTF_8);
        Matcher matcher = PEM_PRIVATE_KEY_PATTERN.matcher(pem);
        if (!matcher.find()) {
            throw new SpectorConfigException(
                    ErrorCode.CONFIG_PARSE_FAILED,
                    "no PEM PRIVATE KEY block found in: " + keyPath
            );
        }
        String base64Body = matcher.group(1).replaceAll("\\s+", "");
        byte[] derBytes = Base64.getDecoder().decode(base64Body);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(derBytes);

        Exception lastError = null;
        for (String alg : KEY_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(keySpec);
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new SpectorConfigException(
                ErrorCode.CONFIG_PARSE_FAILED,
                "unsupported or malformed PKCS#8 private key in " + keyPath,
                lastError
        );
    }

    private static final class ChainOnlyExtendedTrustManager extends X509ExtendedTrustManager {
        private final X509TrustManager delegate;

        ChainOnlyExtendedTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
