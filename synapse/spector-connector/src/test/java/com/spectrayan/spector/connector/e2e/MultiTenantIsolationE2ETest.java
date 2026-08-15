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
import com.spectrayan.spector.connector.sink.BatchIngestionRegistry;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.sink.TenantMemoryRegistry;
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
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: Multi-tenant data isolation.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>Two tenants ingest documents through separate routes</li>
 *   <li>Each tenant's data is isolated in its own SpectorMemory workspace</li>
 *   <li>Tenant A cannot recall Tenant B's documents and vice versa</li>
 *   <li>The "default" tenant uses the global memory instance</li>
 * </ul>
 */
class MultiTenantIsolationE2ETest {

    private static final int DIMS = 384;

    private StubEmbeddingProvider embeddingProvider;
    private SpectorMemory globalMemory;
    private TenantMemoryRegistry tenantRegistry;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        globalMemory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .build();

        // Create instance-based tenant registry with temp directory
        Path tempNamespaces = java.nio.file.Files.createTempDirectory("spector-test-ns-");
        tenantRegistry = new TenantMemoryRegistry(tempNamespaces, embeddingProvider, DIMS);

        IngestionTarget target = globalMemory.target();
        sink = new SpectorIngestionSink(target, embeddingProvider, new InMemoryExecutionLogger(), null, tenantRegistry);

        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (tenantRegistry != null) tenantRegistry.close();
        if (globalMemory != null) globalMemory.close();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Tenant Isolation
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Tenant Alpha and Tenant Beta data are fully isolated")
    void tenantDataIsIsolated() throws Exception {
        // Deploy separate routes for each tenant
        RouteConfig alphaRoute = RouteConfig.builder("route-alpha", "Alpha Route", "direct")
                .tenantId("tenant-alpha")
                .enabled(true)
                .build();
        RouteConfig betaRoute = RouteConfig.builder("route-beta", "Beta Route", "direct")
                .tenantId("tenant-beta")
                .enabled(true)
                .build();

        engine.deployRoute(alphaRoute);
        engine.deployRoute(betaRoute);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();

        // Tenant Alpha ingests a document about financial data
        producer.sendBodyAndHeaders("direct:route-alpha",
                "Confidential: Alpha Corp Q3 revenue exceeded 50 million dollars with 15% growth.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "alpha-financials",
                        SpectorIngestionSink.HEADER_TENANT_ID, "tenant-alpha",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "route-alpha"
                ));

        // Tenant Beta ingests a document about product roadmap
        producer.sendBodyAndHeaders("direct:route-beta",
                "Beta Industries product roadmap includes AI-powered supply chain optimization.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "beta-roadmap",
                        SpectorIngestionSink.HEADER_TENANT_ID, "tenant-beta",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "route-beta"
                ));
        producer.close();

        // Both ingested successfully
        assertThat(sink.totalProcessed()).isEqualTo(2);

        // Tenant Alpha has its own memory workspace
        SpectorMemory alphaMemory = tenantRegistry.getMemoryForTenant("tenant-alpha");
        assertThat(alphaMemory).isNotNull();

        // Tenant Beta has its own memory workspace
        SpectorMemory betaMemory = tenantRegistry.getMemoryForTenant("tenant-beta");
        assertThat(betaMemory).isNotNull();

        // They are different instances
        assertThat(alphaMemory).isNotSameAs(betaMemory);

        // Alpha can recall its own data
        List<CognitiveResult> alphaResults = alphaMemory.recall("revenue growth financial");
        assertThat(alphaResults).isNotEmpty();

        // Beta can recall its own data
        List<CognitiveResult> betaResults = betaMemory.recall("product roadmap supply chain");
        assertThat(betaResults).isNotEmpty();

        // Cross-tenant: Alpha cannot see Beta's data
        List<CognitiveResult> alphaSearchBeta = alphaMemory.recall("product roadmap supply chain");
        // Alpha's memory should not contain Beta's document
        boolean foundBetaDoc = alphaSearchBeta.stream()
                .anyMatch(r -> r.text() != null && r.text().contains("Beta Industries"));
        assertThat(foundBetaDoc).isFalse();

        // Cross-tenant: Beta cannot see Alpha's data
        List<CognitiveResult> betaSearchAlpha = betaMemory.recall("revenue growth financial");
        boolean foundAlphaDoc = betaSearchAlpha.stream()
                .anyMatch(r -> r.text() != null && r.text().contains("Alpha Corp"));
        assertThat(foundAlphaDoc).isFalse();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Default Tenant Uses Global Memory
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Default tenant documents go to global memory")
    void defaultTenantUsesGlobalMemory() throws Exception {
        RouteConfig defaultRoute = RouteConfig.builder("route-default", "Default Route", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(defaultRoute);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:route-default",
                "Global knowledge base entry about vector database fundamentals.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "global-knowledge",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "route-default"
                ));
        producer.close();

        assertThat(sink.totalProcessed()).isEqualTo(1);

        // The global memory should contain the document
        List<CognitiveResult> results = globalMemory.recall("vector database fundamentals");
        assertThat(results).isNotEmpty();
    }
}
