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
package com.spectrayan.spector.synapse.connector.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.connector.model.RouteConfig;
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

class JdbcRouteConfigProviderTest {

    private JdbcRouteConfigProvider provider;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V4__connector_routes.sql")
                .generateUniqueName(true)
                .build();

        jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        provider = new JdbcRouteConfigProvider(jdbc, mapper);
    }

    @Test
    @DisplayName("save and findById retrieves stored RouteConfig")
    void saveAndFindById() {
        RouteConfig config = RouteConfig.builder("slack-outbound", "Slack Notifications", "slack-notify")
                .tenantId("tenant-1")
                .connectorType("OUTBOUND_ACTION")
                .properties(Map.of("channel", "alerts", "webhookUrl", "https://hooks.slack.com/123"))
                .enabled(true)
                .build();

        provider.save(config);

        Optional<RouteConfig> loaded = provider.findById("slack-outbound");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo("slack-outbound");
        assertThat(loaded.get().name()).isEqualTo("Slack Notifications");
        assertThat(loaded.get().templateId()).isEqualTo("slack-notify");
        assertThat(loaded.get().tenantId()).isEqualTo("tenant-1");
        assertThat(loaded.get().connectorType()).isEqualTo("OUTBOUND_ACTION");
        assertThat(loaded.get().enabled()).isTrue();
        assertThat(loaded.get().properties()).containsEntry("channel", "alerts");
    }

    @Test
    @DisplayName("save upserts existing RouteConfig on duplicate key")
    void saveUpsertsExisting() {
        RouteConfig config1 = RouteConfig.builder("s3-ingest", "S3 Ingest", "s3-poll")
                .tenantId("tenant-1")
                .properties(Map.of("bucketName", "old-bucket"))
                .enabled(true)
                .build();
        provider.save(config1);

        RouteConfig config2 = RouteConfig.builder("s3-ingest", "S3 Ingest Updated", "s3-poll")
                .tenantId("tenant-1")
                .properties(Map.of("bucketName", "new-bucket"))
                .enabled(false)
                .build();
        provider.save(config2);

        Optional<RouteConfig> loaded = provider.findById("s3-ingest");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("S3 Ingest Updated");
        assertThat(loaded.get().enabled()).isFalse();
        assertThat(loaded.get().properties()).containsEntry("bucketName", "new-bucket");
    }

    @Test
    @DisplayName("delete removes route from database")
    void deleteRemovesRoute() {
        RouteConfig config = RouteConfig.builder("temp-route", "Temp", "direct").build();
        provider.save(config);
        assertThat(provider.findById("temp-route")).isPresent();

        provider.delete("temp-route");
        assertThat(provider.findById("temp-route")).isEmpty();
    }

    @Test
    @DisplayName("findByTenantId filters routes by tenant")
    void findByTenantIdFilters() {
        provider.save(RouteConfig.builder("r-t1-a", "T1 A", "direct").tenantId("t1").build());
        provider.save(RouteConfig.builder("r-t1-b", "T1 B", "direct").tenantId("t1").build());
        provider.save(RouteConfig.builder("r-t2-a", "T2 A", "direct").tenantId("t2").build());

        List<RouteConfig> t1Routes = provider.findByTenantId("t1");
        assertThat(t1Routes).hasSize(2)
                .extracting(RouteConfig::id)
                .containsExactlyInAnyOrder("r-t1-a", "r-t1-b");

        List<RouteConfig> t2Routes = provider.findByTenantId("t2");
        assertThat(t2Routes).hasSize(1)
                .extracting(RouteConfig::id)
                .containsExactly("r-t2-a");
    }

    @Test
    @DisplayName("findAllEnabled returns only enabled routes")
    void findAllEnabledFilters() {
        provider.save(RouteConfig.builder("r-active", "Active", "direct").enabled(true).build());
        provider.save(RouteConfig.builder("r-disabled", "Disabled", "direct").enabled(false).build());

        List<RouteConfig> enabled = provider.findAllEnabled();
        assertThat(enabled).hasSize(1)
                .extracting(RouteConfig::id)
                .containsExactly("r-active");

        List<RouteConfig> all = provider.findAll();
        assertThat(all).hasSize(2);
    }
}
