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
package com.spectrayan.spector.synapse.config.sql;

import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import com.spectrayan.spector.synapse.error.SynapseDatabaseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * High-performance, Spring Cache-backed loader for externalized SQL queries stored in {@code classpath:sql/}.
 *
 * <p>Pre-warms all SQL resource queries into {@link SynapseCacheConstants#CACHE_SQL_QUERIES} at startup
 * and provides zero-overhead in-memory query retrieval. Cache invalidation via administrative endpoints
 * allows hot-reloading queries on the fly without server restarts.</p>
 */
@Component
public class SqlQueryLoader {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryLoader.class);
    private static final String SQL_BASE_PATH = "classpath:sql/";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * Pre-warms the SQL query cache on application startup.
     */
    @PostConstruct
    public void prewarm() {
        try {
            Resource[] resources = resourceResolver.getResources(SQL_BASE_PATH + "**/*.sql");
            int count = 0;
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    String uri = resource.getURI().toString();
                    String logicalPath = extractLogicalPath(uri);
                    if (logicalPath != null) {
                        load(logicalPath);
                        count++;
                    }
                }
            }
            log.info("[SqlQueryLoader] Pre-warmed {} externalized SQL queries into '{}'", count,
                    SynapseCacheConstants.CACHE_SQL_QUERIES);
        } catch (Exception e) {
            log.warn("[SqlQueryLoader] Failed to pre-warm SQL queries: {}", e.getMessage());
        }
    }

    /**
     * Loads a SQL statement from classpath resources, caching the parsed query string.
     *
     * @param queryPath logical relative path under {@code classpath:sql/} (e.g. "users/find-by-username.sql" or "users/find-by-username")
     * @return trimmed SQL string
     * @throws SynapseDatabaseException if the file is missing or unreadable
     */
    @Cacheable(value = SynapseCacheConstants.CACHE_SQL_QUERIES, key = "#queryPath")
    public String load(String queryPath) {
        Objects.requireNonNull(queryPath, "queryPath must not be null");
        String normalizedPath = queryPath.endsWith(".sql") ? queryPath : queryPath + ".sql";
        String location = SQL_BASE_PATH + normalizedPath;

        try {
            Resource resource = resourceResolver.getResource(location);
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("SQL resource not found or unreadable: " + location);
            }

            try (InputStream is = resource.getInputStream()) {
                String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String cleaned = cleanSql(raw);
                if (cleaned.isBlank()) {
                    throw new IllegalStateException("SQL resource is empty: " + location);
                }
                log.debug("[SqlQueryLoader] Loaded SQL template for '{}'", normalizedPath);
                return cleaned;
            }
        } catch (Exception e) {
            log.error("[SqlQueryLoader] Failed to load SQL query '{}'", queryPath, e);
            throw new SynapseDatabaseException("loadSqlQuery", normalizedPath, e);
        }
    }

    /**
     * Cleans comments and redundant whitespace from a SQL script.
     */
    private static String cleanSql(String sql) {
        if (sql == null) return "";
        // Remove multi-line comments /* ... */ and single-line -- comments
        String withoutMultiLine = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder sb = new StringBuilder();
        for (String line : withoutMultiLine.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String extractLogicalPath(String uri) {
        int sqlIdx = uri.indexOf("/sql/");
        if (sqlIdx != -1) {
            return uri.substring(sqlIdx + 5);
        }
        int jarSqlIdx = uri.indexOf("!/sql/");
        if (jarSqlIdx != -1) {
            return uri.substring(jarSqlIdx + 6);
        }
        return null;
    }
}
