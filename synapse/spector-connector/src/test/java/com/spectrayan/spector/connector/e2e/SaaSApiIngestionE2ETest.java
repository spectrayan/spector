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
 * End-to-end integration tests for enterprise SaaS connectors (Jira, Confluence, GitHub, Notion)
 * into Spector Cognitive Memory via Apache Camel, JSONPath item splitting, and SpectorIngestionSink.
 */
@DisplayName("SaaSApiIngestionE2ETest — SaaS APIs (Jira, Confluence, GitHub, Notion) E2E Ingestion")
class SaaSApiIngestionE2ETest {

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
        // 1. Start embedded HTTP server on ephemeral port simulating SaaS REST endpoints
        mockHttpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockPort = mockHttpServer.getAddress().getPort();

        // ── Jira Cloud Search API Mock ──
        mockHttpServer.createContext("/rest/api/3/search", exchange -> {
            String jiraResponse = "{\n" +
                    "  \"total\": 2,\n" +
                    "  \"issues\": [\n" +
                    "    {\n" +
                    "      \"key\": \"SPEC-1042\",\n" +
                    "      \"fields\": {\n" +
                    "        \"summary\": \"Deadlock in Insula memory cluster sync under high QPS write bursts\",\n" +
                    "        \"description\": \"Virtual thread lock pinning detected in CheckpointDaemon when acquiring off-heap segment lock.\",\n" +
                    "        \"status\": { \"name\": \"IN_PROGRESS\" }\n" +
                    "      }\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"key\": \"SPEC-1043\",\n" +
                    "      \"fields\": {\n" +
                    "        \"summary\": \"Implement Bloom filter synaptic tagging for fast O(1) tag membership testing\",\n" +
                    "        \"description\": \"Replace linear string array tag scans with a 64-bit SIMD Bloom filter hash in CognitiveRecord.\",\n" +
                    "        \"status\": { \"name\": \"DONE\" }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, jiraResponse);
        });

        // ── Confluence Cloud Content API Mock ──
        mockHttpServer.createContext("/wiki/rest/api/content", exchange -> {
            String confluenceResponse = "{\n" +
                    "  \"size\": 1,\n" +
                    "  \"results\": [\n" +
                    "    {\n" +
                    "      \"id\": \"conf-99120\",\n" +
                    "      \"title\": \"Spector On-Premise High Availability Clustering Architecture\",\n" +
                    "      \"body\": {\n" +
                    "        \"storage\": {\n" +
                    "          \"value\": \"<p>Spector HA clusters utilize Raft consensus over gRPC transport. Nodes replicate WAL events synchronously to quorum.</p>\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, confluenceResponse);
        });

        // ── GitHub Commits API Mock ──
        mockHttpServer.createContext("/repos/spectrayan/spector/commits", exchange -> {
            String githubResponse = "[\n" +
                    "  {\n" +
                    "    \"sha\": \"a1b2c3d4e5f67890\",\n" +
                    "    \"commit\": {\n" +
                    "      \"message\": \"feat(synapse): add INT8 scalar quantization to SIMD memory tier\",\n" +
                    "      \"author\": { \"name\": \"Forge\", \"email\": \"forge@spectrayan.com\" }\n" +
                    "    }\n" +
                    "  }\n" +
                    "]";
            respondJson(exchange, githubResponse);
        });

        // ── Notion Database Query API Mock ──
        mockHttpServer.createContext("/v1/databases/eng-roadmap/query", exchange -> {
            String notionResponse = "{\n" +
                    "  \"results\": [\n" +
                    "    {\n" +
                    "      \"id\": \"notion-card-401\",\n" +
                    "      \"properties\": {\n" +
                    "        \"Task\": { \"title\": [{ \"plain_text\": \"Autonomous Multi-Agent Handover Protocol v2\" }] },\n" +
                    "        \"Status\": { \"select\": { \"name\": \"Q3 Deliverable\" } }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, notionResponse);
        });

