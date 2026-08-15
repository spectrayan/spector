/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.tools;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CamelRouteInvokerTool}.
 */
@ExtendWith(MockitoExtension.class)
class CamelRouteInvokerToolTest {

    @Mock private IngestionTarget target;
    @Mock private EmbeddingProvider embeddingProvider;

    private TemplateRegistry templateRegistry;
    private InMemoryRouteConfigProvider configProvider;
    private InMemoryExecutionLogger executionLogger;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;
    private CamelRouteInvokerTool tool;

    @BeforeEach
    void setUp() throws Exception {
        templateRegistry = new TemplateRegistry(null);
        configProvider = new InMemoryRouteConfigProvider();
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);
        engine = new CamelConnectorEngine(sink, configProvider, templateRegistry);
        tool = new CamelRouteInvokerTool(engine);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isStarted()) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Tool metadata matches MCP specifications")
    void toolMetadata() {
        assertThat(tool.name()).isEqualTo("invoke_connector_route");
        assertThat(tool.isWriteTool()).isTrue();
        assertThat(tool.inputSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Invoke custom Camel route returns body")
    void invokeCustomRoute() throws Exception {
        engine.start();
        engine.camelContext().addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:echo-service")
                        .setBody(simple("Echo: ${body}"));
            }
        });

        McpSchema.CallToolResult result = tool.execute(null, Map.of(
                "routeId", "echo-service",
                "payload", "Hello Camel!"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).isEqualTo("Echo: Hello Camel!");
    }

    @Test
    @DisplayName("Missing routeId returns error result")
    void missingRouteIdReturnsError() throws Exception {
        McpSchema.CallToolResult result = tool.execute(null, Map.of());
        assertThat(result.isError()).isTrue();
    }
}
