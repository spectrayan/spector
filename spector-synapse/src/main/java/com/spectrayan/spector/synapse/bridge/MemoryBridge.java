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
package com.spectrayan.spector.synapse.bridge;

import com.spectrayan.spector.embed.EmbeddingConfig;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto.AcceptedResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Memory Access Object (MAO) — bridges the Synapse REST API with the Spector
 * cognitive memory engine.
 *
 * <p>This is the single point of integration between Synapse services and the
 * {@link SpectorMemory} engine. All engine calls go through this class.
 * Service classes call the MAO; controllers call services.</p>
 *
 * <p>When the engine is unavailable (e.g., Ollama offline at startup), the
 * bridge operates in stub mode — all reads return empty results and all writes
 * are no-ops. The {@link #isAvailable()} flag allows health checks to surface
 * this degraded state.</p>
 */
@Service
public class MemoryBridge {

    private static final Logger log = LoggerFactory.getLogger(MemoryBridge.class);

    private final SynapseProperties props;
    private volatile SpectorMemory memory;
    private volatile boolean available;

    public MemoryBridge(SynapseProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        try {
            EmbeddingConfig embedConfig = EmbeddingConfig.ollama(props.ollama().embedModel())
                    .withBaseUrl(props.ollama().baseUrl());
            EmbeddingProvider embedder = new OllamaEmbeddingProvider(embedConfig);

            String dataDir = System.getenv("SPECTOR_DATA_DIR");
            if (dataDir == null || dataDir.isBlank()) {
                dataDir = System.getProperty("java.io.tmpdir") + "/spector-synapse";
            }
            Path dataPath = Path.of(dataDir, "cognitive");
            int dims = props.memory().dimensions() > 0 ? props.memory().dimensions() : 768;

            memory = DefaultSpectorMemory.builder()
                    .dimensions(dims)
                    .embeddingProvider(embedder)
                    .persistenceMode(MemoryPersistenceMode.DISK)
                    .persistence(dataPath)
                    .semanticCapacity(10_000)
                    .hebbianGraphCapacity(10_000)
                    .temporalChainCapacity(10_000)
                    .build();

            available = true;
            log.info("[MemoryBridge] SpectorMemory initialized — dims={}, path={}",
                    dims, dataPath);
        } catch (Exception e) {
            available = false;
            log.warn("[MemoryBridge] SpectorMemory initialization failed — running in stub mode: {}",
                    e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        if (memory != null) {
            try {
                memory.close();
                log.info("[MemoryBridge] SpectorMemory closed");
            } catch (Exception e) {
                log.warn("[MemoryBridge] Error closing memory: {}", e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // WRITE — CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * Store a memory via the cognitive engine (legacy simple store).
     */
    public StoreResponse store(StoreRequest request) {
        if (!available) {
            return new StoreResponse("stub-" + System.nanoTime(), request.text(),
                    "SEMANTIC", 0.0, "Memory stored (stub mode — engine not available)");
        }

        String[] tags = request.tags() != null
                ? request.tags().toArray(String[]::new)
                : new String[0];

        try {
            CompletableFuture<String> future = memory.remember(
                    request.text(), MemoryType.SEMANTIC, MemorySource.USER_STATED, tags);
            String id = future.join();
            log.info("[MemoryBridge] Stored memory id={}, tags={}", id, Arrays.toString(tags));
            return new StoreResponse(id, request.text(), "SEMANTIC", 1.0,
                    "Memory stored in cognitive engine");
        } catch (Exception e) {
            log.error("[MemoryBridge] Store failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Memory store failed: " + e.getMessage(), e);
        }
    }

    /**
     * Remember a memory via the full Cortex remember flow (async, 202 Accepted).
     *
     * <p>Accepts the full {@link RememberRequest} with ICNU cognitive hints,
     * tier, source, and comma-separated tags. Returns immediately after
     * submitting the background task.</p>
     */
    public AcceptedResponse remember(RememberRequest request) {
        String effectiveId = (request.id() != null && !request.id().isBlank())
                ? request.id()
                : UUID.randomUUID().toString();

        if (!available) {
            log.warn("[MemoryBridge] Remember called in stub mode — id={}", effectiveId);
            return AcceptedResponse.forRemember("stub-task-" + System.nanoTime(), effectiveId);
        }

        MemoryType tier = safeMemoryType(request.effectiveTier(), MemoryType.SEMANTIC);
        MemorySource source = safeMemorySource(request.effectiveSource(), MemorySource.OBSERVED);
        String[] tags = request.tagsArray();

        IngestionHints hints = null;
        if (request.hasCognitiveHints()) {
            float interest = request.interest() != null ? request.interest() : 0f;
            float challenge = request.challenge() != null ? request.challenge() : 0f;
            float urgency = request.urgency() != null ? request.urgency() : 0f;
            int valence = request.valence() != null ? request.valence() : 0;
            int arousal = request.arousal() != null ? request.arousal() : 0;
            hints = new IngestionHints(interest, challenge, urgency,
                    (byte) Math.clamp(valence, -128, 127),
                    (byte) Math.clamp(arousal, 0, 255));
        }

        String taskId = UUID.randomUUID().toString();
        final IngestionHints finalHints = hints;
        final String finalId = effectiveId;

        // Submit async — do not block the HTTP thread
        CompletableFuture.runAsync(() -> {
            try {
                memory.remember(finalId, request.text(), tier, source, finalHints, tags).join();
                log.info("[MemoryBridge] Remembered id={} tier={}", finalId, tier);
            } catch (Exception e) {
                log.error("[MemoryBridge] Remember failed id={}: {}", finalId, e.getMessage(), e);
            }
        });

        return AcceptedResponse.forRemember(taskId, effectiveId);
    }

    /**
     * Tombstone (forget) a memory by ID.
     */
    public void forget(String id) {
        if (!available) return;
        try {
            memory.forget(id);
            log.info("[MemoryBridge] Forgot memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Forget failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Reinforce a memory via dopamine feedback using an integer valence.
     */
    public void reinforce(String id, int valence) {
        if (!available) return;
        try {
            byte byteValence = (byte) Math.clamp(valence, -128, 127);
            memory.reinforce(id, byteValence);
            log.info("[MemoryBridge] Reinforced memory id={} valence={}", id, byteValence);
        } catch (Exception e) {
            log.error("[MemoryBridge] Reinforce failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Suppress a memory from future recall results.
     */
    public void suppress(String id, String reason) {
        if (!available) return;
        try {
            if (reason != null && !reason.isBlank()) {
                memory.suppress(id, reason);
            } else {
                memory.suppress(id);
            }
            log.info("[MemoryBridge] Suppressed memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Suppress failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Unsuppress a previously suppressed memory.
     */
    public void unsuppress(String id) {
        if (!available) return;
        try {
            memory.unsuppress(id);
            log.info("[MemoryBridge] Unsuppressed memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Unsuppress failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Mark a memory as resolved.
     */
    public void markResolved(String id) {
        if (!available) return;
        try {
            memory.markResolved(id);
            log.info("[MemoryBridge] Resolved memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Resolve failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Mark a memory as unresolved.
     */
    public void markUnresolved(String id) {
        if (!available) return;
        try {
            memory.markUnresolved(id);
            log.info("[MemoryBridge] Unresolved memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Unresolve failed: {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // READ — QUERY
    // ══════════════════════════════════════════════════════════════

    /**
     * Recall memories via the fused cognitive scoring pipeline.
     */
    public List<RecallResult> recall(RecallRequest request) {
        if (!available) {
            return List.of();
        }

        try {
            List<CognitiveResult> results = memory.recall(request.query());
            int limit = request.topK() > 0 ? request.topK() : 10;

            return results.stream()
                    .limit(limit)
                    .map(r -> new RecallResult(
                            r.id(),
                            r.text(),
                            r.memoryType().name(),
                            (double) r.score(),
                            r.memoryType().name(),
                            String.format("%.1f days", r.ageDays()),
                            Arrays.asList(r.synapticTags())
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("[MemoryBridge] Recall failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Returns a paginated memory table view across all tiers.
     *
     * <p>Reads 64-byte synaptic headers from off-heap tier stores for fast
     * SWMR-safe access, joins with MemoryIndex for text/tags. Aligns with
     * the node module's {@code getMemoryTable} implementation.</p>
     *
     * @param page           page number (0-based)
     * @param pageSize       rows per page
     * @param tierFilter     optional tier name filter (null = all tiers)
     * @param showTombstoned whether to include tombstoned records
     * @return paginated table response
     */
    public MemoryTableResponse getMemoryTable(int page, int pageSize, String tierFilter,
                                              boolean showTombstoned) {
        if (!available) {
            Map<String, Integer> emptyCounts = Map.of(
                    "WORKING", 0, "EPISODIC", 0, "SEMANTIC", 0, "PROCEDURAL", 0);
            Map<String, Float> emptyRatios = Map.of(
                    "WORKING", 0f, "EPISODIC", 0f, "SEMANTIC", 0f, "PROCEDURAL", 0f);
            return new MemoryTableResponse(List.of(), 0, page, pageSize, emptyCounts, emptyRatios);
        }

        var admin = memory.admin();
        var tierRouter = admin.tierRouter();
        var index = admin.index();

        List<MemoryTableRow> allRows = new ArrayList<>();
        Map<String, Integer> tierCounts = new LinkedHashMap<>();

        for (MemoryType type : MemoryType.values()) {
            if (tierFilter != null && !tierFilter.equalsIgnoreCase(type.name())) continue;

            var store = tierRouter.get(type);
            if (!(store instanceof AbstractTierStore ats)) continue;

            int visibleCount = ats.visibleCount();
            tierCounts.put(type.name(), visibleCount);
            var layout = ats.layout();
            long baseOffset = ats.isPersistent()
                    ? AbstractTierStore.METADATA_HEADER_BYTES : 0;

            for (int i = 0; i < visibleCount; i++) {
                long offset = baseOffset + (long) i * layout.stride();
                var header = layout.readHeader(ats.segment(), offset);
                byte flags = header.flags();

                boolean tombstoned = SynapticHeaderConstants.isTombstoned(flags);
                if (!showTombstoned && tombstoned) continue;

                // NOTE: suppressed state is not encoded in the 64-byte synaptic header flags.
                // Suppression is tracked at the index/filter layer inside SpectorMemory.
                // We expose false here; the engine already filters suppressed memories from recall.
                boolean suppressed = false;
                boolean pinned = SynapticHeaderConstants.isPinned(flags);
                boolean resolved = SynapticHeaderConstants.isResolved(flags);
                boolean consolidated = SynapticHeaderConstants.isConsolidated(flags);
                int arousal = Byte.toUnsignedInt(header.arousal());

                String id = index.findIdByOffset(type, offset);
                String text = id != null ? index.text(id) : "";
                String textPreview = text != null && text.length() > 200
                        ? text.substring(0, 200) + "…" : text;
                var source = id != null ? index.source(id) : MemorySource.OBSERVED;
                String[] tags = id != null ? index.tags(id) : new String[0];
                Map<String, String> metadata = id != null ? index.metadata(id) : null;
                if (metadata != null && metadata.isEmpty()) metadata = null;

                String effectiveId = id != null ? id : "unknown-" + type.name() + "-" + i;
                String createdAt = Instant.ofEpochMilli(header.timestampMs()).toString();

                allRows.add(new MemoryTableRow(
                        effectiveId,
                        text != null ? text : "",
                        textPreview != null ? textPreview : "",
                        type.name(),
                        source != null ? source.name() : "OBSERVED",
                        header.importance(),
                        header.valence(),
                        arousal,
                        header.timestampMs(),
                        header.agentRecallCount(),
                        header.agentRecallCount(),
                        tombstoned,
                        suppressed,
                        pinned,
                        resolved,
                        consolidated,
                        tags != null ? Arrays.asList(tags) : List.of(),
                        header.synapticTags(),
                        createdAt,
                        metadata
                ));
            }
        }

        // Sort by timestamp descending (newest first)
        allRows.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));

        // Paginate
        int totalCount = allRows.size();
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        List<MemoryTableRow> pageRows = fromIndex < totalCount
                ? allRows.subList(fromIndex, toIndex) : List.of();

        // Tombstone ratios
        Map<String, Float> tombstoneRatios = new LinkedHashMap<>();
        var ratios = admin.tombstoneRatios();
        for (var entry : ratios.entrySet()) {
            tombstoneRatios.put(entry.getKey().name(), entry.getValue());
        }

        return new MemoryTableResponse(pageRows, totalCount, page, pageSize,
                tierCounts, tombstoneRatios);
    }

    /**
     * Returns comprehensive status of the cognitive memory system.
     *
     * <p>Aligns with the {@code MemoryStatus} interface in the Angular
     * {@code MemoryTableService}.</p>
     */
    public MemoryStatusResponse getStatus() {
        if (!available) {
            return new MemoryStatusResponse(0,
                    Map.of("WORKING", 0, "EPISODIC", 0, "SEMANTIC", 0, "PROCEDURAL", 0),
                    0, 0, 0, 0);
        }
        int total = memory.totalMemories();
        Map<String, Integer> counts = Map.of(
                "WORKING", memory.memoryCount(MemoryType.WORKING),
                "EPISODIC", memory.memoryCount(MemoryType.EPISODIC),
                "SEMANTIC", memory.memoryCount(MemoryType.SEMANTIC),
                "PROCEDURAL", memory.memoryCount(MemoryType.PROCEDURAL)
        );
        int hebbian = memory.hebbianGraph() != null ? memory.hebbianGraph().totalEdges() : 0;
        int entityNodes = memory.entityGraph() != null ? memory.entityGraph().entityCount() : 0;
        int entityEdges = memory.entityGraph() != null ? memory.entityGraph().edgeCount() : 0;

        int temporalLinks = 0;
        if (memory.temporalChain() != null) {
            int cap = memory.temporalChain().capacity();
            for (int i = 0; i < cap; i++) {
                if (memory.temporalChain().isLinked(i)) temporalLinks++;
            }
        }

        return new MemoryStatusResponse(total, counts, hebbian, temporalLinks,
                entityNodes, entityEdges);
    }

    /**
     * Triggers a reflect (sleep consolidation) cycle.
     *
     * @return reflect response with tombstoned count and duration
     */
    public ReflectResponse reflect() {
        if (!available) {
            return new ReflectResponse(0, 0L, "Reflect skipped — engine not available");
        }
        try {
            ReflectReport report = memory.reflect();
            long durationMs = report.duration().toMillis();
            log.info("[MemoryBridge] Reflect completed: tombstoned={}, duration={}ms",
                    report.tombstonedCount(), durationMs);
            return new ReflectResponse(report.tombstonedCount(), durationMs,
                    "Consolidation cycle completed");
        } catch (Exception e) {
            log.error("[MemoryBridge] Reflect failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Reflect failed: " + e.getMessage(), e);
        }
    }

    /**
     * Triggers vacuum compaction for a specific tier.
     *
     * @param tierName the tier to compact
     * @return compaction result, or a no-op result if nothing to compact
     */
    public CompactionResult vacuum(String tierName) {
        if (!available) {
            return new CompactionResult(tierName, 0, 0, 0, 0L, 0L);
        }
        try {
            MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
            long start = System.currentTimeMillis();
            var result = memory.admin().vacuum(tier);
            long durationMs = System.currentTimeMillis() - start;
            if (result == null) {
                return new CompactionResult(tierName, 0, 0, 0, 0L, durationMs);
            }
            return new CompactionResult(
                    tierName,
                    result.beforeCount(),
                    result.afterCount(),
                    result.tombstonesRemoved(),
                    result.bytesReclaimed(),
                    durationMs
            );
        } catch (Exception e) {
            log.error("[MemoryBridge] Vacuum failed for tier={}: {}", tierName, e.getMessage(), e);
            throw new IllegalStateException("Vacuum failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ingest a file's text content into memory asynchronously.
     *
     * @param content      file text content
     * @param originalName original file name (for metadata)
     * @param tierName     target memory tier
     * @param sourceName   provenance source
     * @return accepted response with taskId and documentId
     */
    public AcceptedResponse ingestFile(String content, String originalName,
                                       String tierName, String sourceName) {
        String documentId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();

        if (!available) {
            log.warn("[MemoryBridge] Ingest-file called in stub mode — file={}", originalName);
            return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
        }

        MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
        MemorySource source = safeMemorySource(sourceName, MemorySource.OBSERVED);
        String[] provenanceTags = new String[]{originalName};

        CompletableFuture.runAsync(() -> {
            try {
                memory.remember(documentId, content, tier, source, (IngestionHints) null, provenanceTags).join();
                log.info("[MemoryBridge] File ingested: name={}, id={}", originalName, documentId);
            } catch (Exception e) {
                log.error("[MemoryBridge] File ingest failed name={}: {}", originalName, e.getMessage(), e);
            }
        });

        return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
    }

    // ══════════════════════════════════════════════════════════════
    // INTROSPECTION
    // ══════════════════════════════════════════════════════════════

    /** Check if the memory engine is available. */
    public boolean isAvailable() { return available; }

    /** Get the underlying SpectorMemory instance (for advanced bridge use). */
    public SpectorMemory engine() { return memory; }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static MemoryType safeMemoryType(String name, MemoryType fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemoryType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static MemorySource safeMemorySource(String name, MemorySource fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemorySource.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
