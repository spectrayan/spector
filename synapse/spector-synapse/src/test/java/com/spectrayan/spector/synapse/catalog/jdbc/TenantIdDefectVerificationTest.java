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
package com.spectrayan.spector.synapse.catalog.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.spectrayan.spector.synapse.catalog.exception.TenantReassignmentException;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link Account#tenantId()} and {@link Account#legalHold()} round-trip correctly
 * through {@link JdbcAccountCatalog} from the database, missing schema columns fail loudly,
 * and tenant reassignment is refused when namespaces are owned (Task 1.8, Spec §1.2, Req R2).
 */
@DisplayName("Task 1.8: Account tenantId and legalHold persistence and enforcement")
class TenantIdDefectVerificationTest {

    private JdbcClient jdbc;
    private JdbcAccountCatalog catalog;

    private static final String ACCOUNT_ID = "0195500000001";

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:tenantdefect-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SqlQueryLoader sqlLoader = new SqlQueryLoader();
        sqlLoader.prewarm();

        catalog = new JdbcAccountCatalog(jdbc, sqlLoader, objectMapper);
    }

    @Test
    @DisplayName("Account.tenantId and legalHold round-trip correctly from DB")
    void testTenantIdAndLegalHoldRoundTripFromDb() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        // Update database directly with tenant_id and legal_hold
        int rows = jdbc.sql("UPDATE users SET tenant_id = 'acme', legal_hold = TRUE WHERE user_id = :userId")
                .param("userId", ACCOUNT_ID)
                .update();
        assertThat(rows).isEqualTo(1);

        // Verify that raw DB row actually contains the updated values
        String dbTenant = jdbc.sql("SELECT tenant_id FROM users WHERE user_id = :userId")
                .param("userId", ACCOUNT_ID)
                .query(String.class)
                .single();
        assertThat(dbTenant).isEqualTo("acme");

        Boolean dbHold = jdbc.sql("SELECT legal_hold FROM users WHERE user_id = :userId")
                .param("userId", ACCOUNT_ID)
                .query(Boolean.class)
                .single();
        assertThat(dbHold).isTrue();

        // Reload account through catalog
        Account account = catalog.getAccount(ACCOUNT_ID);
        assertThat(account).isNotNull();

        // find-by-id.sql selects tenant_id and legal_hold, and mapAccountRow maps them into Account
        assertThat(account.tenantId())
                .as("Account.tenantId() equals 'acme' after DB update")
                .isEqualTo("acme");
        assertThat(account.legalHold())
                .as("Account.legalHold() is true after DB update")
                .isTrue();
    }

    @Test
    @DisplayName("Missing tenant_id column in database schema fails loudly")
    void testMissingTenantIdColumnFailsLoudly() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        // Drop tenant_id column from users table
        jdbc.sql("ALTER TABLE users DROP COLUMN tenant_id").update();

        // Attempting to reload account must fail loudly instead of returning null
        assertThatThrownBy(() -> catalog.getAccount(ACCOUNT_ID))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("assignTenant persists tenant and refuses reassignment when account owns namespaces")
    void testAssignTenantAndReassignmentGuard() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        assertThat(catalog.getAccount(ACCOUNT_ID).tenantId()).isNull();

        // Initial assignment succeeds
        catalog.assignTenant(ACCOUNT_ID, "acme");
        assertThat(catalog.getAccount(ACCOUNT_ID).tenantId()).isEqualTo("acme");

        // Attempting to reassign tenant when account owns namespaces throws TenantReassignmentException
        assertThatThrownBy(() -> catalog.assignTenant(ACCOUNT_ID, "globex"))
                .isInstanceOf(TenantReassignmentException.class)
                .hasMessageContaining("Cannot reassign tenant");

        // Idempotent assignment to same tenant succeeds
        catalog.assignTenant(ACCOUNT_ID, "acme");
        assertThat(catalog.getAccount(ACCOUNT_ID).tenantId()).isEqualTo("acme");
    }
}
