/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.PiiScrubber;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: PII scrubbing in the ingestion pipeline.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>Text containing SSNs, emails, and credit card numbers is ingested</li>
 *   <li>PII patterns are redacted BEFORE embedding and storage</li>
 *   <li>Stored content in memory does NOT contain raw PII</li>
 *   <li>Non-PII content is preserved and recallable</li>
 * </ul>
 */
class PiiScrubE2ETest {

    private static final int DIMS = 384;

    private StubEmbeddingProvider embeddingProvider;
    private SpectorMemory memory;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .persistenceMode(com.spectrayan.spector.memory.model.MemoryPersistenceMode.IN_MEMORY)
                .build();

        IngestionTarget target = memory.target();
        sink = new SpectorIngestionSink(target, embeddingProvider, new InMemoryExecutionLogger());

        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (memory != null) memory.close();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PII Scrubbing: SSN Removal
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: SSN patterns are scrubbed before storage in memory")
    void ssnIsScrubbed() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-pii-ssn", "PII SSN", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        String contentWithSsn = "Employee record: John Smith, SSN 123-45-6789, " +
                "hired on 2025-01-15 as Senior Engineer in the Platform team.";

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-pii-ssn", contentWithSsn,
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "employee-record",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-pii-ssn"
                ));
        producer.close();

        assertThat(sink.totalProcessed()).isEqualTo(1);

        // Verify the document is in memory — find the one containing "Senior Engineer"
        List<CognitiveResult> results = memory.recall("employee Senior Engineer Platform");
        assertThat(results).isNotEmpty();

        // Find our specific document among results
        Optional<String> ourDoc = results.stream()
                .map(CognitiveResult::text)
                .filter(t -> t.contains("Senior Engineer"))
                .findFirst();
        assertThat(ourDoc).isPresent();

        // Verify the raw SSN is NOT in the stored content
        assertThat(ourDoc.get()).doesNotContain("123-45-6789");
    }

    // ═══════════════════════════════════════════════════════════════
    //  PII Scrubbing: Email Removal
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Email addresses are scrubbed before storage in memory")
    void emailIsScrubbed() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-pii-email", "PII Email", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        String contentWithEmail = "Contact the architecture team at arch-team@spectrayan.com " +
                "for questions about the cognitive memory integration design.";

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-pii-email", contentWithEmail,
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "contact-info",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-pii-email"
                ));
        producer.close();

        assertThat(sink.totalProcessed()).isEqualTo(1);

        List<CognitiveResult> results = memory.recall("cognitive memory integration design");
        System.out.println("DEBUG RECALL RESULTS: " + results.stream().map(CognitiveResult::text).toList());
        assertThat(results).isNotEmpty();

        // Find our document
        Optional<String> ourDoc = results.stream()
                .map(CognitiveResult::text)
                .filter(t -> t.contains("cognitive memory"))
                .findFirst();
        assertThat(ourDoc).isPresent();

        assertThat(ourDoc.get()).doesNotContain("arch-team@spectrayan.com");
    }

    // ═══════════════════════════════════════════════════════════════
    //  PII Scrubbing: Credit Card Removal
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Credit card numbers are scrubbed before storage in memory")
    void creditCardIsScrubbed() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-pii-cc", "PII CC", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        String contentWithCC = "Payment processed: card 4111-1111-1111-1111, " +
                "amount $2,500.00 for enterprise license renewal.";

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-pii-cc", contentWithCC,
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "payment-record",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-pii-cc"
                ));
        producer.close();

        assertThat(sink.totalProcessed()).isEqualTo(1);

        List<CognitiveResult> results = memory.recall("enterprise license renewal payment");
        assertThat(results).isNotEmpty();

        // Find our specific document — it should contain "enterprise license" (PII-scrubbed version)
        Optional<String> ourDoc = results.stream()
                .map(CognitiveResult::text)
                .filter(t -> t.contains("enterprise license"))
                .findFirst();
        assertThat(ourDoc).isPresent();

        // Verify PII was scrubbed
        assertThat(ourDoc.get()).doesNotContain("4111-1111-1111-1111");
        assertThat(ourDoc.get()).doesNotContain("4111111111111111");
    }

    // ═══════════════════════════════════════════════════════════════
    //  PII Scrubbing: Multiple PII Types in One Document
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Document with multiple PII types has all patterns scrubbed")
    void multiplePiiTypesScrubbed() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-pii-multi", "PII Multi", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        String contentWithMultiplePii = """
                Customer onboarding record:
                Name: Jane Doe
                Email: jane.doe@example.com
                SSN: 987-65-4321
                Credit Card: 5500-0000-0000-0004
                Role: Chief Technology Officer at Acme Corp
                Notes: Interested in cognitive memory platform for knowledge management.
                """;

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-pii-multi", contentWithMultiplePii,
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "onboarding-record",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-pii-multi"
                ));
        producer.close();

        assertThat(sink.totalProcessed()).isEqualTo(1);

        List<CognitiveResult> results = memory.recall("Chief Technology Officer knowledge management");
        assertThat(results).isNotEmpty();

        // Find our document
        Optional<String> ourDoc = results.stream()
                .map(CognitiveResult::text)
                .filter(t -> t.contains("Chief Technology Officer"))
                .findFirst();
        assertThat(ourDoc).isPresent();

        // All PII should be scrubbed
        String stored = ourDoc.get();
        assertThat(stored).doesNotContain("jane.doe@example.com");
        assertThat(stored).doesNotContain("987-65-4321");
        assertThat(stored).doesNotContain("5500-0000-0000-0004");

        // Non-PII business content should be preserved
        assertThat(stored).contains("cognitive memory");
    }

    // ═══════════════════════════════════════════════════════════════
    //  PII Scrubber Standalone Unit Check
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PiiScrubber: Verify scrubbing happens at the scrubber level")
    void piiScrubberDirectly() {
        String input = "Call John at 555-12-3456 or email john@test.com, card 4111-1111-1111-1111.";
        String scrubbed = PiiScrubber.scrub(input);

        assertThat(scrubbed).doesNotContain("555-12-3456");
        assertThat(scrubbed).doesNotContain("john@test.com");
        assertThat(scrubbed).doesNotContain("4111-1111-1111-1111");
        // Should contain redaction markers
        assertThat(scrubbed).contains("[REDACTED");
    }
}
