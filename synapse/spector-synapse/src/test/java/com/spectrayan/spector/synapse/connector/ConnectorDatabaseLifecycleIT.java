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
package com.spectrayan.spector.synapse.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.core.RouteLifecycleService;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.model.RouteStatus;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.CredentialProvider;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.synapse.connector.repository.JdbcRouteConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test verifying the database-backed connector lifecycle.
 *
 * <p>Validates that route definitions persisted via {@link JdbcRouteConfigProvider} in H2
 * are dynamically started, hot-reloaded, paused, and purged in {@link CamelConnectorEngine}
 * via {@link RouteLifecycleService}.</p>
 */
class ConnectorDatabaseLifecycleIT {

    private DataSource dataSource;
    private JdbcRouteConfigProvider routeConfigProvider;
    private TemplateRegistry templateRegistry;
    private CamelConnectorEngine engine;
    private RouteLifecycleService lifecycleService;
    private SpectorIngestionSink sink;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V4__connector_routes.sql")
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        routeConfigProvider = new JdbcRouteConfigProvider(jdbc, mapper);

        templateRegistry = new TemplateRegistry(null);

        IngestionTarget target = mock(IngestionTarget.class);
        EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[384], 384, "test-model"));

        sink = new SpectorIngestionSink(target, embeddingProvider, new InMemoryExecutionLogger());
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();

        CredentialProvider credentialProvider = Optional::ofNullable;
        lifecycleService = new RouteLifecycleService(engine, templateRegistry, credentialProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Complete Lifecycle: DB save -> Camel activate -> DB query -> hot reload -> deactivate -> delete")
    void fullConnectorDatabaseLifecycle() throws Exception {
        // 1. Create and save RouteConfig to DB
        RouteConfig config = RouteConfig.builder("direct-audit-route", "Direct Audit Route", "direct")
                .tenantId("tenant-finance")
                .connectorType("INBOUND_EVENT")
                .properties(Map.of("collection", "audit-logs"))
                .enabled(true)
                .build();

        routeConfigProvider.save(config);

        // Verify persisted in DB
        Optional<RouteConfig> loaded = routeConfigProvider.findById("direct-audit-route");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().tenantId()).isEqualTo("tenant-finance");

        // 2. Activate route in Camel via RouteLifecycleService
        RouteConfig active = lifecycleService.activateRoute(config);
        assertThat(active.status()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(engine.activeRouteIds()).contains("direct-audit-route");

        // 3. Hot-reload route with updated properties
        RouteConfig updatedConfig = RouteConfig.builder("direct-audit-route", "Direct Audit Route Updated", "direct")
                .tenantId("tenant-finance")
                .connectorType("INBOUND_EVENT")
                .properties(Map.of("collection", "audit-logs-v2"))
                .enabled(true)
                .build();
        routeConfigProvider.save(updatedConfig);

        RouteConfig reloaded = lifecycleService.activateRoute(updatedConfig);
        assertThat(reloaded.status()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(engine.activeRouteIds()).contains("direct-audit-route");

        // 4. Deactivate route
        lifecycleService.deactivateRoute("direct-audit-route");
        assertThat(engine.activeRouteIds()).doesNotContain("direct-audit-route");

        // 5. Delete route: verify purged from DB
        routeConfigProvider.delete("direct-audit-route");
        assertThat(routeConfigProvider.findById("direct-audit-route")).isEmpty();
    }

    @Test
    @DisplayName("Multi-tenant isolation: routes in DB are filtered and started per tenant")
    void multiTenantRouteIsolation() throws Exception {
        RouteConfig t1 = RouteConfig.builder("t1-route", "T1 Direct", "direct")
                .tenantId("tenant-alpha")
                .enabled(true)
                .build();
        RouteConfig t2 = RouteConfig.builder("t2-route", "T2 Direct", "direct")
                .tenantId("tenant-beta")
                .enabled(true)
                .build();

        routeConfigProvider.save(t1);
        routeConfigProvider.save(t2);

        lifecycleService.activateRoute(t1);
        lifecycleService.activateRoute(t2);

        List<RouteConfig> alphaRoutes = routeConfigProvider.findByTenantId("tenant-alpha");
        List<RouteConfig> betaRoutes = routeConfigProvider.findByTenantId("tenant-beta");

        assertThat(alphaRoutes).hasSize(1).extracting(RouteConfig::id).containsExactly("t1-route");
        assertThat(betaRoutes).hasSize(1).extracting(RouteConfig::id).containsExactly("t2-route");
        assertThat(engine.activeRouteIds()).contains("t1-route", "t2-route");
    }

    @Test
    @DisplayName("Messaging Channel Routes: deploy channel route template from DB")
    void deployMessagingChannelRoute() throws Exception {
        RouteConfig slackRoute = RouteConfig.builder("prod-slack-notify", "Slack Alerts", "slack-notify")
                .tenantId("default")
                .connectorType("OUTBOUND_ACTION")
                .properties(Map.of(
                        "channel", "general-alerts",
                        "webhookUrl", "https://hooks.slack.com/services/T00/B00/X00"
                ))
                .enabled(true)
                .build();

        routeConfigProvider.save(slackRoute);
        RouteConfig deployed = lifecycleService.activateRoute(slackRoute);

        assertThat(deployed.status()).isEqualTo(RouteStatus.ACTIVE);
        assertThat(engine.activeRouteIds()).contains("prod-slack-notify");
    }
}
