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
package com.spectrayan.spector.synapse.config.cache;

import java.time.Duration;

/**
 * Central registry of all Spring Cache names, key patterns, and eviction policies across Spector Synapse.
 */
public final class SynapseCacheConstants {

    private SynapseCacheConstants() {}

    /**
     * Cache for revoked JWT identifiers (JTI).
     * High-throughput auth verification on every API / WebSocket request.
     */
    public static final String CACHE_JTI_BLOCKLIST = "jti-blocklist";
    public static final Duration TTL_JTI_BLOCKLIST = Duration.ofMinutes(15);
    public static final long MAX_SIZE_JTI_BLOCKLIST = 50_000;

    /**
     * Cache for resolved user accounts and credentials by username or user ID.
     */
    public static final String CACHE_USER_ACCOUNTS = "user-accounts";
    public static final Duration TTL_USER_ACCOUNTS = Duration.ofMinutes(10);
    public static final long MAX_SIZE_USER_ACCOUNTS = 5_000;

    /**
     * Short-lived cache for in-flight decrypted credential secrets in memory.
     */
    public static final String CACHE_DECRYPTED_SECRETS = "decrypted-secrets";
    public static final Duration TTL_DECRYPTED_SECRETS = Duration.ofMinutes(1);
    public static final long MAX_SIZE_DECRYPTED_SECRETS = 1_000;

    /**
     * Cache for encrypted credential records by tenant and name.
     */
    public static final String CACHE_CREDENTIAL_RECORDS = "credential-records";
    public static final Duration TTL_CREDENTIAL_RECORDS = Duration.ofMinutes(5);
    public static final long MAX_SIZE_CREDENTIAL_RECORDS = 2_000;

    /**
     * Cache for hierarchical scoped configuration overrides.
     */
    public static final String CACHE_SCOPED_CONFIGS = "scoped-configs";
    public static final Duration TTL_SCOPED_CONFIGS = Duration.ofMinutes(15);
    public static final long MAX_SIZE_SCOPED_CONFIGS = 1_000;

    /**
     * Cache for active connector route definitions.
     */
    public static final String CACHE_CONNECTOR_ROUTES = "connector-routes";
    public static final Duration TTL_CONNECTOR_ROUTES = Duration.ofMinutes(15);
    public static final long MAX_SIZE_CONNECTOR_ROUTES = 1_000;

    /**
     * Cache for compiled LangGraph4j subgraphs.
     */
    public static final String CACHE_COMPILED_SUBGRAPHS = "compiled-agent-graphs";
    public static final Duration TTL_COMPILED_SUBGRAPHS = Duration.ofHours(1);
    public static final long MAX_SIZE_COMPILED_SUBGRAPHS = 500;

    /**
     * Cache for externalized SQL query files loaded from classpath resources.
     */
    public static final String CACHE_SQL_QUERIES = "sql-queries";
    public static final Duration TTL_SQL_QUERIES = Duration.ofHours(24);
    public static final long MAX_SIZE_SQL_QUERIES = 500;

    /**
     * Cache for token usage aggregations partitioned by user, model, session, and global stats.
     */
    public static final String CACHE_TOKEN_USAGE = "token-usage";
    public static final Duration TTL_TOKEN_USAGE = Duration.ofDays(7);
    public static final long MAX_SIZE_TOKEN_USAGE = 50_000;

    /**
     * Cache for sampled graph neighborhood overviews for Graph Explorer.
     */
    public static final String CACHE_MEMORY_GRAPH_OVERVIEW = com.spectrayan.spector.memory.cortex.cache.MemoryCacheNames.GRAPH_OVERVIEW;
    public static final Duration TTL_MEMORY_GRAPH_OVERVIEW = Duration.ofSeconds(5);
    public static final long MAX_SIZE_MEMORY_GRAPH_OVERVIEW = 100;

    /**
     * Cache for entity and relationship topology statistics.
     */
    public static final String CACHE_MEMORY_TOPOLOGY_STATS = com.spectrayan.spector.memory.cortex.cache.MemoryCacheNames.TOPOLOGY_STATS;
    public static final Duration TTL_MEMORY_TOPOLOGY_STATS = Duration.ofSeconds(5);
    public static final long MAX_SIZE_MEMORY_TOPOLOGY_STATS = 10;

