/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.memory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * Iterates all cached namespace entries from {@link MemoryRegistry} (via {@link NamespaceResolver})
 * and writes one snapshot row per namespace per interval. Activity deltas are derived from the
 * {@link MeterRegistry} (via {@code ObservedSpectorMemory} Observations), not from
 * hand-rolled AtomicLong counters.</p>
 */
@Service
@ConditionalOnProperty(name = "spector.memory.analytics.history.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryAnalyticsScheduler.class);

    private final MemoryAccessObject mao;
    private final JdbcClient jdbc;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;
    private final ObjectProvider<SpectorMemory> fallbackMemoryProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Value("${spector.memory.analytics.instance-id:local}")
    private String instanceId;

    // Last-seen timer counts and total durations per namespace for interval delta calculation.
    // [0] = recalls, [1] = remembers, [2] = consolidations, [3] = recallTotalDurationMs (as double bits)
    private final ConcurrentHashMap<String, long[]> lastSnapshots = new ConcurrentHashMap<>();

    public MemoryAnalyticsScheduler(MemoryAccessObject mao, JdbcClient jdbc,
                                    ObjectProvider<MemoryRegistry> memoryRegistryProvider,
                                    ObjectProvider<SpectorMemory> fallbackMemoryProvider,
                                    ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.mao = mao;
        this.jdbc = jdbc;
        this.memoryRegistryProvider = memoryRegistryProvider;
        this.fallbackMemoryProvider = fallbackMemoryProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Scheduled(fixedDelayString = "${spector.memory.analytics.history.interval:10000}", initialDelay = 5000)
    public void captureSnapshot() {
        MemoryRegistry memoryRegistry = memoryRegistryProvider != null ? memoryRegistryProvider.getIfAvailable() : null;
        MeterRegistry meterRegistry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;

        Map<String, SpectorMemory> entries;
        if (memoryRegistry != null && memoryRegistry.namespaceResolver() != null) {
            entries = memoryRegistry.namespaceResolver().cachedEntries();
        } else {
            SpectorMemory fallback = fallbackMemoryProvider != null ? fallbackMemoryProvider.getIfAvailable() : null;
            if (fallback != null) {
                entries = Map.of("default", fallback);
            } else {
                entries = Map.of();
            }
        }

        if (entries.isEmpty()) {
            return;
        }

        // Prune state for any evicted namespaces to avoid memory leak
        lastSnapshots.keySet().removeIf(ns -> !entries.containsKey(ns));

        Instant now = Instant.now();

        for (var entry : entries.entrySet()) {
            String namespaceId = entry.getKey();
            SpectorMemory memory = entry.getValue();

            if (!mao.isAvailable(memory) || memory.admin() == null
                    || memory.admin().index() == null || memory.admin().graph() == null) {
                continue;
            }

            try {
                captureNamespaceSnapshot(namespaceId, memory, now, meterRegistry);
            } catch (Exception e) {
                log.error("[MemoryAnalytics] Failed to persist snapshot for namespace '{}': {}",
                        namespaceId, e.getMessage(), e);
            }
        }
    }

    private void captureNamespaceSnapshot(String namespaceId, SpectorMemory memory, Instant now, MeterRegistry meterRegistry) {
        long totalCount = memory.admin().index().size();
        int workingCount = memory.memoryCount(MemoryType.WORKING);
        int episodicCount = memory.memoryCount(MemoryType.EPISODIC);
        int semanticCount = memory.memoryCount(MemoryType.SEMANTIC);
        int proceduralCount = memory.memoryCount(MemoryType.PROCEDURAL);

        GraphStats graphStats = memory.admin().graph().graphStats();

        // Read cumulative timer counts and total times from MeterRegistry (ADR-0083)
        long curRecalls = timerCount(meterRegistry, "spector.memory.recall", namespaceId);
        double curRecallTotalMs = timerTotalTimeMs(meterRegistry, "spector.memory.recall", namespaceId);
        long curRemembers = timerCount(meterRegistry, "spector.memory.remember", namespaceId);
        long curConsolidations = timerCount(meterRegistry, "spector.memory.consolidate", namespaceId);

        // Compute interval deltas from last snapshot
        long[] prev = lastSnapshots.getOrDefault(namespaceId, new long[]{0, 0, 0, 0});
        long deltaRecalls = Math.max(0, curRecalls - prev[0]);
        long deltaRemembers = Math.max(0, curRemembers - prev[1]);
        long deltaConsolidations = Math.max(0, curConsolidations - prev[2]);
        double prevRecallTotalMs = Double.longBitsToDouble(prev[3]);
        double deltaRecallTotalMs = Math.max(0.0, curRecallTotalMs - prevRecallTotalMs);

        // True interval average latency: delta total duration / delta count
        double avgLatencyMs = deltaRecalls > 0 ? (deltaRecallTotalMs / deltaRecalls) : 0.0;

        lastSnapshots.put(namespaceId, new long[]{curRecalls, curRemembers, curConsolidations, Double.doubleToLongBits(curRecallTotalMs)});

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
    private long timerCount(MeterRegistry registry, String metricName, String namespaceId) {
        if (registry == null) return 0;
        Timer timer = registry.find(metricName)
                .tag("spector.namespace", namespaceId)
                .timer();
        return timer != null ? timer.count() : 0;
    }

    /**
     * Reads cumulative total duration in milliseconds from MeterRegistry, filtered by namespace tag.
     * Returns 0.0 on cold start.
     */
    private double timerTotalTimeMs(MeterRegistry registry, String metricName, String namespaceId) {
        if (registry == null) return 0.0;
        Timer timer = registry.find(metricName)
                .tag("spector.namespace", namespaceId)
                .timer();
        return timer != null ? timer.totalTime(TimeUnit.MILLISECONDS) : 0.0;
    }
}
