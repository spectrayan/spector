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
package com.spectrayan.spector.connector.core;

import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.ingestion.IngestionTarget;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the template-driven {@link CamelConnectorEngine}.
 *
 * <p>Tests the full Camel route lifecycle using route templates:
 * deploy → process → remove. Uses real CamelContext with in-memory SPIs
 * and mock Spector target/provider.</p>
 */
@ExtendWith(MockitoExtension.class)
class CamelConnectorEngineTest {

    @Mock private IngestionTarget target;
    @Mock private EmbeddingProvider embeddingProvider;

    private InMemoryRouteConfigProvider configProvider;
    private InMemoryExecutionLogger executionLogger;
    private SpectorIngestionSink sink;
    private TemplateRegistry templateRegistry;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setUp() {
        configProvider = new InMemoryRouteConfigProvider();
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);
        templateRegistry = new TemplateRegistry(null); // built-in only
        engine = new CamelConnectorEngine(sink, configProvider, templateRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isStarted()) {
            engine.close();
        }
    }

    // ─────────────── Lifecycle ───────────────

    @Test
    @DisplayName("Engine starts and stops cleanly")
    void startsAndStops() throws Exception {
        engine.start();
        assertThat(engine.isStarted()).isTrue();
        assertThat(engine.camelContext().getStatus()).isEqualTo(ServiceStatus.Started);

        engine.close();
        assertThat(engine.isStarted()).isFalse();
    }

    @Test
    @DisplayName("Double start is idempotent")
    void doubleStartIsIdempotent() throws Exception {
        engine.start();
        engine.start(); // Should not throw

        assertThat(engine.isStarted()).isTrue();
    }

    @Test
    @DisplayName("Close before start is safe")
    void closeBeforeStart() throws Exception {
        engine.close(); // Should not throw
    }

    // ─────────────── Route Deployment via Templates ───────────────

    @Test
    @DisplayName("Deploy a direct route template and process a message")
    void deployDirectRoute() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f, 0.2f}, 3, "test"));

        engine.start();

        var config = RouteConfig.builder("test-route", "Test Route", "direct")
                .tenantId("default")
                .build();
        engine.deployRoute(config);

        assertThat(engine.activeRouteIds()).contains("test-route");
        assertThat(engine.getRouteStatus("test-route")).isEqualTo(ServiceStatus.Started);

        // Send a message through the route
        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:test-route", "Hello Spector",
                Map.of("spector-doc-id", "doc-1"));

        // Verify ingestion happened
        verify(target, timeout(5000)).ingest(eq("doc-1"), eq("Hello Spector"), any());
        assertThat(sink.totalProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deploy route loads enabled routes on startup")
    void loadsEnabledRoutesOnStartup() throws Exception {
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        // Pre-populate config provider with enabled routes
        configProvider.save(RouteConfig.builder("startup-route", "Startup Route", "direct").build());
        configProvider.save(RouteConfig.builder("disabled-route", "Disabled", "direct")
                .enabled(false).build());

        engine.start();

        // Only enabled route should be deployed
        assertThat(engine.activeRouteIds()).contains("startup-route");
        assertThat(engine.activeRouteIds()).doesNotContain("disabled-route");
    }

    // ─────────────── Route Removal ───────────────

    @Test
    @DisplayName("Remove route stops and removes it")
    void removeRouteStopsAndRemoves() throws Exception {
        engine.start();

        var config = RouteConfig.builder("to-remove", "Temp Route", "direct").build();
        engine.deployRoute(config);
        assertThat(engine.activeRouteIds()).contains("to-remove");

        boolean removed = engine.removeRoute("to-remove");
        assertThat(removed).isTrue();
        assertThat(engine.activeRouteIds()).doesNotContain("to-remove");
    }

    @Test
    @DisplayName("Remove nonexistent route returns false")
    void removeNonexistentRoute() throws Exception {
        engine.start();

        boolean removed = engine.removeRoute("nonexistent");
        assertThat(removed).isFalse();
    }

    // ─────────────── Route Redeploy ───────────────

    @Test
    @DisplayName("Deploying same route ID replaces the existing route")
    void redeployReplacesExisting() throws Exception {
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config1 = RouteConfig.builder("dup-route", "Version 1", "direct")
                .tenantId("v1").build();
        engine.deployRoute(config1);

        var config2 = RouteConfig.builder("dup-route", "Version 2", "direct")
                .tenantId("v2").build();
        engine.deployRoute(config2);

        // Should only have one route with that ID
        assertThat(engine.activeRouteIds()).containsExactly("dup-route");
    }

    // ─────────────── Route Start/Stop ───────────────

    @Test
    @DisplayName("Stop and restart a route")
    void stopAndRestartRoute() throws Exception {
        engine.start();

        var config = RouteConfig.builder("lifecycle-route", "Lifecycle Test", "direct").build();
        engine.deployRoute(config);
        assertThat(engine.getRouteStatus("lifecycle-route")).isEqualTo(ServiceStatus.Started);

        engine.stopRoute("lifecycle-route");
        assertThat(engine.getRouteStatus("lifecycle-route")).isEqualTo(ServiceStatus.Stopped);

        engine.startRoute("lifecycle-route");
        assertThat(engine.getRouteStatus("lifecycle-route")).isEqualTo(ServiceStatus.Started);
    }

    // ─────────────── File Watch Template ───────────────

    @Test
    @DisplayName("File watch template picks up new files")
    void fileWatchPicksUpFiles() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        Path watchDir = Files.createTempDirectory("spector-test-watch");

        engine.start();

        var config = RouteConfig.builder("file-route", "File Watch", "file-watch")
                .properties(Map.of("path", watchDir.toString(), "pattern", ".*\\.txt"))
                .build();
        engine.deployRoute(config);

        // Write a file to the watched directory
        Files.writeString(watchDir.resolve("test.txt"), "Hello from file");

        // Wait for Camel to pick it up
        verify(target, timeout(10000)).ingest(eq("test.txt"), eq("Hello from file"), any());

        // Cleanup
        Files.deleteIfExists(watchDir.resolve("test.txt"));
        Files.deleteIfExists(watchDir.resolve(".camel"));
        deleteRecursive(watchDir);
    }

    // ─────────────── Template Validation ───────────────

    @Test
    @DisplayName("Unknown template throws IllegalArgumentException")
    void unknownTemplateThrows() throws Exception {
        engine.start();

        var config = RouteConfig.builder("bad-route", "Bad Route", "nonexistent-template").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> engine.deployRoute(config))
                .withMessageContaining("Template not found");
    }

    @Test
    @DisplayName("Template registry lists all 20 built-in templates")
    void templateRegistryListsBuiltIns() {
        var templates = templateRegistry.listTemplates();
        assertThat(templates).hasSize(20);
        assertThat(templates.stream().map(t -> t.templateId()))
                .contains("file-watch", "s3-poll", "rest-api-poll", "webhook-receiver",
                        "db-query", "slack-notify", "email-notify", "kafka-consumer",
                        "direct", "mongodb-poll", "github-ingest", "notion-pages", "slack-ingest",
                        "rss", "web-scraper", "confluence", "jira", "google-drive", "sharepoint", "salesforce");
    }

    // ─────────────── Error in Route Processing ───────────────

    @Test
    @DisplayName("Embedding failure in route logs error but engine stays running")
    void embeddingFailureDoesNotCrashEngine() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenThrow(new RuntimeException("Model down"));

        engine.start();

        var config = RouteConfig.builder("error-route", "Error Test", "direct").build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        try {
            producer.sendBodyAndHeader("direct:error-route", "will fail",
                    "spector-doc-id", "fail-doc");
        } catch (Exception e) {
            // Expected — Camel propagates the exception
        }

        // Engine should still be running
        assertThat(engine.isStarted()).isTrue();
        assertThat(sink.totalErrors()).isEqualTo(1);
    }

    // ─────────────── Doc ID Resolution ───────────────

    @Test
    @DisplayName("Doc ID from header is preserved by direct template")
    void docIdFromHeaderPreserved() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config = RouteConfig.builder("docid-route", "DocID Test", "direct").build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:docid-route", "content here",
                Map.of("spector-doc-id", "my-custom-doc-id"));

        verify(target, timeout(5000)).ingest(eq("my-custom-doc-id"), eq("content here"), any());
    }

    @Test
    @DisplayName("Exchange ID used as doc-id fallback in direct template")
    void exchangeIdFallback() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, 1, "m"));

        engine.start();

        var config = RouteConfig.builder("fallback-route", "Fallback Test", "direct").build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBody("direct:fallback-route", "no doc id");

        // Should ingest with some auto-generated doc ID (exchange ID)
        verify(target, timeout(5000)).ingest(argThat(id -> id != null && !id.isBlank()),
                eq("no doc id"), any());
    }

    @Test
    @DisplayName("Deploy a MongoDB polling route and ingest dynamic documents into Spector Memory")
    void deployMongoDbRouteAndIngest() throws Exception {
        // Mock Embedding with standard dimension sizing (384) to be off-heap/FFM safe
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[384], 384, "test"));

        // Mock MongoDB driver classes with custom Answers to log all invocations and isolate the issue
        var mockCollection = mock(com.mongodb.client.MongoCollection.class);
        var mockDb = mock(com.mongodb.client.MongoDatabase.class, invocation -> {
            String name = invocation.getMethod().getName();
            System.out.println("DEBUG-DB-MOCK: method=" + name + ", args=" + java.util.Arrays.toString(invocation.getArguments()));
            if ("getCollection".equals(name)) {
                return mockCollection;
            }
            if ("getName".equals(name)) {
                return "testdb";
            }
            return null;
        });
        var mockClient = mock(com.mongodb.client.MongoClient.class, invocation -> {
            String name = invocation.getMethod().getName();
            System.out.println("DEBUG-CLIENT-MOCK: method=" + name + ", args=" + java.util.Arrays.toString(invocation.getArguments()));
            if ("getDatabase".equals(name)) {
                return mockDb;
            }
            return null;
        });
        
        var mockIterable = mock(com.mongodb.client.FindIterable.class);
        var mockCursor = mock(com.mongodb.client.MongoCursor.class);
        
        org.bson.Document doc = new org.bson.Document();
        doc.put("_id", new org.bson.types.ObjectId());
        doc.put("content", "Spector MongoDB cognitive memory ingestion pipeline works!");

        when(mockCollection.find()).thenReturn(mockIterable);
        when(mockCollection.withWriteConcern(any())).thenReturn(mockCollection);
        when(mockIterable.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(true, false); // Return one doc, then stop
        when(mockCursor.next()).thenReturn(doc);
        doCallRealMethod().when(mockCursor).forEachRemaining(any());

        engine.start();

        // Pre-register our mockMongoClient bean so it's matched immediately
        engine.camelContext().getRegistry().bind("mongoClient", mockClient);

        System.out.println("DIAG-REGISTRY: Looked up mongoClient: " + engine.camelContext().getRegistry().lookupByName("mongoClient"));

        // Deploy the MongoDB route configuration
        var config = RouteConfig.builder("mongo-route", "Mongo Ingestion", "mongodb-poll")
                .tenantId("default")
                .properties(Map.of(
                        "connectionUri", "mongodb://localhost:27017",
                        "database", "testdb",
                        "collectionName", "testdocs",
                        "pollIntervalMs", "1000",
                        "collection", "cognitivedocs"
                ))
                .build();

        engine.deployRoute(config);

        assertThat(engine.activeRouteIds()).contains("mongo-route");

        // Inspect endpoints after deployment
        for (var ep : engine.camelContext().getEndpoints()) {
            System.out.println("DIAG-ENDPOINT: URI=" + ep.getEndpointUri() + ", Class=" + ep.getClass().getName());
            if (ep instanceof org.apache.camel.component.mongodb.MongoDbEndpoint mongoEp) {
                System.out.println("DIAG-ENDPOINT-MONGO: database=" + mongoEp.getDatabase() + ", collection=" + mongoEp.getCollection());
                System.out.println("DIAG-ENDPOINT-MONGO: mongoConnection=" + mongoEp.getMongoConnection());
                System.out.println("DIAG-ENDPOINT-MONGO: mongoDatabase (via getMongoDatabase())=" + mongoEp.getMongoDatabase());
            }
        }

        // Wait for Camel to poll MongoDB and process the message
        verify(target, timeout(5000)).ingest(
                argThat(id -> id != null && id.startsWith("mongodb-mongo-route")),
                eq(doc.toString()),
                any(float[].class)
        );

        assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1);
    }

    // ─────────────── Model Tests ───────────────

    @Test
    @DisplayName("RouteConfig builder creates valid config")
    void routeConfigBuilder() {
        var config = RouteConfig.builder("r1", "Route One", "direct")
                .tenantId("t1")
                .connectorType("DIRECT")
                .source("direct:test")
                .schedule("0 */5 * * *")
                .properties(Map.of("key", "value"))
                .enabled(false)
                .build();

        assertThat(config.id()).isEqualTo("r1");
        assertThat(config.name()).isEqualTo("Route One");
        assertThat(config.templateId()).isEqualTo("direct");
        assertThat(config.connectorType()).isEqualTo("DIRECT");
        assertThat(config.tenantId()).isEqualTo("t1");
        assertThat(config.enabled()).isFalse();
        assertThat(config.properties()).containsEntry("key", "value");
    }

    @Test
    @DisplayName("RouteConfig rejects blank id")
    void routeConfigRejectsBlankId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RouteConfig.builder("", "name", "template").build());
    }

    @Test
    @DisplayName("RouteConfig defaults tenantId to 'default'")
    void routeConfigDefaultsTenant() {
        var config = RouteConfig.builder("r1", "Route", "direct").build();
        assertThat(config.tenantId()).isEqualTo("default");
    }

    // ─────────────── Helpers ───────────────

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (var child : stream.toList()) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
