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
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.GraphStats;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Background scheduler that periodically captures per-namespace memory diagnostic telemetry
 * and persists it to the database for historical analytics (ADR-0083).
 *
 * <p>Gated by {@code spector.memory.analytics.history.enabled} (default {@code true}).
 * Iterates all cached namespace entries from {@link NamespaceResolver} and writes one
 * snapshot row per namespace per interval. Activity deltas are derived from the
 * {@link MeterRegistry} (via {@code ObservedSpectorMemory} Observations), not from
 * hand-rolled AtomicLong counters.</p>
 */
@Service
@ConditionalOnProperty(name = "spector.memory.analytics.history.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryAnalyticsScheduler.class);

    private final MemoryAccessObject mao;
    private final JdbcClient jdbc;
    private final NamespaceResolver namespaceResolver;
    private final MeterRegistry meterRegistry;

    @Value("${spector.memory.analytics.instance-id:local}")
    private String instanceId;

    // Last-seen timer counts per namespace for interval delta calculation
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> lastSnapshots = new java.util.concurrent.ConcurrentHashMap<>();

    public MemoryAnalyticsScheduler(MemoryAccessObject mao, JdbcClient jdbc,
                                    NamespaceResolver namespaceResolver,
                                    MeterRegistry meterRegistry) {
        this.mao = mao;
        this.jdbc = jdbc;
        this.namespaceResolver = namespaceResolver;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${spector.memory.analytics.history.interval:10000}", initialDelay = 5000)
    public void captureSnapshot() {
        Map<String, SpectorMemory> entries = namespaceResolver.cachedEntries();
        if (entries.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        for (var entry : entries.entrySet()) {
            String namespaceId = entry.getKey();
            SpectorMemory memory = entry.getValue();

            if (!mao.isAvailable(memory) || memory.admin() == null
                    || memory.admin().index() == null || memory.admin().graph() == null) {
                continue;
            }

            try {
                captureNamespaceSnapshot(namespaceId, memory, now);
            } catch (Exception e) {
                log.error("[MemoryAnalytics] Failed to persist snapshot for namespace '{}': {}",
                        namespaceId, e.getMessage(), e);
            }
        }
    }

    private void captureNamespaceSnapshot(String namespaceId, SpectorMemory memory, Instant now) {
        long totalCount = memory.admin().index().size();
        int workingCount = memory.memoryCount(MemoryType.WORKING);
        int episodicCount = memory.memoryCount(MemoryType.EPISODIC);
        int semanticCount = memory.memoryCount(MemoryType.SEMANTIC);
        int proceduralCount = memory.memoryCount(MemoryType.PROCEDURAL);

        GraphStats graphStats = memory.admin().graph().graphStats();

        // Read cumulative timer counts from MeterRegistry (ADR-0083)
        long curRecalls = timerCount("spector.memory.recall", namespaceId);
        long curRemembers = timerCount("spector.memory.remember", namespaceId);
        long curConsolidations = timerCount("spector.memory.consolidate", namespaceId);

        // Compute interval deltas from last snapshot
        long[] prev = lastSnapshots.getOrDefault(namespaceId, new long[]{0, 0, 0, 0});
        long deltaRecalls = Math.max(0, curRecalls - prev[0]);
        long deltaRemembers = Math.max(0, curRemembers - prev[1]);
        long deltaConsolidations = Math.max(0, curConsolidations - prev[2]);

        // Read average latency from timer mean
        double avgLatencyMs = timerMeanMs("spector.memory.recall", namespaceId);

        lastSnapshots.put(namespaceId, new long[]{curRecalls, curRemembers, curConsolidations, 0});

        jdbc.sql("INSERT INTO memory_analytics_snapshot " +
                        "(snapshot_time, namespace_id, instance_id, total_count, working_count, episodic_count, " +
                        "semantic_count, procedural_count, hebbian_edges, temporal_links, entity_nodes, entity_edges, " +
                        "avg_latency_ms, recall_count, remember_count, consolidations_run) " +
                        "VALUES (:time, :ns, :inst, :total, :working, :episodic, :semantic, :procedural, " +
                        ":hebbian, :temporal, :nodes, :edges, :avgLatency, :recalls, :remembers, :consolidations)")
                .param("time", Timestamp.from(now))
                .param("ns", namespaceId)
                .param("inst", instanceId)
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
                .param("recalls", deltaRecalls)
                .param("remembers", deltaRemembers)
                .param("consolidations", deltaConsolidations)
                .update();

        log.debug("[MemoryAnalytics] Persisted snapshot: ns={}, total={}, avgLatency={}ms",
                namespaceId, totalCount, avgLatencyMs);
    }

    /**
     * Reads cumulative timer count from MeterRegistry, filtered by namespace tag.
     * Returns 0 on cold start (timer not yet created).
     */
    private long timerCount(String metricName, String namespaceId) {
        Timer timer = meterRegistry.find(metricName)
                .tag("spector.namespace", namespaceId)
                .timer();
        return timer != null ? timer.count() : 0;
    }

    /**
     * Reads mean timer duration in milliseconds from MeterRegistry.
     * Returns 0.0 on cold start.
     */
    private double timerMeanMs(String metricName, String namespaceId) {
        Timer timer = meterRegistry.find(metricName)
                .tag("spector.namespace", namespaceId)
                .timer();
        return timer != null ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
    }
}