    /**
     * Cache for memory subsystem tier statistics.
     */
    public static final String CACHE_MEMORY_STATS = com.spectrayan.spector.memory.cortex.cache.MemoryCacheNames.MEMORY_STATS;
    public static final Duration TTL_MEMORY_STATS = Duration.ofSeconds(5);
    public static final long MAX_SIZE_MEMORY_STATS = 100;

    /**
     * Cache for cognitive scoring and salience profile calibration stats.
     */
    public static final String CACHE_MEMORY_SCORING_STATS = com.spectrayan.spector.memory.cortex.cache.MemoryCacheNames.SCORING_STATS;
    public static final Duration TTL_MEMORY_SCORING_STATS = Duration.ofSeconds(5);
    public static final long MAX_SIZE_MEMORY_SCORING_STATS = 100;

    /**
     * Cache for catalog accounts (profiles, quotas, default namespace).
     */
    public static final String CACHE_CATALOG_ACCOUNTS = "catalog.accounts";
    public static final Duration TTL_CATALOG_ACCOUNTS = Duration.ofMinutes(10);
    public static final long MAX_SIZE_CATALOG_ACCOUNTS = 5_000;

    /**
     * Cache for catalog namespace records by (accountId, slugOrId).
     */
    public static final String CACHE_CATALOG_NAMESPACES = "catalog.namespaces";
    public static final Duration TTL_CATALOG_NAMESPACES = Duration.ofMinutes(10);
    public static final long MAX_SIZE_CATALOG_NAMESPACES = 10_000;

    /**
     * Cache for catalog active grants.
     */
    public static final String CACHE_CATALOG_GRANTS = "catalog.grants";
    public static final Duration TTL_CATALOG_GRANTS = Duration.ofSeconds(30);
    public static final long MAX_SIZE_CATALOG_GRANTS = 10_000;

    /**
     * Cache for org unit memberships by account ID.
     */
    public static final String CACHE_CATALOG_ORG_MEMBERSHIP = "catalog.org-membership";
    public static final Duration TTL_CATALOG_ORG_MEMBERSHIP = Duration.ofMinutes(5);
    public static final long MAX_SIZE_CATALOG_ORG_MEMBERSHIP = 5_000;

    /**
     * Cache for PEP namespace authorization decisions.
     */
    public static final String CACHE_PEP_NAMESPACE = "pep.namespace";
    public static final Duration TTL_PEP_NAMESPACE = Duration.ofSeconds(15);
    public static final long MAX_SIZE_PEP_NAMESPACE = 10_000;

    /**
     * Cache for PEP identity region authorization decisions.
     */
    public static final String CACHE_PEP_IDENTITY = "pep.identity";
    public static final Duration TTL_PEP_IDENTITY = Duration.ofSeconds(15);
    public static final long MAX_SIZE_PEP_IDENTITY = 10_000;

    /**
     * All managed cache names in Synapse.
     */
    public static final String[] ALL_CACHES = {
            CACHE_JTI_BLOCKLIST,
            CACHE_USER_ACCOUNTS,
            CACHE_DECRYPTED_SECRETS,
            CACHE_CREDENTIAL_RECORDS,
            CACHE_SCOPED_CONFIGS,
            CACHE_CONNECTOR_ROUTES,
            CACHE_COMPILED_SUBGRAPHS,
            CACHE_SQL_QUERIES,
            CACHE_TOKEN_USAGE,
            CACHE_MEMORY_GRAPH_OVERVIEW,
            CACHE_MEMORY_TOPOLOGY_STATS,
            CACHE_MEMORY_STATS,
            CACHE_MEMORY_SCORING_STATS,
            CACHE_CATALOG_ACCOUNTS,
            CACHE_CATALOG_NAMESPACES,
            CACHE_CATALOG_GRANTS,
            CACHE_CATALOG_ORG_MEMBERSHIP,
            CACHE_PEP_NAMESPACE,
            CACHE_PEP_IDENTITY
    };
}
