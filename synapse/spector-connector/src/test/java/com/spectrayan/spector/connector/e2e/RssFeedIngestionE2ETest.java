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
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration tests for RSS & Atom news feed ingestion (rss)
 * into Spector Cognitive Memory via Apache Camel and SpectorIngestionSink.
 */
@DisplayName("RssFeedIngestionE2ETest — RSS & Atom Feed E2E Ingestion")
class RssFeedIngestionE2ETest {

    private static final int DIMS = 64;

    private HttpServer mockHttpServer;
    private int mockPort;
    private SpectorMemory memory;
    private StubEmbeddingProvider embeddingProvider;
    private SpectorIngestionSink sink;
    private InMemoryExecutionLogger executionLogger;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setup() throws Exception {
        // 1. Start embedded HTTP server on ephemeral port serving RSS 2.0 XML
        mockHttpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockPort = mockHttpServer.getAddress().getPort();

        mockHttpServer.createContext("/feeds/security-bulletins.xml", exchange -> {
            String rssFeedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<rss version=\"2.0\">\n" +
                    "  <channel>\n" +
                    "    <title>Spectrayan Security Bulletins</title>\n" +
                    "    <link>https://security.spectrayan.com</link>\n" +
                    "    <description>Critical Security Updates and CVE Advisory Feed</description>\n" +
                    "    <item>\n" +
                    "      <title>CVE-2026-9042: Remote Code Execution In Unvalidated Webhook Payloads</title>\n" +
                    "      <link>https://security.spectrayan.com/advisories/CVE-2026-9042</link>\n" +
                    "      <description>Flaw in HMAC header verification allows forged requests. Patch immediately by upgrading to spector 0.1.0.</description>\n" +
                    "      <pubDate>Mon, 18 Aug 2026 12:00:00 GMT</pubDate>\n" +
                    "    </item>\n" +
                    "    <item>\n" +
                    "      <title>CVE-2026-8819: Timing Attack In Memory Graph Associativity Edges</title>\n" +
                    "      <link>https://security.spectrayan.com/advisories/CVE-2026-8819</link>\n" +
                    "      <description>Hebbian graph traversal side-channel leaks relation topology. Constant-time traversal implemented in insula kernel.</description>\n" +
                    "      <pubDate>Mon, 18 Aug 2026 14:30:00 GMT</pubDate>\n" +
                    "    </item>\n" +
                    "  </channel>\n" +
                    "</rss>";
            byte[] bytes = rssFeedXml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/rss+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        mockHttpServer.start();

        // 2. Start Spector Memory
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .build();

        // 3. Configure Ingestion Target & Sink
        IngestionTarget target = memory.target();
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);

        // 4. Initialize Camel Connector Engine
        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (memory != null) memory.close();
        if (mockHttpServer != null) mockHttpServer.stop(0);
    }

    @Test
    @DisplayName("E2E: RSS feed polling ingests security advisory items and agent recalls CVE details")
    void rssFeedIngestionAndRecall() throws Exception {
        String feedUrl = "http://localhost:" + mockPort + "/feeds/security-bulletins.xml";

        RouteConfig rssConfig = RouteConfig.builder("e2e-rss-security", "Security Advisory RSS", "rss")
                .tenantId("security-team")
                .properties(Map.of(
                        "feedUrl", feedUrl,
                        "pollIntervalMs", "1000",
                        "splitEntries", "true",
                        "collection", "cve-bulletins"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(rssConfig);

        // Wait for Camel to poll RSS feed and ingest both split entries
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(2));

        assertThat(sink.totalErrors()).isZero();

        // Verify cognitive recall surfaces CVE-2026-9042 webhook flaw
        var webhookRecall = memory.recall("CVE-2026-9042 HMAC header remote code execution");
        assertThat(webhookRecall).isNotEmpty();
        assertThat(webhookRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("CVE-2026-9042") && text.contains("HMAC header"));

        // Verify cognitive recall surfaces CVE-2026-8819 graph timing attack
        var graphRecall = memory.recall("CVE-2026-8819 timing attack Hebbian graph insula");
        assertThat(graphRecall).isNotEmpty();
        assertThat(graphRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("CVE-2026-8819") && text.contains("Hebbian graph"));
    }
}
