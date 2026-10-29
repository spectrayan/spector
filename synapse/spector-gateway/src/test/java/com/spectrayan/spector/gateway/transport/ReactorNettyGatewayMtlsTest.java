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
package com.spectrayan.spector.gateway.transport;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.GatewayHttpTransport;
import com.spectrayan.spector.cluster.gateway.GatewayTlsConfig;
import com.spectrayan.spector.cluster.idempotency.InMemoryIdempotencyStore;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.gateway.config.GatewayConfiguration;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for HTTPS and mutual TLS in {@link ReactorNettyGatewayHttpTransport}
 * and {@link GatewayConfiguration} (#1182).
 */
class ReactorNettyGatewayMtlsTest {

    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    @Test
    @DisplayName("GatewayConfiguration and ReactorNettyGatewayHttpTransport forward over HTTPS + mTLS without manual scheme prefix on node IDs")
    void reactorNettyTransportForwardsOverHttpsAndMutualTls(@TempDir Path tempDir) throws Exception {
        MtlsMaterials materials = generateMaterials(tempDir, "SAN=ip:127.0.0.1,dns:localhost");
        AtomicReference<String> observedClientDn = new AtomicReference<>();

        HttpsServer server = startMtlsHttpsServer(materials.serverSslContext(), true, observedClientDn);
        try {
            int port = server.getAddress().getPort();
            String bareNodeId = "127.0.0.1:" + port;

            GatewayProperties props = new GatewayProperties();
            props.getCell().setId("cell-1");
            props.getCell().setNodeId("gateway-1");
            props.getCell().setRingMembers(bareNodeId);

            GatewayProperties.GatewayRoutingProperties gwProps = props.getRouting().getGateway();
            gwProps.setScheme("https");
            gwProps.setDefaultPort(port);
            gwProps.getTls().setEnabled(true);
            gwProps.getTls().setCaCertPath(materials.caPem().toString());
            gwProps.getTls().setClientCertPath(materials.clientCertPem().toString());
            gwProps.getTls().setClientKeyPath(materials.clientKeyPem().toString());
            gwProps.getTls().setHostnameVerificationEnabled(true);

            GatewayConfiguration config = new GatewayConfiguration();
            OwnershipResolver ownership = config.ownershipResolver(props);
            WaterfallRoutingResolver resolver = config.waterfallRoutingResolver(
                    props, ownership, emptyProvider(), RoutingMetricsListener.NOOP);
            GatewayHttpTransport transport = config.gatewayHttpTransport(props);
            assertThat(transport).isInstanceOf(ReactorNettyGatewayHttpTransport.class);

            GatewayForwarder forwarder = config.gatewayForwarder(
                    props, resolver, transport, new InMemoryIdempotencyStore());

            ForwardRequest request = ForwardRequest.ofBytes(
                    "POST",
                    "/api/v1/memories/remember",
                    Map.of("Content-Type", List.of("application/json")),
                    "{\"content\":\"netty-mtls\"}".getBytes(StandardCharsets.UTF_8),
                    null
            );

            ForwardResponse response = forwarder.forward(RoutingKey.ofUntenanted("default", "ns-netty"), request);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("netty-mtls-ok");
            assertThat(observedClientDn.get()).contains("CN=gateway-client");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("ReactorNettyGatewayHttpTransport enforces CA bundle, hostname verification, and fails closed on TLS misconfiguration")
    void reactorNettyTransportEnforcesHostnameVerificationAndFailsClosed(@TempDir Path tempDir) throws Exception {
        MtlsMaterials trusted = generateMaterials(tempDir.resolve("trusted"), "SAN=ip:127.0.0.1,dns:localhost");
        MtlsMaterials untrusted = generateMaterials(tempDir.resolve("untrusted"), "SAN=ip:127.0.0.1,dns:localhost");
        MtlsMaterials wrongHost = generateMaterials(tempDir.resolve("wrong-host"), "SAN=dns:wrong-owner.spector.internal");

        // Fail closed in GatewayConfiguration when TLS config is invalid (must NOT silently fall back to plaintext HTTP)
        GatewayProperties badProps = new GatewayProperties();
        badProps.getRouting().getGateway().getTls().setEnabled(true);
        badProps.getRouting().getGateway().getTls().setCaCertPath(tempDir.resolve("nonexistent-ca.pem").toString());
        GatewayConfiguration config = new GatewayConfiguration();
        assertThatThrownBy(() -> config.gatewayHttpTransport(badProps))
                .isInstanceOf(SpectorConfigException.class);

        // Untrusted server rejected
        HttpsServer mtlsServer = startMtlsHttpsServer(trusted.serverSslContext(), true, new AtomicReference<>());
        try {
            int port = mtlsServer.getAddress().getPort();
            GatewayTlsConfig wrongCaTls = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(untrusted.caPem())
                    .clientCertPath(trusted.clientCertPem())
                    .clientKeyPath(trusted.clientKeyPem())
                    .hostnameVerificationEnabled(true)
                    .build();
            ReactorNettyGatewayHttpTransport transport = new ReactorNettyGatewayHttpTransport(
                    Duration.ofSeconds(5), Duration.ofSeconds(5), wrongCaTls);

            ForwardRequest req = ForwardRequest.ofBytes("GET", "/api/v1/memories/stats", Map.of(), new byte[0], null);
            assertThatThrownBy(() -> transport.send("node-1", "https://127.0.0.1:" + port, req, Map.of()))
                    .isInstanceOf(Exception.class);
        } finally {
            mtlsServer.stop(0);
        }

        // Hostname verification mismatch rejected when enabled, accepted when disabled
        HttpsServer wrongHostServer = startMtlsHttpsServer(wrongHost.serverSslContext(), false, new AtomicReference<>());
        try {
            int port = wrongHostServer.getAddress().getPort();
            ForwardRequest req = ForwardRequest.ofBytes("GET", "/api/v1/memories/stats", Map.of(), new byte[0], null);

            GatewayTlsConfig strictTls = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(wrongHost.caPem())
                    .hostnameVerificationEnabled(true)
                    .build();
            ReactorNettyGatewayHttpTransport strictTransport = new ReactorNettyGatewayHttpTransport(
                    Duration.ofSeconds(5), Duration.ofSeconds(5), strictTls);

            assertThatThrownBy(() -> strictTransport.send("node-1", "https://127.0.0.1:" + port, req, Map.of()))
                    .isInstanceOf(Exception.class);

            GatewayTlsConfig relaxedTls = GatewayTlsConfig.builder()
                    .enabled(true)
                    .scheme("https")
                    .caCertPath(wrongHost.caPem())
                    .hostnameVerificationEnabled(false)
                    .build();
            ReactorNettyGatewayHttpTransport relaxedTransport = new ReactorNettyGatewayHttpTransport(
                    Duration.ofSeconds(5), Duration.ofSeconds(5), relaxedTls);

            ForwardResponse okResp = relaxedTransport.send("node-1", "https://127.0.0.1:" + port, req, Map.of());
            assertThat(okResp.statusCode()).isEqualTo(200);
        } finally {
            wrongHostServer.stop(0);
        }
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) { return null; }
            @Override
            public T getIfAvailable() { return null; }
            @Override
            public T getIfUnique() { return null; }
            @Override
            public T getObject() { return null; }
        };
    }

    private static HttpsServer startMtlsHttpsServer(
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
            byte[] resp = "{\"status\":\"netty-mtls-ok\"}".getBytes(StandardCharsets.UTF_8);
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
            Path caPem,
            Path clientCertPem,
            Path clientKeyPem,
            SSLContext serverSslContext
    ) {}

    private static MtlsMaterials generateMaterials(Path dir, String sanExt) throws Exception {
        Files.createDirectories(dir);
        Path serverKsPath = dir.resolve("server.p12");
        Path serverCertPath = dir.resolve("server.crt");
        Path serverTsPath = dir.resolve("server-trust.p12");
        Path clientKsPath = dir.resolve("client.p12");
        Path clientCertPath = dir.resolve("client.crt");

        String javaHome = System.getProperty("java.home");
        String keytoolBin = Path.of(javaHome, "bin", "keytool").toString();

        runKeytool(keytoolBin, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "PKCS12", "-keystore", serverKsPath.toString(), "-storepass", "changeit",
                "-validity", "30", "-dname", "CN=localhost, OU=Spector, O=Spectrayan, C=US",
                "-ext", sanExt);
        runKeytool(keytoolBin, "-exportcert", "-alias", "server", "-keystore", serverKsPath.toString(),
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

        return new MtlsMaterials(caPem, clientCertPem, clientKeyPem, serverSsl);
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
