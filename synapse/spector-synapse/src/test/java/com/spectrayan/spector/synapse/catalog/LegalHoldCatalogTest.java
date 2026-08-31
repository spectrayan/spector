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
package com.spectrayan.spector.synapse.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.catalog.jdbc.JdbcAccountCatalog;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

@DisplayName("Catalog Legal Hold Governance Specification (ADR-0029 §19, §23)")
class LegalHoldCatalogTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private FileAccountCatalog fileCatalog;
    private JdbcAccountCatalog jdbcCatalog;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        fileCatalog = new FileAccountCatalog(tempDir, objectMapper);

        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        SqlQueryLoader sqlLoader = new SqlQueryLoader();
        jdbcCatalog = new JdbcAccountCatalog(jdbc, sqlLoader, objectMapper);
    }

    @Test
    @DisplayName("FileCatalog: Placing namespace on legal hold prevents tombstone and reset")
    void fileCatalogLegalHoldEnforcement() {
        String accountId = "01JFILELEG001";
        fileCatalog.getOrCreateAccount(accountId);

        NamespaceRecord ns = fileCatalog.createNamespace(accountId, "audit-data", NamespaceType.PROJECT);
        assertThat(ns.legalHold()).isFalse();

        // Enable legal hold
        NamespaceRecord held = fileCatalog.setLegalHold(accountId, "audit-data", true);
        assertThat(held.legalHold()).isTrue();

        // Verify tombstone is blocked
        assertThatThrownBy(() -> fileCatalog.tombstone(accountId, held.namespaceId()))
                .isInstanceOf(NamespaceLegalHoldException.class);

        // Verify reset is blocked
        assertThatThrownBy(() -> fileCatalog.resetNamespace(accountId, "audit-data"))
                .isInstanceOf(NamespaceLegalHoldException.class);

        // Release legal hold
        NamespaceRecord released = fileCatalog.setLegalHold(accountId, "audit-data", false);
        assertThat(released.legalHold()).isFalse();

        // Verify reset succeeds after release
        fileCatalog.resetNamespace(accountId, "audit-data");
    }

    @Test
    @DisplayName("JdbcCatalog: Placing namespace on legal hold prevents tombstone and reset")
    void jdbcCatalogLegalHoldEnforcement() {
        String accountId = "01JJDBCLEG001";
        jdbcCatalog.getOrCreateAccount(accountId);

        NamespaceRecord ns = jdbcCatalog.createNamespace(accountId, "compliance-vault", NamespaceType.PROJECT);
        assertThat(ns.legalHold()).isFalse();

        // Enable legal hold
        NamespaceRecord held = jdbcCatalog.setLegalHold(accountId, "compliance-vault", true);
        assertThat(held.legalHold()).isTrue();

        // Verify tombstone is blocked
        assertThatThrownBy(() -> jdbcCatalog.tombstone(accountId, held.namespaceId()))
                .isInstanceOf(NamespaceLegalHoldException.class);

        // Verify reset is blocked
        assertThatThrownBy(() -> jdbcCatalog.resetNamespace(accountId, "compliance-vault"))
                .isInstanceOf(NamespaceLegalHoldException.class);

        // Release legal hold
        NamespaceRecord released = jdbcCatalog.setLegalHold(accountId, "compliance-vault", false);
        assertThat(released.legalHold()).isFalse();

        // Verify reset succeeds after release
        jdbcCatalog.resetNamespace(accountId, "compliance-vault");
    }
}
