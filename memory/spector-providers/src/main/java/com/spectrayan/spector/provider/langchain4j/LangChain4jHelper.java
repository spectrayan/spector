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
package com.spectrayan.spector.provider.langchain4j;

import com.spectrayan.spector.provider.ProviderConfig;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared utility to parse and apply infrastructure settings (proxies, mTLS, custom headers)
 * from {@link ProviderConfig#properties()} to LangChain4j model builders.
 *
 * <p>Supports dynamic environment detection, automatically binding to Spring's
 * {@code RestClient} or {@code WebClient} in Spring Boot environments, or falling
 * back to Java's native {@code HttpClient} in standalone CLI/stdio deployments.</p>
 */
public final class LangChain4jHelper {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jHelper.class);

    // Holders for Spring-managed builders (registered at startup by spector-spring)
    private static volatile Object springRestClientBuilder;
    private static volatile Object springWebClientBuilder;

    private LangChain4jHelper() {
        // Prevent instantiation
    }

    /**
     * Registers the Spring RestClient.Builder.
     */
    public static void setSpringRestClientBuilder(Object builder) {
        springRestClientBuilder = builder;
    }

    /**
     * Registers the Spring WebClient.Builder.
     */
    public static void setSpringWebClientBuilder(Object builder) {
        springWebClientBuilder = builder;
    }

    /**
     * Resolves an {@link HttpClientBuilder} configured with HTTP proxy and SSL/mTLS client certificates
     * if specified in the provider properties, or reuses Spring's managed connection pool.
     *
     * @param config the provider configuration
     * @param defaultTimeout standard timeout to apply to the client
     * @return a configured {@link HttpClientBuilder}, or {@code null} if no custom network configuration is needed
     */
    public static HttpClientBuilder resolveHttpClient(ProviderConfig config, Duration defaultTimeout) {
        String clientType = config.properties().getOrDefault("httpClientType", "auto");

        // 1. Try to resolve Spring RestClient
        if ("spring-restclient".equalsIgnoreCase(clientType) || ("auto".equalsIgnoreCase(clientType) && springRestClientBuilder != null)) {
            try {
                Class<?> builderClass = Class.forName("dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder");
                Object builderInstance = builderClass.getConstructor().newInstance();

                // Find and invoke .restClientBuilder(...)
                boolean foundMethod = false;
                for (java.lang.reflect.Method m : builderClass.getMethods()) {
                    if (m.getName().equals("restClientBuilder")) {
                        m.invoke(builderInstance, springRestClientBuilder);
                        foundMethod = true;
                        break;
                    }
                }

                if (foundMethod) {
                    builderClass.getMethod("connectTimeout", Duration.class).invoke(builderInstance, defaultTimeout);
                    builderClass.getMethod("readTimeout", Duration.class).invoke(builderInstance, defaultTimeout);
                    log.info("[ProviderRegistry] Successfully configured SpringRestClientBuilder for provider '{}'", config.name());
                    return (HttpClientBuilder) builderInstance;
                }
            } catch (ClassNotFoundException e) {
                // Ignore if dependency not on classpath
            } catch (Exception e) {
                log.warn("[ProviderRegistry] Failed to configure SpringRestClientBuilder: {}", e.getMessage());
            }
        }

        // 2. Try to resolve Spring WebClient
        if ("spring-webclient".equalsIgnoreCase(clientType) || ("auto".equalsIgnoreCase(clientType) && springWebClientBuilder != null)) {
            try {
                Class<?> builderClass = Class.forName("dev.langchain4j.http.client.spring.webclient.SpringWebClientBuilder");
                Object builderInstance = builderClass.getConstructor().newInstance();

                // Find and invoke .webClientBuilder(...)
                boolean foundMethod = false;
                for (java.lang.reflect.Method m : builderClass.getMethods()) {
                    if (m.getName().equals("webClientBuilder")) {
                        m.invoke(builderInstance, springWebClientBuilder);
                        foundMethod = true;
                        break;
                    }
                }

                if (foundMethod) {
                    builderClass.getMethod("connectTimeout", Duration.class).invoke(builderInstance, defaultTimeout);
                    builderClass.getMethod("readTimeout", Duration.class).invoke(builderInstance, defaultTimeout);
                    log.info("[ProviderRegistry] Successfully configured SpringWebClientBuilder for provider '{}'", config.name());
                    return (HttpClientBuilder) builderInstance;
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            } catch (Exception e) {
                log.warn("[ProviderRegistry] Failed to configure SpringWebClientBuilder: {}", e.getMessage());
            }
        }

        // 3. Standalone JDK HttpClient Fallback
        String host = config.properties().get("proxyHost");
        String portStr = config.properties().get("proxyPort");
        String certPath = config.properties().get("clientCertPath");
        String keyPath = config.properties().get("clientKeyPath");

        boolean hasProxy = host != null && !host.isBlank() && portStr != null && !portStr.isBlank();
        boolean hasMtls = certPath != null && !certPath.isBlank() && keyPath != null && !keyPath.isBlank();

        if (!hasProxy && !hasMtls) {
            return null;
        }

        try {
            HttpClient.Builder javaClientBuilder = HttpClient.newBuilder()
                    .connectTimeout(defaultTimeout);

            // Configure HTTP Proxy
            if (hasProxy) {
                try {
                    int port = Integer.parseInt(portStr.trim());
                    log.info("[ProviderRegistry] Configuring HTTP proxy for provider {}: {}:{}", config.name(), host, port);
                    javaClientBuilder.proxy(ProxySelectorWrapper.of(host, port));
                } catch (NumberFormatException e) {
                    log.warn("[ProviderRegistry] Invalid proxy port '{}' configured for provider '{}'. Skipping proxy setup.",
                            portStr, config.name());
                }
            }

            // Configure mTLS Client Certificates
            if (hasMtls) {
                try {
                    log.info("[ProviderRegistry] Configuring mTLS client certificate for provider {}: cert={}, key={}",
                            config.name(), certPath, keyPath);
                    SSLContext sslContext = createSslContext(certPath, keyPath);
                    javaClientBuilder.sslContext(sslContext);
                } catch (Exception e) {
                    log.error("[ProviderRegistry] Failed to configure mTLS for provider '{}': {}", config.name(), e.getMessage(), e);
                }
            }

            // Wrap the native Java HttpClient builder in JdkHttpClientBuilder
            return JdkHttpClient.builder()
                    .httpClientBuilder(javaClientBuilder);
        } catch (Exception e) {
            log.error("[ProviderRegistry] Failed to create HttpClientBuilder for provider '{}': {}", config.name(), e.getMessage(), e);
            return null;
        }
    }

    private static SSLContext createSslContext(String certPath, String keyPath) throws Exception {
        // Load PEM public certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (InputStream is = Files.newInputStream(Paths.get(certPath))) {
            cert = (X509Certificate) cf.generateCertificate(is);
        }

        // Load PEM private key (PKCS#8 format)
        String keyPem = Files.readString(Paths.get(keyPath))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);

        PrivateKey privateKey;
        try {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            privateKey = KeyFactory.getInstance("EC").generatePrivate(spec);
        }

        // Create an in-memory PKCS12 keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client-identity", privateKey, new char[0], new Certificate[]{cert});

        // Initialize key manager
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        // Build SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Extracts custom headers configured under the {@code header.} namespace prefix.
     *
     * @param config the provider configuration
     * @return a map of header names and values, or empty map if none configured
     */
    public static Map<String, String> resolveCustomHeaders(ProviderConfig config) {
        Map<String, String> headers = new HashMap<>();
        for (var entry : config.properties().entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith("header.")) {
                String headerName = entry.getKey().substring("header.".length());
                if (headerName != null && !headerName.isBlank() && entry.getValue() != null) {
                    headers.put(headerName, entry.getValue());
                }
            }
        }
        return headers;
    }

    /**
     * Minimal implementation of ProxySelector helper.
     */
    private static class ProxySelectorWrapper extends java.net.ProxySelector {
        private final String host;
        private final int port;

        private ProxySelectorWrapper(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public static ProxySelectorWrapper of(String host, int port) {
            return new ProxySelectorWrapper(host, port);
        }

        @Override
        public java.util.List<Proxy> select(java.net.URI uri) {
            return java.util.List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }

        @Override
        public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            log.error("[ProviderRegistry] Proxy connection failed for host={}: {}", host, ioe.getMessage());
        }
    }
}
