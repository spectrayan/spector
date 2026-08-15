/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.connector;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.core.RouteLifecycleService;
import com.spectrayan.spector.connector.model.ConnectionTestResult;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.RouteStatus;
import com.spectrayan.spector.connector.model.TemplateDescriptor;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConnectorController}.
 */
@ExtendWith(MockitoExtension.class)
class ConnectorControllerTest {

    @Mock private IngestionTarget target;
    @Mock private EmbeddingProvider embeddingProvider;

    private TemplateRegistry templateRegistry;
    private InMemoryRouteConfigProvider configProvider;
    private InMemoryExecutionLogger executionLogger;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;
    private RouteLifecycleService lifecycleService;
    private ConnectorController controller;

    @BeforeEach
    void setUp() throws Exception {
        templateRegistry = new TemplateRegistry(null);
        configProvider = new InMemoryRouteConfigProvider();
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);
        engine = new CamelConnectorEngine(sink, configProvider, templateRegistry);
        engine.start();
        lifecycleService = new RouteLifecycleService(engine, templateRegistry, Optional::ofNullable);
        controller = new ConnectorController(templateRegistry, engine, lifecycleService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isStarted()) {
            engine.close();
        }
    }

    @Test
    @DisplayName("List templates returns all built-in templates")
    void listTemplates() {
        ResponseEntity<List<TemplateDescriptor>> response = controller.listTemplates();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody().stream().map(TemplateDescriptor::templateId))
                .contains("file-watch", "direct", "rss", "web-scraper");
    }

    @Test
    @DisplayName("Get existing template returns 200 OK")
    void getTemplateFound() {
        ResponseEntity<TemplateDescriptor> response = controller.getTemplate("file-watch");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().templateId()).isEqualTo("file-watch");
    }

    @Test
    @DisplayName("Get nonexistent template returns 404 NOT_FOUND")
    void getTemplateNotFound() {
        ResponseEntity<TemplateDescriptor> response = controller.getTemplate("nonexistent");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Create and activate valid direct route")
    void createRouteSuccess() {
        RouteConfig config = RouteConfig.builder("test-direct", "Direct Test Route", "direct")
                .tenantId("tenant-1")
                .properties(Map.of("collection", "default"))
                .build();

        ResponseEntity<?> response = controller.createRoute(config);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(RouteConfig.class);

        RouteConfig created = (RouteConfig) response.getBody();
        assertThat(created.id()).isEqualTo("test-direct");
        assertThat(created.status()).isEqualTo(RouteStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create route with invalid template returns 400 BAD_REQUEST")
    void createRouteInvalidTemplate() {
        RouteConfig config = RouteConfig.builder("invalid-route", "Invalid Route", "nonexistent-template")
                .build();

        ResponseEntity<?> response = controller.createRoute(config);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Start, stop, and delete route lifecycle")
    void routeLifecycle() {
        RouteConfig config = RouteConfig.builder("lifecycle-route", "Lifecycle Route", "direct")
                .build();

        controller.createRoute(config);

        ResponseEntity<?> stopResponse = controller.stopRoute("lifecycle-route");
        assertThat(stopResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<?> startResponse = controller.startRoute("lifecycle-route");
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> deleteResponse = controller.deleteRoute("lifecycle-route");
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<RouteConfig> getResponse = controller.getRoute("lifecycle-route");
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Test connection returns success result")
    void testConnection() {
        RouteConfig config = RouteConfig.builder("test-route", "Test Route", "direct")
                .properties(Map.of())
                .build();

        ResponseEntity<ConnectionTestResult> response = controller.testConnectionConfig(config);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
    }
}
