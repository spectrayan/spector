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

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration tests for Webhook Ingestion (webhook-receiver) and Outbound Alerting (slack-notify)
 * into Spector Cognitive Memory via Apache Camel and SpectorIngestionSink.
 */
@DisplayName("EmailAndWebhookIngestionE2ETest — Webhook & Notification E2E Ingestion")
class EmailAndWebhookIngestionE2ETest {

    private static final int DIMS = 64;

    private HttpServer mockSlackServer;
    private int mockSlackPort;
    private ConcurrentLinkedQueue<String> receivedSlackWebhooks;

    private SpectorMemory memory;
    private StubEmbeddingProvider embeddingProvider;
    private SpectorIngestionSink sink;
    private InMemoryExecutionLogger executionLogger;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setup() throws Exception {
        receivedSlackWebhooks = new ConcurrentLinkedQueue<>();

        // 1. Mock Slack Webhook Server
        mockSlackServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        mockSlackPort = mockSlackServer.getAddress().getPort();
        mockSlackServer.createContext("/services/T00/B00/X00", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedSlackWebhooks.add(new String(body, StandardCharsets.UTF_8));
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        mockSlackServer.start();

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
        if (mockSlackServer != null) mockSlackServer.stop(0);
    }

    @Test
    @DisplayName("E2E: Inbound Netty HTTP webhook ingestion and agent cognitive recall")
    void inboundWebhookIngestionAndRecall() throws Exception {
        int webhookPort = 18976;
        RouteConfig webhookConfig = RouteConfig.builder("e2e-webhook", "Payment Webhook Receiver", "webhook-receiver")
                .tenantId("finance-team")
                .properties(Map.of(
                        "host", "127.0.0.1",
                        "port", String.valueOf(webhookPort),
                        "webhookPath", "/webhooks/stripe-events",
                        "collection", "stripe-webhooks"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(webhookConfig);

        // Send simulated Stripe charge.dispute.created webhook event
        String eventPayload = "{\n" +
                "  \"id\": \"evt_dispute_9941\",\n" +
                "  \"type\": \"charge.dispute.created\",\n" +
                "  \"data\": {\n" +
                "    \"amount\": 45000,\n" +
                "    \"currency\": \"usd\",\n" +
                "    \"reason\": \"fraudulent_transaction_unrecognized_charge\"\n" +
                "  }\n" +
                "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + webhookPort + "/webhooks/stripe-events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(eventPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        // Wait for SpectorIngestionSink to process the webhook event
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        assertThat(sink.totalErrors()).isZero();

        // Verify agent cognitive recall pulls the dispute event
        var recallResults = memory.recall("evt_dispute_9941 charge dispute fraudulent");
        assertThat(recallResults).isNotEmpty();
        assertThat(recallResults)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("evt_dispute_9941") && text.contains("fraudulent_transaction"));
    }

    @Test
    @DisplayName("E2E: Outbound Slack alert dispatch via slack-notify template")
    void outboundSlackNotification() throws Exception {
        String webhookUrl = "http://localhost:" + mockSlackPort + "/services/T00/B00/X00";

        RouteConfig slackConfig = RouteConfig.builder("e2e-slack-notify", "Security Incident Alert", "slack-notify")
                .tenantId("security-ops")
                .connectorType("OUTBOUND_ACTION")
                .credentialRef("env:SLACK_WEBHOOK_URL")
                .properties(Map.of(
                        "channel", "security-incidents",
                        "webhookUrl", webhookUrl
                ))
                .enabled(true)
                .build();
        engine.deployRoute(slackConfig);

        // Send alert through direct route
        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBody("direct:e2e-slack-notify", "CRITICAL: Suspicious privilege escalation detected on node-alpha");

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(receivedSlackWebhooks).isNotEmpty());

        assertThat(receivedSlackWebhooks.poll())
                .contains("CRITICAL: Suspicious privilege escalation detected on node-alpha");
    }
}
