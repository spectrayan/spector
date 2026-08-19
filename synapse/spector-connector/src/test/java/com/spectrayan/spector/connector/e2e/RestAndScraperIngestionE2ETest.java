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
 * End-to-end integration tests for REST API polling (rest-api-poll) and Web Scraping (web-scraper)
 * into Spector Cognitive Memory via Apache Camel and SpectorIngestionSink.
 */
@DisplayName("RestAndScraperIngestionE2ETest — REST & Web Scraping E2E Ingestion")
class RestAndScraperIngestionE2ETest {

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
        // 1. Start embedded HTTP server on ephemeral port
        mockHttpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockPort = mockHttpServer.getAddress().getPort();

        // Handler for REST API polling
        mockHttpServer.createContext("/api/v1/system-telemetry", exchange -> {
            String jsonResponse = "{\n" +
                    "  \"status\": \"HEALTHY\",\n" +
                    "  \"cluster\": \"spectrayan-us-east\",\n" +
                    "  \"metrics\": {\n" +
                    "    \"qps\": 42500,\n" +
                    "    \"p99LatencyMs\": 1.4,\n" +
                    "    \"gcPauseMs\": 0.2\n" +
                    "  },\n" +
                    "  \"activeNodes\": [\"node-alpha\", \"node-beta\", \"node-gamma\"]\n" +
                    "}";
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Handler for Web Scraping
        mockHttpServer.createContext("/docs/architecture-guide.html", exchange -> {
            String htmlResponse = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head><title>Spector Memory Architecture Guide</title></head>\n" +
                    "<body>\n" +
                    "  <h1>Spector Cognitive Memory System</h1>\n" +
                    "  <p>The Spector memory subsystem uses biologically inspired cognitive architectures: " +
                    "Working, Episodic, Semantic, and Procedural memory tiers. " +
                    "Off-heap memory allocation utilizes high-performance SIMD vector quantization.</p>\n" +
                    "</body>\n" +
                    "</html>";
            byte[] bytes = htmlResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
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
    @DisplayName("E2E: REST API polling ingests JSON metrics and agent recalls cluster health")
    void restApiPollIngestionAndRecall() throws Exception {
        String restUrl = "http://localhost:" + mockPort + "/api/v1/system-telemetry";

        RouteConfig restConfig = RouteConfig.builder("e2e-rest-poll", "Telemetry REST Poll", "rest-api-poll")
                .tenantId("infra-team")
                .properties(Map.of(
                        "url", restUrl,
                        "method", "GET",
                        "authHeader", "Bearer test-api-token-secret-12345",
                        "pollIntervalMs", "1000",
                        "collection", "system-telemetry"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(restConfig);

        // Wait for Camel to poll REST endpoint and ingest into sink
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        // Verify cognitive recall surfaces telemetry metrics
        var recallResults = memory.recall("cluster spectrayan-us-east qps latency");
        assertThat(recallResults).isNotEmpty();
        assertThat(recallResults)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("spectrayan-us-east") && text.contains("42500"));
    }

    @Test
    @DisplayName("E2E: Web Scraper ingests HTML content and agent recalls architectural facts")
    void webScraperIngestionAndRecall() throws Exception {
        String pageUrl = "http://localhost:" + mockPort + "/docs/architecture-guide.html";

        RouteConfig scraperConfig = RouteConfig.builder("e2e-web-scraper", "Architecture Docs Scraper", "web-scraper")
                .tenantId("docs-team")
                .properties(Map.of(
                        "startUrl", pageUrl,
                        "pollIntervalMs", "1000",
                        "collection", "architecture-guides"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(scraperConfig);

        // Wait for Camel to scrape page and ingest into sink
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        // Verify cognitive recall surfaces scraped HTML knowledge
        var recallResults = memory.recall("cognitive architecture off-heap SIMD vector quantization");
        assertThat(recallResults).isNotEmpty();
        assertThat(recallResults)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Spector Cognitive Memory System") && text.contains("SIMD"));
    }
}
