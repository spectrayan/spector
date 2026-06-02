package com.spectrayan.spector.node.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import com.spectrayan.spector.cluster.ClusterCoordinator;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.config.CortexTelemetryConfig;
import com.spectrayan.spector.node.event.SpectorCortexClusterNodeInfo;
import com.spectrayan.spector.node.event.SpectorCortexClusterTopologyEvent;
import com.spectrayan.spector.node.event.SpectorCortexEmbeddingProjectionEvent;
import com.spectrayan.spector.node.event.SpectorCortexMemoryDiagnosticEvent;
import com.spectrayan.spector.node.event.SpectorEventBus;

/**
 * Periodic publisher that reads from a Micrometer {@link MeterRegistry}
 * and emits cortex dashboard events via {@link SpectorEventBus}.
 *
 * <p>This is the bridge between Micrometer (single source of truth for
 * all metrics) and the Cortex SSE stream. Instead of manually timing
 * operations with {@code System.nanoTime()}, the publisher reads from
 * existing timers, counters, and gauges that are already registered by
 * {@code MeteredSpectorEngine} and {@code MeteredSpectorMemory}.</p>
 *
 * <h3>Emitted Events</h3>
 * <ul>
 *   <li>{@link SpectorCortexMemoryDiagnosticEvent} — every {@code intervalMs} (health snapshot)</li>
 *   <li>{@link SpectorCortexClusterTopologyEvent} — every 5s (cluster state)</li>
 *   <li>{@link SpectorCortexEmbeddingProjectionEvent} — every 10s (vector space 3D)</li>
 * </ul>
 *
 * <h3>Design Rationale</h3>
 * <ul>
 *   <li>Eliminates double-timing (Micrometer + manual nanoTime)</li>
 *   <li>Prometheus scraping and cortex dashboard see identical numbers</li>
 *   <li>Percentile histograms are available from Micrometer for free</li>
 *   <li>Uses {@link ConcurrentTasks} for virtual thread dispatch (not raw Thread API)</li>
 * </ul>
 */