        // ── Salesforce SOQL Query Mock ──
        mockHttpServer.createContext("/services/data/v58.0/query", exchange -> {
            String sfdcResponse = "{\n" +
                    "  \"totalSize\": 1,\n" +
                    "  \"done\": true,\n" +
                    "  \"records\": [\n" +
                    "    {\n" +
                    "      \"Id\": \"500xx0000001AAA\",\n" +
                    "      \"CaseNumber\": \"00049210\",\n" +
                    "      \"Subject\": \"Enterprise License Renewal for HNSW SIMD Module\",\n" +
                    "      \"Description\": \"Customer requested 50-node cluster license expansion for vector memory.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, sfdcResponse);
        });

        // ── Google Drive API Mock ──
        mockHttpServer.createContext("/drive/v3/files", exchange -> {
            String gdriveResponse = "{\n" +
                    "  \"files\": [\n" +
                    "    {\n" +
                    "      \"id\": \"file-gdrive-8812\",\n" +
                    "      \"name\": \"Spectrayan_AI_Governance_Playbook_2026.pdf\",\n" +
                    "      \"mimeType\": \"application/pdf\",\n" +
                    "      \"description\": \"Enterprise AI memory governance, ethical soul guardrails, and compliance.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, gdriveResponse);
        });

        // ── SharePoint Graph API Mock ──
        mockHttpServer.createContext("/v1.0/sites/eng-site/drive/root/children", exchange -> {
            String sharepointResponse = "{\n" +
                    "  \"value\": [\n" +
                    "    {\n" +
                    "      \"id\": \"sp-item-7741\",\n" +
                    "      \"name\": \"Zero_Trust_Memory_Isolation_Spec.docx\",\n" +
                    "      \"description\": \"Multi-tenant memory partitioning and partition bundle isolation standard.\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";
            respondJson(exchange, sharepointResponse);
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
    @DisplayName("E2E: Jira Cloud issues ingestion via JSONPath splitting and agent cognitive recall")
    void jiraIngestionAndRecall() throws Exception {
        RouteConfig jiraConfig = RouteConfig.builder("e2e-jira", "Jira Bug Tracker", "jira")
                .tenantId("engineering-org")
                .properties(Map.of(
                        "scheme", "http",
                        "host", "localhost:" + mockPort,
                        "projectKey", "SPEC",
                        "username", "bot@spectrayan.com",
                        "apiToken", "secret-token",
                        "pollIntervalMs", "1000",
                        "collection", "jira-tickets"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(jiraConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(2));

        assertThat(sink.totalErrors()).isZero();

        // Recall SPEC-1042 virtual thread lock pinning
        var jiraRecall1 = memory.recall("SPEC-1042 CheckpointDaemon off-heap segment lock");
        assertThat(jiraRecall1).isNotEmpty();
        assertThat(jiraRecall1)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("SPEC-1042") && text.contains("Virtual thread lock"));

        // Recall SPEC-1043 Bloom filter synaptic tagging
        var jiraRecall2 = memory.recall("SPEC-1043 Bloom filter synaptic tagging SIMD");
        assertThat(jiraRecall2).isNotEmpty();
        assertThat(jiraRecall2)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("SPEC-1043") && text.contains("Bloom filter"));
    }

    @Test
    @DisplayName("E2E: Confluence Cloud space documentation ingestion and agent cognitive recall")
    void confluenceIngestionAndRecall() throws Exception {
        RouteConfig confConfig = RouteConfig.builder("e2e-confluence", "Confluence Architecture Wiki", "confluence")
                .tenantId("engineering-org")
                .properties(Map.of(
                        "scheme", "http",
                        "host", "localhost:" + mockPort,
                        "spaceKey", "ARCH",
                        "username", "bot@spectrayan.com",
                        "apiToken", "secret-token",
                        "pollIntervalMs", "1000",
                        "collection", "wiki-spaces"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(confConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var confRecall = memory.recall("Spector HA Clustering Raft consensus gRPC");
        assertThat(confRecall).isNotEmpty();
        assertThat(confRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Raft consensus") && text.contains("WAL events"));
    }

    @Test
    @DisplayName("E2E: GitHub commits stream ingestion and agent cognitive recall")
    void gitHubIngestionAndRecall() throws Exception {
        RouteConfig ghConfig = RouteConfig.builder("e2e-github", "GitHub Repository Commits", "github-ingest")
                .tenantId("engineering-org")
                .properties(Map.of(
                        "apiBaseUrl", "http://localhost:" + mockPort,
                        "repo", "spectrayan/spector",
                        "branch", "main",
                        "oauthToken", "ghp_fake_token",
                        "pollIntervalMs", "1000",
                        "collection", "git-commits"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(ghConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var ghRecall = memory.recall("INT8 scalar quantization SIMD memory tier Forge");
        assertThat(ghRecall).isNotEmpty();
        assertThat(ghRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("INT8 scalar quantization") && text.contains("Forge"));
    }

    @Test
    @DisplayName("E2E: Notion database pages ingestion and agent cognitive recall")
    void notionIngestionAndRecall() throws Exception {
        RouteConfig notionConfig = RouteConfig.builder("e2e-notion", "Notion Engineering Roadmap", "notion-pages")
                .tenantId("engineering-org")
                .properties(Map.of(
                        "apiBaseUrl", "http://localhost:" + mockPort,
                        "databaseId", "eng-roadmap",
                        "apiKey", "secret_notion_key",
                        "pollIntervalMs", "1000",
                        "collection", "roadmap"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(notionConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var notionRecall = memory.recall("Autonomous Multi-Agent Handover Protocol Q3");
        assertThat(notionRecall).isNotEmpty();
        assertThat(notionRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Autonomous Multi-Agent Handover Protocol") && text.contains("Q3 Deliverable"));
    }

    @Test
    @DisplayName("E2E: Salesforce SOQL query ingestion and agent cognitive recall")
    void salesforceIngestionAndRecall() throws Exception {
        RouteConfig sfdcConfig = RouteConfig.builder("e2e-salesforce", "Salesforce Cases", "salesforce")
                .tenantId("sales-org")
                .properties(Map.of(
                        "instanceUrl", "http://localhost:" + mockPort,
                        "soqlQuery", "SELECT Id, Name, Description FROM Case",
                        "pollIntervalMs", "1000",
                        "collection", "crm-cases"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(sfdcConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var sfdcRecall = memory.recall("Enterprise License Renewal HNSW SIMD 50-node cluster");
        assertThat(sfdcRecall).isNotEmpty();
        assertThat(sfdcRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Enterprise License Renewal") && text.contains("50-node"));
    }

    @Test
    @DisplayName("E2E: Google Drive shared files polling and agent cognitive recall")
    void googleDriveIngestionAndRecall() throws Exception {
        RouteConfig driveConfig = RouteConfig.builder("e2e-gdrive", "Google Drive Shared Folder", "google-drive")
                .tenantId("compliance-org")
                .properties(Map.of(
                        "driveApiUrl", "http://localhost:" + mockPort + "/drive/v3",
                        "folderId", "folder-governance-001",
                        "pollIntervalMs", "1000",
                        "collection", "governance-files"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(driveConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var gdriveRecall = memory.recall("Spectrayan AI Governance Playbook ethical soul guardrails");
        assertThat(gdriveRecall).isNotEmpty();
        assertThat(gdriveRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Spectrayan_AI_Governance_Playbook_2026.pdf") && text.contains("ethical soul guardrails"));
    }

    @Test
    @DisplayName("E2E: SharePoint document library polling and agent cognitive recall")
    void sharepointIngestionAndRecall() throws Exception {
        RouteConfig spConfig = RouteConfig.builder("e2e-sharepoint", "SharePoint Engineering Specs", "sharepoint")
                .tenantId("engineering-org")
                .properties(Map.of(
                        "graphApiUrl", "http://localhost:" + mockPort + "/v1.0",
                        "siteId", "eng-site",
                        "pollIntervalMs", "1000",
                        "collection", "specs"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(spConfig);

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        var spRecall = memory.recall("Zero Trust Memory Isolation Spec multi-tenant partitioning");
        assertThat(spRecall).isNotEmpty();
        assertThat(spRecall)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Zero_Trust_Memory_Isolation_Spec.docx") && text.contains("Multi-tenant memory partitioning"));
    }

    private void respondJson(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
