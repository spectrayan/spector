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
import com.spectrayan.spector.memory.model.CognitiveResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test: External Database (H2) → Apache Camel db-query Route
 * → SpectorIngestionSink → Spector Memory → Agent Recall.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>An external SQL database (simulated with standalone H2) contains multiple tables:
 *       Knowledge Base solutions and multi-turn Conversation logs.</li>
 *   <li>Camel dynamic 'db-query' routes poll the external database via JDBC.</li>
 *   <li>Row-level splitting breaks each table result set into individual documents.</li>
 *   <li>Query results flow through PII scrubbing, embedding, and cognitive ingestion.</li>
 *   <li>Data lands in Spector Memory tiers (Working, Episodic, Semantic) with HNSW + BM25 indexing.</li>
 *   <li>The Agent queries Spector Memory and successfully pulls specific rows and conversational turns.</li>
 * </ul>
 */
class DatabaseQueryIngestionE2ETest {

    private static final int DIMS = 384;
    private static final String EXTERNAL_DB_URL = "jdbc:h2:mem:external_enterprise_db;DB_CLOSE_DELAY=-1";

    private StubEmbeddingProvider embeddingProvider;
    private SpectorMemory memory;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;
    private InMemoryExecutionLogger executionLogger;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Setup External System (H2 Database) with sample enterprise data (KB and Conversations)
        try (Connection conn = DriverManager.getConnection(EXTERNAL_DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            // Knowledge Base table
            stmt.execute("DROP TABLE IF EXISTS KNOWLEDGE_ARTICLES");
            stmt.execute("CREATE TABLE KNOWLEDGE_ARTICLES (" +
                    "id INT PRIMARY KEY, " +
                    "title VARCHAR(255), " +
                    "category VARCHAR(100), " +
                    "solution VARCHAR(1000)" +
                    ")");
            stmt.execute("INSERT INTO KNOWLEDGE_ARTICLES VALUES (" +
                    "1, " +
                    "'PostgreSQL Deadlock In High Concurrency Transactions', " +
                    "'Database', " +
                    "'Apply row-level locking with SELECT FOR UPDATE and enforce consistent lock ordering across transactions.'" +
                    ")");
            stmt.execute("INSERT INTO KNOWLEDGE_ARTICLES VALUES (" +
                    "2, " +
                    "'Kubernetes CrashLoopBackOff Pod Remediation Guide', " +
                    "'DevOps', " +
                    "'Check OOMKilled exit code 137 via kubectl describe pod and increase container memory limits in values.yaml.'" +
                    ")");
            stmt.execute("INSERT INTO KNOWLEDGE_ARTICLES VALUES (" +
                    "3, " +
                    "'Redis Allkeys-LRU Memory Eviction Behavior', " +
                    "'Caching', " +
                    "'Configure maxmemory-policy allkeys-lru to automatically evict least recently used keys when memory limit is reached.'" +
                    ")");

            // Conversation History table
            stmt.execute("DROP TABLE IF EXISTS CONVERSATION_HISTORY");
            stmt.execute("CREATE TABLE CONVERSATION_HISTORY (" +
                    "session_id VARCHAR(50), " +
                    "turn_id INT, " +
                    "speaker VARCHAR(50), " +
                    "utterance VARCHAR(1000), " +
                    "PRIMARY KEY (session_id, turn_id)" +
                    ")");
            stmt.execute("INSERT INTO CONVERSATION_HISTORY VALUES (" +
                    "'sess-001', 1, 'USER', " +
                    "'How do we take screenshots of the prompt editing page with Playwright in our Angular app?'" +
                    ")");
            stmt.execute("INSERT INTO CONVERSATION_HISTORY VALUES (" +
                    "'sess-001', 2, 'ASSISTANT', " +
                    "'Angular router guards check localStorage during bootstrap. To avoid race conditions in Playwright, set localStorage before navigating or wait for project selector initialization.'" +
                    ")");
            stmt.execute("INSERT INTO CONVERSATION_HISTORY VALUES (" +
                    "'sess-002', 1, 'USER', " +
                    "'What about the ABA therapy assistant prompt HIPAA compliance rules?'" +
                    ")");
            stmt.execute("INSERT INTO CONVERSATION_HISTORY VALUES (" +
                    "'sess-002', 2, 'ASSISTANT', " +
                    "'HIPAA compliance annotations and vulnerability scan metadata must be attached to the draft-review-approve workflow states.'" +
                    ")");
        }

        // 2. Real SpectorMemory with deterministic embedding provider
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .build();

        // 3. Real IngestionTarget from SpectorMemory
        IngestionTarget target = memory.target();

        // 4. Real SpectorIngestionSink
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);

