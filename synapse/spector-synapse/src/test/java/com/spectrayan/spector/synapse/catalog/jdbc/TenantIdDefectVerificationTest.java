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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that {@link Account#tenantId()} and {@link Account#legalHold()} are structurally
 * unreachable on main today, even when set directly in the database (Task 0.4, Spec §1.2, Req R2).
 *
 * <p>This test documents the baseline defect prior to Group 1. It will be inverted in Task 1.4.</p>
 */
@DisplayName("Task 0.4: Prove Account.tenantId is unreachable on main today")
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
    @DisplayName("Account.tenantId and legalHold return null/false even when set in DB")
    void testTenantIdAndLegalHoldAreUnreachableOnCurrentMain() {
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

        // PROOF: find-by-id.sql omits tenant_id and legal_hold, and mapAccountRow swallows the SQLException.
        // Therefore, account.tenantId() returns null and account.legalHold() returns false!
        assertThat(account.tenantId())
                .as("Account.tenantId() is null because find-by-id.sql does not SELECT tenant_id")
                .isNull();
        assertThat(account.legalHold())
                .as("Account.legalHold() is false because find-by-id.sql does not SELECT legal_hold")
                .isFalse();
    }
}
