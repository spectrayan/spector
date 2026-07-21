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
package com.spectrayan.spector.synapse.memory;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.GraphStats;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Background scheduler that periodically captures memory diagnostic telemetry
 * and persists it to the H2 database for historical analytics.
 */
@Service
public class MemoryAnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryAnalyticsScheduler.class);

    private final MemoryAccessObject mao;
    private final MemoryService memoryService;
    private final JdbcClient jdbc;

    /**
     * Temporary bridge for resolving the target {@link SpectorMemory}.
     *
     * <p>TODO(15.2/16.1): this background scheduler has no request-bound security context;
     * once per-user memory routing lands it should iterate the {@code UserMemoryRegistry}.
     * For now it captures analytics for the single shared instance.</p>
     */
    private final ObjectProvider<SpectorMemory> memoryProvider;

    public MemoryAnalyticsScheduler(MemoryAccessObject mao, MemoryService memoryService, JdbcClient jdbc,
                                    ObjectProvider<SpectorMemory> memoryProvider) {
        this.mao = mao;
        this.memoryService = memoryService;
        this.jdbc = jdbc;
        this.memoryProvider = memoryProvider;
    }

    @Scheduled(fixedDelayString = "${spector.memory.analytics.interval:10000}", initialDelay = 5000)
    public void captureSnapshot() {
        var memory = memoryProvider != null ? memoryProvider.getIfAvailable() : null;
        if (!mao.isAvailable(memory)) {
            return;
        }

        try {

            long totalCount = memory.admin().index().size();
            int workingCount = memory.admin().index().locationMap().values().stream()
                    .filter(loc -> loc.type() == MemoryType.WORKING).toList().size();
            int episodicCount = memory.admin().index().locationMap().values().stream()
                    .filter(loc -> loc.type() == MemoryType.EPISODIC).toList().size();
            int semanticCount = memory.admin().index().locationMap().values().stream()
                    .filter(loc -> loc.type() == MemoryType.SEMANTIC).toList().size();
            int proceduralCount = memory.admin().index().locationMap().values().stream()
                    .filter(loc -> loc.type() == MemoryType.PROCEDURAL).toList().size();

            GraphStats graphStats = memory.admin().graph().graphStats();

            // Fetch activity metrics from MemoryService
            long recalls = memoryService.getAndResetRecallCount();
            long remembers = memoryService.getAndResetRememberCount();
            long latencyMs = memoryService.getAndResetTotalLatencyMs();
            long consolidations = memoryService.getConsolidationCount();

            long totalOps = recalls + remembers;
            double avgLatencyMs = totalOps == 0 ? 0.0 : (double) latencyMs / totalOps;

            Instant now = Instant.now();

            jdbc.sql("INSERT INTO memory_analytics_snapshot " +
                            "(snapshot_time, total_count, working_count, episodic_count, semantic_count, procedural_count, " +
                            "hebbian_edges, temporal_links, entity_nodes, entity_edges, avg_latency_ms, recall_count, remember_count, consolidations_run) " +
                            "VALUES (:time, :total, :working, :episodic, :semantic, :procedural, :hebbian, :temporal, :nodes, :edges, :avgLatency, :recalls, :remembers, :consolidations)")
                    .param("time", Timestamp.from(now))
                    .param("total", totalCount)
                    .param("working", workingCount)
                    .param("episodic", episodicCount)
                    .param("semantic", semanticCount)
                    .param("procedural", proceduralCount)
                    .param("hebbian", graphStats.hebbianEdges())
                    .param("temporal", graphStats.temporalLinks())
                    .param("nodes", graphStats.entityNodes())
                    .param("edges", graphStats.entityEdges())
                    .param("avgLatency", avgLatencyMs)
                    .param("recalls", recalls)
                    .param("remembers", remembers)
                    .param("consolidations", consolidations)
                    .update();

            log.debug("[MemoryAnalytics] Persisted telemetry snapshot: totalMemories={}, avgLatency={}ms", totalCount, avgLatencyMs);
        } catch (Exception e) {
            log.error("[MemoryAnalytics] Failed to persist analytics snapshot: {}", e.getMessage(), e);
        }
    }
}