public class CortexMetricsPublisher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CortexMetricsPublisher.class);

    /** Cluster topology snapshot cadence (ms). */
    private static final long CLUSTER_INTERVAL_MS = 5_000;

    /** Embedding projection cadence (ms). */
    private static final long PROJECTION_INTERVAL_MS = 10_000;

    private final MeterRegistry registry;
    private final SpectorEventBus eventBus;
    private final String nodeId;
    private final CortexTelemetryConfig config;
    private final ClusterCoordinator coordinator;  // nullable — null in standalone
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CortexMetricsPublisher(MeterRegistry registry, SpectorEventBus eventBus,
                                   String nodeId, CortexTelemetryConfig config,
                                   ClusterCoordinator coordinator) {
        this.registry = registry;
        this.eventBus = eventBus;
        this.nodeId = nodeId;
        this.config = config;
        this.coordinator = coordinator;
    }

    /**
     * Starts the periodic publisher via {@link ConcurrentTasks}.
     *
     * <p>Dispatches the polling loop as a fire-and-forget task on the
     * shared virtual thread executor — consistent with how other async
     * work (event bus dispatch, post-recall hooks) is managed.</p>
     *
     * <p>Three cadences run on the same loop:
     * <ul>
     *   <li>Diagnostic snapshot: every {@code intervalMs} (~2s)</li>
     *   <li>Cluster topology: every 5s</li>
     *   <li>Embedding projection: every 10s</li>
     * </ul>
     */
    public void start() {
        if (!config.enabled()) {
            log.info("Cortex telemetry disabled — skipping publisher");
            return;
        }

        running.set(true);
        ConcurrentTasks.fireAndForget(() -> {
            log.info("CortexMetricsPublisher started (interval={}ms)", config.intervalMs());
            long tickCount = 0;
            while (running.get()) {
                try {
                    Thread.sleep(config.intervalMs());
                    if (!running.get()) break;

                    tickCount++;

                    // ── Primary: diagnostic snapshot every tick ──
                    publishDiagnosticSnapshot();

                    // ── Cluster topology: every ~5s ──
                    long clusterTicks = CLUSTER_INTERVAL_MS / config.intervalMs();
                    if (clusterTicks < 1) clusterTicks = 1;
                    if (tickCount % clusterTicks == 0) {
                        publishClusterTopology();
                    }

                    // ── Embedding projection: every ~10s ──
                    long projectionTicks = PROJECTION_INTERVAL_MS / config.intervalMs();
                    if (projectionTicks < 1) projectionTicks = 1;
                    if (tickCount % projectionTicks == 0) {
                        publishEmbeddingProjection();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Publishes a diagnostic snapshot by reading from Micrometer gauges.
     *
     * <p>All values are sourced from the shared {@link MeterRegistry} —
     * the same registry that Prometheus scrapes. No manual computation.</p>
     */
    void publishDiagnosticSnapshot() {
        try {
            long pinnedBytes = readGauge("spector.memory.pinned.bytes");
            long jvmUsed = readGaugeWithTag("jvm.memory.used", "area", "heap");
            long jvmMax = readGaugeWithTag("jvm.memory.max", "area", "heap");
            long softFaults = readGaugeWithTag("spector.memory.page.faults", "type", "soft");
            long hardFaults = readGaugeWithTag("spector.memory.page.faults", "type", "hard");
            int totalMemories = (int) readGauge("spector.memory.count");

            eventBus.publish(new SpectorCortexMemoryDiagnosticEvent(
                    nodeId, Instant.now(),
                    0L,                     // offHeapAllocated — TBD
                    pinnedBytes,            // pinnedBytes — from Micrometer gauge
                    jvmUsed, jvmMax,        // from JvmMemoryMetrics
                    0L, 0L,                 // gpuAllocated, gpuFree — TBD
                    softFaults, hardFaults, // from MeteredSpectorMemory
                    totalMemories, 0, 0, 0, // per-tier TBD
                    0, 0,                   // hebbian edges, temporal links — TBD
                    0, 0,                   // entity nodes, edges — TBD
                    0, 0));                 // coActivation, stdp — TBD

        } catch (Exception e) {
            log.debug("Diagnostic snapshot failed: {}", e.getMessage());
        }
    }

    /**
     * Publishes cluster topology — reads node health from the coordinator.
     *
     * <p>In standalone mode (no coordinator), emits a single-node topology
     * so the dashboard always has something to display.</p>
     */
    void publishClusterTopology() {
        try {
            List<SpectorCortexClusterNodeInfo> nodes = new ArrayList<>();
            List<String[]> links = new ArrayList<>();

            if (coordinator != null) {
                // ── Clustered mode — read real shard state ──
                Map<String, Boolean> health = coordinator.healthCheck();
                for (var entry : health.entrySet()) {
                    nodes.add(new SpectorCortexClusterNodeInfo(
                            entry.getKey(),
                            entry.getValue() ? "active" : "down",
                            0,  // shard count TBD
                            0L, // memory TBD
                            0.0 // query rate TBD
                    ));
                }
                // Full mesh replication links
                for (int i = 0; i < nodes.size(); i++) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        links.add(new String[]{nodes.get(i).nodeId(), nodes.get(j).nodeId()});
                    }
                }
            } else {
                // ── Standalone — single-node topology ──
                long jvmUsed = readGaugeWithTag("jvm.memory.used", "area", "heap");
                nodes.add(new SpectorCortexClusterNodeInfo(
                        nodeId, "active",
                        1,       // 1 shard (local)
                        jvmUsed,
                        0.0      // query rate TBD from Micrometer counter
                ));
            }

            eventBus.publish(new SpectorCortexClusterTopologyEvent(
                    nodeId, Instant.now(), nodes, links));

        } catch (Exception e) {
            log.debug("Cluster topology snapshot failed: {}", e.getMessage());
        }
    }

    /**
     * Publishes embedding projection — projects stored vectors to 3D.
     *
     * <p>Uses random projection (Johnson-Lindenstrauss) for O(n·d) cost.
     * Actual projection is delegated to the engine's
     * {@code EmbeddingProjectionTelemetry} listener if wired.</p>
     *
     * <p>Currently emits an empty projection — will be populated when
     * the engine's random projection matrix is implemented.</p>
     */
    void publishEmbeddingProjection() {
        try {
            // Projection is triggered from the engine via EmbeddingProjectionTelemetry.
            // The listener (wired in SpectorNode) publishes the event.
            // Here we just ensure the engine reports periodically.
            // For now, emit an empty projection so the frontend SSE handler is exercised.
            eventBus.publish(new SpectorCortexEmbeddingProjectionEvent(
                    nodeId, Instant.now(),
                    List.of(), // points — populated by engine listener
                    null));    // queryProjection — populated on search

        } catch (Exception e) {
            log.debug("Embedding projection failed: {}", e.getMessage());
        }
    }

    /** Returns the cortex telemetry configuration. */
    public CortexTelemetryConfig config() {
        return config;
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            log.info("CortexMetricsPublisher stopped");
        }
    }

    // ── Micrometer helpers ──

    private long readGauge(String name) {
        Gauge gauge = registry.find(name).gauge();
        return gauge != null ? (long) gauge.value() : 0L;
    }

    private long readGaugeWithTag(String name, String tagKey, String tagValue) {
        Gauge gauge = registry.find(name).tag(tagKey, tagValue).gauge();
        return gauge != null ? (long) gauge.value() : 0L;
    }
}
