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

import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.spi.InMemoryRouteConfigProvider;
import com.spectrayan.spector.connector.template.TemplateRegistry;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConnectorExecutionAuditNotifier & Generic Engine Tests")
class ConnectorExecutionAuditNotifierTest {

    private InMemoryExecutionLogger executionLogger;
    private InMemoryRouteConfigProvider configProvider;
    private TemplateRegistry templateRegistry;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setUp() {
        executionLogger = new InMemoryExecutionLogger();
        configProvider = new InMemoryRouteConfigProvider();
        templateRegistry = new TemplateRegistry(null);
        // Generic connector engine without ingestion sink
        engine = new CamelConnectorEngine(executionLogger, configProvider, templateRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isStarted()) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Generic engine starts without ingestion sink and exposes executionLogger")
    void genericEngineStartsWithoutSink() throws Exception {
        engine.start();

        assertThat(engine.isStarted()).isTrue();
        assertThat(engine.executionLogger()).isSameAs(executionLogger);
        assertThat(engine.ingestionSink()).isEmpty();
        assertThat(engine.camelContext().getRegistry().lookupByName("spectorExecutionLogger")).isSameAs(executionLogger);
    }

    @Test
    @DisplayName("Audit notifier records successful execution for non-ingestion route")
    void recordsSuccessfulExecutionForGenericRoute() throws Exception {
        engine.start();

        // Add a direct route (e.g. outbound notification or chat message)
        engine.camelContext().addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:notification-out")
                        .routeId("route-notify-001")
                        .setHeader("spector-tenant-id", constant("tenant-abc"))
                        .setHeader("spector-trace-id", constant("trace-999"))
                        .log("Notification sent successfully");
            }
        });

        // Send a message
        engine.camelContext().createProducerTemplate().sendBody("direct:notification-out", "{\"to\":\"user@example.com\",\"body\":\"Hello\"}");

        // Verify execution record
        List<ExecutionRecord> history = engine.getExecutionHistory("route-notify-001", 10);
        assertThat(history).hasSize(1);

        ExecutionRecord record = history.getFirst();
        assertThat(record.routeId()).isEqualTo("route-notify-001");
        assertThat(record.tenantId()).isEqualTo("tenant-abc");
        assertThat(record.traceId()).isEqualTo("trace-999");
        assertThat(record.status()).isEqualTo(ExecutionRecord.ExecutionStatus.COMPLETED);
        assertThat(record.errors()).isZero();
        assertThat(record.errorMessage()).isNull();
    }

    @Test
    @DisplayName("Audit notifier records failed execution with error message")
    void recordsFailedExecutionForGenericRoute() throws Exception {
        engine.start();

        engine.camelContext().addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:failing-channel")
                        .routeId("route-chat-002")
                        .setHeader("spector-tenant-id", constant("tenant-xyz"))
                        .setHeader("spector-trace-id", constant("trace-error-456"))
                        .throwException(new IllegalStateException("Chat service connection refused"));
            }
        });

        try {
            engine.camelContext().createProducerTemplate().sendBody("direct:failing-channel", "Hello");
        } catch (Exception ignored) {
            // Expected
        }

        List<ExecutionRecord> history = engine.getExecutionHistory("route-chat-002", 10);
        assertThat(history).hasSize(1);

        ExecutionRecord record = history.getFirst();
        assertThat(record.routeId()).isEqualTo("route-chat-002");
        assertThat(record.tenantId()).isEqualTo("tenant-xyz");
        assertThat(record.traceId()).isEqualTo("trace-error-456");
        assertThat(record.status()).isEqualTo(ExecutionRecord.ExecutionStatus.FAILED);
        assertThat(record.errors()).isEqualTo(1);
        assertThat(record.errorMessage()).contains("Chat service connection refused");
    }
}
