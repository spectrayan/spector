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
package com.spectrayan.spector.synapse.config;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;

/**
 * Auto-configuration for the namespace catalog plane (ADR-0029 §6).
 *
 * <p>When {@code spector.auth.enabled=true} (the default for multi-user deployments),
 * a {@link com.spectrayan.spector.synapse.catalog.jdbc.JdbcAccountCatalog} bean is created
 * as the production default. A {@link FileAccountCatalog} is available for legacy/test
 * environments only via {@code spector.catalog.type=file}. When auth is disabled, a no-op catalog
 * is provided to satisfy dependency injection.</p>
 */
@Configuration
public class CatalogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CatalogAutoConfiguration.class);

    /**
     * JDBC-backed catalog for production and enterprise deployments (default).
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "true", matchIfMissing = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "spector.catalog.type", havingValue = "jdbc", matchIfMissing = true)
    public AccountCatalog jdbcAccountCatalog(
            org.springframework.jdbc.core.simple.JdbcClient jdbc,
            com.spectrayan.spector.synapse.config.sql.SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper) {
        log.info("[CatalogAutoConfiguration] creating JdbcAccountCatalog");
        return new com.spectrayan.spector.synapse.catalog.jdbc.JdbcAccountCatalog(jdbc, sqlLoader, objectMapper);
    }

    /**
     * File-backed catalog for standalone or file-based deployments.
     * Rooted at the same persistence path as the memory data plane.
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "true", matchIfMissing = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "spector.catalog.type", havingValue = "file", matchIfMissing = false)
    public AccountCatalog fileAccountCatalog(SynapseProperties synapseProps, ObjectMapper objectMapper) {
        String path = synapseProps.getMemory().getPersistencePath();
        if (path == null || path.isBlank()) {
            path = synapseProps.dataDir();
        }
        Path basePath = Path.of(path);
        log.info("[CatalogAutoConfiguration] creating FileAccountCatalog at basePath={}", basePath);
        return new FileAccountCatalog(basePath, objectMapper);
    }

    /**
     * No-op catalog for auth-disabled deployments (single shared bean mode).
     * All methods throw {@link UnsupportedOperationException} — they should never be
     * called because {@link com.spectrayan.spector.synapse.memory.MemoryRegistry}
     * short-circuits to the shared bean when auth is disabled.
     */
    @Bean
    @ConditionalOnProperty(name = "spector.auth.enabled", havingValue = "false", matchIfMissing = true)
    public AccountCatalog noopAccountCatalog() {
        log.info("[CatalogAutoConfiguration] auth disabled — using no-op AccountCatalog");
        return new NoopAccountCatalog();
    }

    /**
     * No-op catalog implementation for auth-disabled deployments.
     * All methods throw — the resolution chain never reaches the catalog when auth is off.
     */
    private static final class NoopAccountCatalog implements AccountCatalog {

        private static final String MSG = "AccountCatalog is not available when auth is disabled";

        @Override
        public com.spectrayan.spector.synapse.catalog.Account getOrCreateAccount(String accountId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public com.spectrayan.spector.synapse.catalog.Account getAccount(String accountId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public com.spectrayan.spector.synapse.catalog.NamespaceRecord createNamespace(
                String accountId, String slug,
                com.spectrayan.spector.synapse.catalog.NamespaceType type,
                String displayName, String description,
                com.spectrayan.spector.synapse.catalog.NamespaceBias bias) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public com.spectrayan.spector.synapse.catalog.NamespaceRecord updateNamespace(
                String accountId, String slugOrId,
                String displayName, String description,
                com.spectrayan.spector.synapse.catalog.NamespaceType type,
                com.spectrayan.spector.synapse.catalog.NamespaceBias bias) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void resetNamespace(String accountId, String slugOrId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public java.util.Optional<com.spectrayan.spector.synapse.catalog.NamespaceRecord> resolve(
                String accountId, String slugOrId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<com.spectrayan.spector.synapse.catalog.NamespaceRecord> listAccessible(
                String accountId) {
            return java.util.List.of();
        }

        @Override
        public void setDefaultNamespace(String accountId, String namespaceId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void addGrant(com.spectrayan.spector.synapse.catalog.Grant grant) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void revokeGrant(String grantId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public java.util.List<com.spectrayan.spector.synapse.catalog.Grant> listGrants(
                String accountId, String slugOrId) {
            return java.util.List.of();
        }

        @Override
        public com.spectrayan.spector.synapse.catalog.Grant grantNamespace(
                String callerAccountId, String slugOrId, String granteeAccountId,
                com.spectrayan.spector.synapse.catalog.GrantRole role,
                java.time.Instant expiresAt,
                com.spectrayan.spector.synapse.catalog.GrantConstraints constraints) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void revokeNamespaceGrant(String callerAccountId, String slugOrId, String grantId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public com.spectrayan.spector.synapse.catalog.NamespaceRecord setLegalHold(
                String accountId, String slugOrId, boolean legalHold) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public java.util.Optional<com.spectrayan.spector.synapse.catalog.Grant> authorize(
                String accountId, String namespaceId,
                com.spectrayan.spector.synapse.catalog.GrantRole minimumRole) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean authorizeIdentity(String accountId, String bundleId,
                String regionId,
                com.spectrayan.spector.synapse.catalog.GrantAction action) {
            // Auth disabled → permit everything
            return true;
        }

        @Override
        public void tombstone(String accountId, String namespaceId) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void recordAccess(String namespaceId) {
            // no-op
        }
    }
}
