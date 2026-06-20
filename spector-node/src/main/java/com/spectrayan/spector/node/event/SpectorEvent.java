package com.spectrayan.spector.node.event;

import java.time.Instant;

import com.spectrayan.spector.events.NotificationScope;

/**
 * Sealed base interface for all Spector node events.
 *
 * <p>Follows Spring/Redis naming convention: {@code Spector[Domain][Action]Event}.
 * Events are published via {@link SpectorEventBus} and consumed by subscribers
 * (SSE clients, metrics collectors, audit loggers, etc.).</p>
 *
 * <h3>Event Categories</h3>
 * <ul>
 *   <li><b>Lifecycle</b>: Node start, stop, health changes</li>
 *   <li><b>Search</b>: Query completed, query failed</li>
 *   <li><b>Document</b>: Ingested, deleted, bulk completed</li>
 *   <li><b>Cluster</b>: Node joined, left, shard rebalanced, replica synced</li>
 *   <li><b>MCP</b>: Client connected, disconnected, tool executed</li>
 *   <li><b>Engine</b>: Index rebuilt, embedding provider changed</li>
 * </ul>
 *
 * <h3>Notification Scoping</h3>
 * <p>Each event declares a {@link NotificationScope} via {@link #scope()} that
 * determines which subscribers receive it. Override {@code scope()} in event
 * records to restrict delivery — e.g., ingestion events return
 * {@code NotificationScope.user(userId)} so only the initiating user sees them.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   eventBus.publish(new SpectorSearchCompletedEvent("node-1", 5, 12L, "HYBRID"));
 *   eventBus.subscribe(event -> {
 *       switch (event) {
 *           case SpectorSearchCompletedEvent e -> log.info("Search: {} results in {}ms", e.resultCount(), e.latencyMs());
 *           case SpectorDocumentIngestedEvent e -> log.info("Ingested: {}", e.documentId());
 *           default -> {}
 *       }
 *   });
 * }</pre>
 */
public sealed interface SpectorEvent permits
        // ── Lifecycle ──
        SpectorNodeStartedEvent,
        SpectorNodeStoppingEvent,
        SpectorNodeHealthChangedEvent,
        // ── Search ──
        SpectorSearchCompletedEvent,
        SpectorSearchFailedEvent,
        // ── Document ──
        SpectorDocumentIngestedEvent,
        SpectorDocumentDeletedEvent,
        SpectorBulkIngestCompletedEvent,
        // ── Cluster ──
        SpectorNodeJoinedEvent,
        SpectorNodeLeftEvent,
        SpectorShardRebalancedEvent,
        SpectorReplicaSyncCompletedEvent,
        // ── MCP ──
        SpectorMcpClientConnectedEvent,
        SpectorMcpClientDisconnectedEvent,
        SpectorMcpToolExecutedEvent,
        // ── Engine ──
        SpectorIndexRebuiltEvent,
        SpectorEmbeddingProviderChangedEvent,
        // ── Ingestion Tasks ──
        SpectorIngestionProgressEvent,
        SpectorIngestionCompletedEvent,
        // ── Cortex Dashboard ──
        SpectorCortexQueryTraceEvent,
        SpectorCortexSimdLaneEvent,
        SpectorCortexMemoryDiagnosticEvent,
        SpectorCortexGraphPulseEvent,
        SpectorCortexReflectCycleEvent,
        SpectorCortexMemorySnapshotEvent,
        SpectorCortexGpuKernelEvent,
        SpectorCortexClusterTopologyEvent,
        SpectorCortexEmbeddingProjectionEvent {

    /** Timestamp when the event occurred. */
    Instant timestamp();

    /** Node ID that originated the event. */
    String nodeId();

    /** Event type name (e.g., "search.completed"). Used in SSE {@code event:} field. */
    String eventType();

    /**
     * Notification scope — determines which subscribers receive this event.
     *
     * <p>Defaults to {@link NotificationScope#BROADCAST} (all subscribers).
     * Override in event records to restrict delivery:</p>
     * <ul>
     *   <li>{@link NotificationScope.User} — ingestion progress, query traces</li>
     *   <li>{@link NotificationScope.Tenant} — memory diagnostics, graph pulses</li>
     *   <li>{@link NotificationScope.Global} — node health, cluster topology</li>
     *   <li>{@link NotificationScope.Agent} — MCP tool execution results</li>
     *   <li>{@link NotificationScope.Topic} — named channels (ops-alerts, etc.)</li>
     * </ul>
     *
     * @return the notification scope for this event
     * @see NotificationScope
     */
    default NotificationScope scope() { return NotificationScope.BROADCAST; }

    /**
     * Target user ID for user-scoped event delivery.
     *
     * @deprecated Use {@link #scope()} instead. This method is retained for
     *             backward compatibility and delegates to the scope model:
     *             returns the userId if scope is {@link NotificationScope.User},
     *             otherwise null.
     * @return the target user ID, or null for non-user-scoped events
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    default String targetUserId() {
        return scope() instanceof NotificationScope.User u ? u.userId() : null;
    }
}
