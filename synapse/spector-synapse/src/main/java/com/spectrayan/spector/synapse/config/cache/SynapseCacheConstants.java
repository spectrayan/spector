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
            CACHE_SQL_QUERIES
    };
}
