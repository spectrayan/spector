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

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for HTTPS and mutual TLS (mTLS) on internal gateway-to-owner-node hops (#1182).
 */
class GatewayMtlsForwardingTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    @Test
    @DisplayName("defaultNodeUrlResolver applies configured https scheme without requiring manual scheme prefix on node IDs")
    void defaultNodeUrlResolverUsesConfiguredHttpsSchemeWithoutManualNodePrefix() {
        Function<String, String> httpsResolver = GatewayForwarder.defaultNodeUrlResolver("https", 7070);

        assertThat(httpsResolver.apply("node-a")).isEqualTo("https://node-a:7070");
        assertThat(httpsResolver.apply("node-b:8443")).isEqualTo("https://node-b:8443");
        assertThat(httpsResolver.apply("https://node-c:9443")).isEqualTo("https://node-c:9443");
        assertThat(httpsResolver.apply("http://node-d:7070")).isEqualTo("http://node-d:7070");

        assertThatThrownBy(() -> GatewayForwarder.defaultNodeUrlResolver("ftp", 7070))
                .isInstanceOf(SpectorConfigException.class);
    }

    @Test
    @DisplayName("JDK GatewayHttpTransport and GatewayForwarder forward over HTTPS + mTLS using PEM CA bundle and client cert/key")
    void jdkTransportForwardsOverHttpsAndMutualTlsWithPemCertificates(@TempDir Path tempDir) throws Exception {
        MtlsMaterials materials = generateMaterials(tempDir, "SAN=ip:127.0.0.1,dns:localhost");
        AtomicReference<String> observedClientDn = new AtomicReference<>();

        HttpsServer server = startMtlsHttpsServer(materials.serverSslContext(), true, observedClientDn);
        try {
            int port = server.getAddress().getPort();
            String bareNodeId = "127.0.0.1:" + port;

            GatewayTlsConfig tlsConfig = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .defaultPort(port)
                    .caCertPath(materials.caPem())
                    .clientCertPath(materials.clientCertPem())
                    .clientKeyPath(materials.clientKeyPem())
                    .hostnameVerificationEnabled(true)
                    .build();

            WaterfallRoutingResolver resolver = resolverForNode(bareNodeId);
            GatewayHttpTransport transport = GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(5), tlsConfig);
            GatewayForwarder forwarder = new GatewayForwarder(
                    resolver,
                    1,
                    transport,
                    GatewayForwarder.defaultNodeUrlResolver(tlsConfig.effectiveScheme(), tlsConfig.defaultPort()),
                    null,
                    tlsConfig
            );

            ForwardRequest request = ForwardRequest.ofBytes(
                    "POST",
                    "/api/v1/memories/remember",
                    Map.of("Content-Type", List.of("application/json")),
                    "{\"content\":\"secure hop\"}".getBytes(StandardCharsets.UTF_8),
                    null
            );

            ForwardResponse response = forwarder.forward(RoutingKey.ofUntenanted("default", "ns-mtls"), request);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("mtls-ok");
            assertThat(observedClientDn.get()).contains("CN=gateway-client");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("JDK GatewayHttpTransport enforces CA trust, client cert requirement, hostname verification, and fails closed on misconfiguration")
    void jdkTransportEnforcesTrustHostnameVerificationAndFailsClosed(@TempDir Path tempDir) throws Exception {
        MtlsMaterials trusted = generateMaterials(tempDir.resolve("trusted"), "SAN=ip:127.0.0.1,dns:localhost");
        MtlsMaterials untrusted = generateMaterials(tempDir.resolve("untrusted"), "SAN=ip:127.0.0.1,dns:localhost");
        MtlsMaterials wrongHost = generateMaterials(tempDir.resolve("wrong-host"), "SAN=dns:internal-owner.spector.local");

        // 1. Fail closed on misconfigured TLS properties
        assertThatThrownBy(() -> GatewayTlsConfig.builder()
                .enabled(true)
                .scheme("invalid-scheme")
                .build()
                .validate())
                .isInstanceOf(SpectorConfigException.class);

        assertThatThrownBy(() -> GatewayTlsConfig.builder()
                .enabled(true)
                .caCertPath(tempDir.resolve("missing-ca.pem"))
                .build()
                .validate())
                .isInstanceOf(SpectorConfigException.class);

        assertThatThrownBy(() -> GatewayTlsConfig.builder()
                .enabled(true)
                .clientCertPath(trusted.clientCertPem())
                // Missing clientKeyPath -> fail closed
                .build()
                .validate())
                .isInstanceOf(SpectorConfigException.class);

        // 2. Reject untrusted server certificate and missing client certificate on mTLS server
        HttpsServer mtlsServer = startMtlsHttpsServer(trusted.serverSslContext(), true, new AtomicReference<>());
        try {
            int port = mtlsServer.getAddress().getPort();
            String bareNodeId = "127.0.0.1:" + port;
            WaterfallRoutingResolver resolver = resolverForNode(bareNodeId);

            // Wrong CA bundle -> handshake failure
            GatewayTlsConfig wrongCaConfig = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(untrusted.caPem())
                    .clientCertPath(trusted.clientCertPem())
                    .clientKeyPath(trusted.clientKeyPem())
                    .build();
            GatewayForwarder wrongCaForwarder = new GatewayForwarder(
                    resolver, 0,
                    GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(5), wrongCaConfig),
                    GatewayForwarder.defaultNodeUrlResolver(wrongCaConfig.effectiveScheme(), port),
                    null, wrongCaConfig);

            ForwardRequest req = ForwardRequest.ofBytes("GET", "/api/v1/memories/stats", Map.of(), new byte[0], null);
            assertThatThrownBy(() -> wrongCaForwarder.forward(RoutingKey.ofUntenanted("default", "ns-1"), req))
                    .isInstanceOf(Exception.class);

            // Missing client certificate when server requires mTLS -> handshake/connection failure
            GatewayTlsConfig noClientCertConfig = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(trusted.caPem())
                    .build();
            GatewayForwarder noClientCertForwarder = new GatewayForwarder(
                    resolver, 0,
                    GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(5), noClientCertConfig),
                    GatewayForwarder.defaultNodeUrlResolver(noClientCertConfig.effectiveScheme(), port),
                    null, noClientCertConfig);

            assertThatThrownBy(() -> noClientCertForwarder.forward(RoutingKey.ofUntenanted("default", "ns-1"), req))
                    .isInstanceOf(Exception.class);
        } finally {
            mtlsServer.stop(0);
        }

        // 3. Hostname verification: mismatch rejected when enabled, allowed when disabled (while still verifying CA)
        HttpsServer wrongHostServer = startMtlsHttpsServer(wrongHost.serverSslContext(), false, new AtomicReference<>());
        try {
            int port = wrongHostServer.getAddress().getPort();
            String bareNodeId = "127.0.0.1:" + port;
            WaterfallRoutingResolver resolver = resolverForNode(bareNodeId);
            ForwardRequest req = ForwardRequest.ofBytes("GET", "/api/v1/memories/stats", Map.of(), new byte[0], null);

            GatewayTlsConfig strictHostConfig = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(wrongHost.caPem())
                    .hostnameVerificationEnabled(true)
                    .build();
            GatewayForwarder strictForwarder = new GatewayForwarder(
                    resolver, 0,
                    GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(5), strictHostConfig),
                    GatewayForwarder.defaultNodeUrlResolver(strictHostConfig.effectiveScheme(), port),
                    null, strictHostConfig);

            assertThatThrownBy(() -> strictForwarder.forward(RoutingKey.ofUntenanted("default", "ns-1"), req))
                    .isInstanceOf(Exception.class);

            GatewayTlsConfig relaxedHostConfig = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(wrongHost.caPem())
                    .hostnameVerificationEnabled(false)
                    .build();
            GatewayForwarder relaxedForwarder = new GatewayForwarder(
                    resolver, 0,
                    GatewayHttpTransport.defaultJdkTransport(Duration.ofSeconds(5), relaxedHostConfig),
                    GatewayForwarder.defaultNodeUrlResolver(relaxedHostConfig.effectiveScheme(), port),
                    null, relaxedHostConfig);

            ForwardResponse okResp = relaxedForwarder.forward(RoutingKey.ofUntenanted("default", "ns-1"), req);
            assertThat(okResp.statusCode()).isEqualTo(200);
        } finally {
            wrongHostServer.stop(0);
        }
    }

    private static WaterfallRoutingResolver resolverForNode(String nodeId) {
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of(nodeId), 16);
        return new WaterfallRoutingResolver(
                () -> ring,
                null,
                Duration.ofSeconds(30),
                100,
                30,
                RoutingMetricsListener.NOOP,
                null
        );
    }

    static HttpsServer startMtlsHttpsServer(
            SSLContext serverSslContext,
            boolean needClientAuth,
            AtomicReference<String> observedClientDn
    ) throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                sslParams.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                sslParams.setNeedClientAuth(needClientAuth);
                params.setSSLParameters(sslParams);
            }
        });
        server.createContext("/", exchange -> {
            HttpsExchange httpsExchange = (HttpsExchange) exchange;
            SSLSession session = httpsExchange.getSSLSession();
            if (needClientAuth) {
                Certificate[] peerCerts = session.getPeerCertificates();
                if (peerCerts.length > 0 && peerCerts[0] instanceof X509Certificate x509) {
                    observedClientDn.set(x509.getSubjectX500Principal().getName());
                }
            }
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            byte[] resp = "{\"status\":\"mtls-ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(resp);
            }
        });
        server.start();
        return server;
    }

    record MtlsMaterials(
            Path serverKeyStore,
            Path serverTrustStore,
            Path clientKeyStore,
            Path clientTrustStore,
            Path caPem,
            Path clientCertPem,
            Path clientKeyPem,
            SSLContext serverSslContext
    ) {}

    static MtlsMaterials generateMaterials(Path dir, String sanExt) throws Exception {
        Files.createDirectories(dir);
        Path serverKsPath = dir.resolve("server.p12");
        Path serverCertPath = dir.resolve("server.crt");
        Path serverTsPath = dir.resolve("server-trust.p12");
        Path clientKsPath = dir.resolve("client.p12");
        Path clientCertPath = dir.resolve("client.crt");
        Path clientTsPath = dir.resolve("client-trust.p12");

        String javaHome = System.getProperty("java.home");
        String keytoolBin = Path.of(javaHome, "bin", "keytool").toString();

        runKeytool(keytoolBin, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", serverKsPath.toString(), "-storepass", "changeit",
                "-validity", "30", "-dname", "CN=localhost, OU=Spector, O=Spectrayan, C=US",
                "-ext", sanExt);
        runKeytool(keytoolBin, "-exportcert", "-alias", "server", "-keystore", serverKsPath.toString(),
                "-storepass", "changeit", "-file", serverCertPath.toString());
        runKeytool(keytoolBin, "-importcert", "-noprompt", "-alias", "server", "-keystore", clientTsPath.toString(),
                "-storepass", "changeit", "-file", serverCertPath.toString());

        runKeytool(keytoolBin, "-genkeypair", "-alias", "client", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", clientKsPath.toString(), "-storepass", "changeit",
                "-validity", "30", "-dname", "CN=gateway-client, OU=Spector, O=Spectrayan, C=US");
        runKeytool(keytoolBin, "-exportcert", "-alias", "client", "-keystore", clientKsPath.toString(),
                "-storepass", "changeit", "-file", clientCertPath.toString());
        runKeytool(keytoolBin, "-importcert", "-noprompt", "-alias", "client", "-keystore", serverTsPath.toString(),
                "-storepass", "changeit", "-file", clientCertPath.toString());

        KeyStore serverKs = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(serverKsPath)) {
            serverKs.load(in, STORE_PASSWORD);
        }
        KeyStore serverTs = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(serverTsPath)) {
            serverTs.load(in, STORE_PASSWORD);
        }
        KeyStore clientKs = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(clientKsPath)) {
            clientKs.load(in, STORE_PASSWORD);
        }

        // Write PEM files for CA cert, client cert, and client private key
        Certificate serverCert = serverKs.getCertificate("server");
        Certificate clientCert = clientKs.getCertificate("client");
        PrivateKey clientKey = (PrivateKey) clientKs.getKey("client", STORE_PASSWORD);

        Path caPem = dir.resolve("ca.pem");
        Path clientCertPem = dir.resolve("client-cert.pem");
        Path clientKeyPem = dir.resolve("client-key.pem");

        Files.writeString(caPem, toPem("CERTIFICATE", serverCert.getEncoded()), StandardCharsets.UTF_8);
        Files.writeString(clientCertPem, toPem("CERTIFICATE", clientCert.getEncoded()), StandardCharsets.UTF_8);
        Files.writeString(clientKeyPem, toPem("PRIVATE KEY", clientKey.getEncoded()), StandardCharsets.UTF_8);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKs, STORE_PASSWORD);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(serverTs);
        SSLContext serverSsl = SSLContext.getInstance("TLS");
        serverSsl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return new MtlsMaterials(
                serverKsPath, serverTsPath, clientKsPath, clientTsPath,
                caPem, clientCertPem, clientKeyPem, serverSsl
        );
    }

    private static String toPem(String type, byte[] encoded) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    private static void runKeytool(String... cmd) throws Exception {
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!proc.waitFor(15, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IllegalStateException("keytool timed out");
        }
        if (proc.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