        // 5. Real TemplateRegistry & CamelConnectorEngine
        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (memory != null) memory.close();

        try (Connection conn = DriverManager.getConnection(EXTERNAL_DB_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS KNOWLEDGE_ARTICLES");
            stmt.execute("DROP TABLE IF EXISTS CONVERSATION_HISTORY");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("E2E: Multi-table DB Ingestion (KB Articles & Conversation Turns) → Spector Memory → Agent Recall")
    void multiTableDatabaseIngestionAndAgentRecall() throws Exception {
        // 1. Deploy Camel route for Knowledge Base table
        RouteConfig kbConfig = RouteConfig.builder("e2e-h2-kb", "External Knowledge Base", "db-query")
                .tenantId("enterprise-tenant")
                .properties(Map.of(
                        "jdbcUrl", EXTERNAL_DB_URL,
                        "username", "sa",
                        "password", "",
                        "query", "SELECT id, title, category, solution FROM KNOWLEDGE_ARTICLES",
                        "pollIntervalMs", "1000",
                        "collection", "kb-articles"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(kbConfig);

        // 2. Deploy Camel route for Conversation History table
        RouteConfig convConfig = RouteConfig.builder("e2e-h2-conv", "External Conversation Logs", "db-query")
                .tenantId("enterprise-tenant")
                .properties(Map.of(
                        "jdbcUrl", EXTERNAL_DB_URL,
                        "username", "sa",
                        "password", "",
                        "query", "SELECT session_id, turn_id, speaker, utterance FROM CONVERSATION_HISTORY",
                        "pollIntervalMs", "1000",
                        "collection", "chat-logs"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(convConfig);

        // Await Camel polling and processing all 7 rows across both tables
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(7));

        assertThat(sink.totalErrors()).isZero();
        assertThat(executionLogger.allRecords()).isNotEmpty();

        // 3. Verify Agent pulls Knowledge Base solutions on demand
        List<CognitiveResult> deadlockRecall = memory.recall("PostgreSQL deadlock high concurrency");
        assertThat(deadlockRecall).isNotEmpty();
        assertThat(deadlockRecall)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("SELECT FOR UPDATE") || text.contains("lock ordering"));

        List<CognitiveResult> k8sRecall = memory.recall("Kubernetes CrashLoopBackOff remediation");
        assertThat(k8sRecall).isNotEmpty();
        assertThat(k8sRecall)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("OOMKilled") || text.contains("kubectl describe pod"));

        List<CognitiveResult> redisRecall = memory.recall("Redis allkeys-lru memory eviction policy");
        assertThat(redisRecall).isNotEmpty();
        assertThat(redisRecall)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("allkeys-lru") || text.contains("evict least recently used"));

        // 4. Verify Agent pulls conversational context on demand
        List<CognitiveResult> playwrightRecall = memory.recall("Playwright Angular router guard race condition");
        assertThat(playwrightRecall).isNotEmpty();
        assertThat(playwrightRecall)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("localStorage") && text.contains("Playwright"));

        List<CognitiveResult> hipaaRecall = memory.recall("ABA therapy HIPAA compliance annotations");
        assertThat(hipaaRecall).isNotEmpty();
        assertThat(hipaaRecall)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("HIPAA") && text.contains("draft-review-approve"));
    }
}
