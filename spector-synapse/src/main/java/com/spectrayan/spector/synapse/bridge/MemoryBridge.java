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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Memory Access Object (MAO) — thin adapter layer between Synapse services
 * and the Spring-managed {@link SpectorMemory} engine.
 *
 * <h3>Architecture</h3>
 * <p>The {@code SpectorMemory} bean is created and lifecycle-managed by
 * {@code SpectorAutoConfiguration} from the {@code spector-spring} module.
 * This class no longer constructs or manages the engine — it simply wraps
 * engine calls with DTO conversion, error handling, and stub-mode guards.</p>
 *
 * <pre>
 *   MemoryController
 *        │
 *   MemoryService       (orchestration, input validation)
 *        │
 *   MemoryBridge (MAO)  (DTO ↔ engine API translation, error boundary)
 *        │
 *   SpectorMemory       (Spring-managed bean from spector-spring auto-config)
 * </pre>
 *
 * <p>When the memory engine is not available (e.g., Ollama offline at startup
 * caused {@code SpectorAutoConfiguration} to skip the bean), all reads return
 * empty results and writes are no-ops. Call {@link #isAvailable()} to check.</p>
 */
@Service
public class MemoryBridge {

    private static final Logger log = LoggerFactory.getLogger(MemoryBridge.class);

    private final SpectorMemory memory;

    /**
     * Receives the Spring-managed {@link SpectorMemory} bean via
     * {@link ObjectProvider} so that startup succeeds even when the bean is
     * absent (e.g., Ollama offline → auto-config skips creation).
     */
    public MemoryBridge(ObjectProvider<SpectorMemory> memoryProvider) {
        this.memory = memoryProvider.getIfAvailable();
        if (this.memory != null) {
            log.info("[MemoryBridge] SpectorMemory available — engine ready");
        } else {
            log.warn("[MemoryBridge] SpectorMemory bean not available — running in stub mode. " +
                    "Check spector.memory.enabled=true and that the EmbeddingProvider started correctly.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // AVAILABILITY
    // ══════════════════════════════════════════════════════════════

    /** Returns true if the SpectorMemory engine bean is available. */
    public boolean isAvailable() { return memory != null; }

    /** Returns the underlying engine (may be null in stub mode). */
    public SpectorMemory engine() { return memory; }

    // ══════════════════════════════════════════════════════════════
    // WRITE — CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * Store a memory (legacy simple form).
     */
    public StoreResponse store(StoreRequest request) {
        if (!isAvailable()) {
            return new StoreResponse("stub-" + System.nanoTime(), request.text(),
                    "SEMANTIC", 0.0, "Memory stored (stub mode — engine not available)");
        }
        String[] tags = request.tags() != null
                ? request.tags().toArray(String[]::new) : new String[0];
        try {
            String id = memory.remember(request.text(), MemoryType.SEMANTIC,
                    MemorySource.USER_STATED, tags).join();
            log.info("[MemoryBridge] Stored memory id={}, tags={}", id, Arrays.toString(tags));
            return new StoreResponse(id, request.text(), "SEMANTIC", 1.0,
                    "Memory stored in cognitive engine");
        } catch (Exception e) {
            log.error("[MemoryBridge] Store failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Memory store failed: " + e.getMessage(), e);
        }
    }

    /**
     * Remember a memory via the full Cortex UI flow (async, 202 Accepted).
     */
    public AcceptedResponse remember(RememberRequest request) {
        String effectiveId = (request.id() != null && !request.id().isBlank())
                ? request.id() : UUID.randomUUID().toString();

        if (!isAvailable()) {
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
        if (!isAvailable()) return;
        try {
            memory.forget(id);
            log.info("[MemoryBridge] Forgot memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Forget failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Reinforce a memory via dopamine feedback.
     */
    public void reinforce(String id, int valence) {
        if (!isAvailable()) return;
        try {
            memory.reinforce(id, (byte) Math.clamp(valence, -128, 127));
            log.info("[MemoryBridge] Reinforced memory id={} valence={}", id, valence);
        } catch (Exception e) {
            log.error("[MemoryBridge] Reinforce failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Suppress a memory from future recall results.
     */
    public void suppress(String id, String reason) {
        if (!isAvailable()) return;
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

    /** Unsuppress a previously suppressed memory. */
    public void unsuppress(String id) {
        if (!isAvailable()) return;
        try {
            memory.unsuppress(id);
            log.info("[MemoryBridge] Unsuppressed memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Unsuppress failed: {}", e.getMessage(), e);
        }
    }

    /** Mark a memory as resolved. */
    public void markResolved(String id) {
        if (!isAvailable()) return;
        try {
            memory.markResolved(id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Resolve failed: {}", e.getMessage(), e);
        }
    }

    /** Mark a memory as unresolved. */
    public void markUnresolved(String id) {
        if (!isAvailable()) return;
        try {
            memory.markUnresolved(id);
        } catch (Exception e) {
            log.error("[MemoryBridge] Unresolve failed: {}", e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // READ — QUERY
    // ══════════════════════════════════════════════════════════════

    /**
     * Cognitive recall via the fused scoring pipeline.
     */
    public List<RecallResult> recall(RecallRequest request) {
        if (!isAvailable()) return List.of();
        try {
            List<CognitiveResult> results = memory.recall(request.query());
            int limit = request.topK() > 0 ? request.topK() : 10;
            return results.stream().limit(limit)
                    .map(r -> new RecallResult(r.id(), r.text(), r.memoryType().name(),
                            (double) r.score(), r.memoryType().name(),
                            String.format("%.1f days", r.ageDays()),
                            Arrays.asList(r.synapticTags())))
                    .toList();
        } catch (Exception e) {
            log.error("[MemoryBridge] Recall failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Returns a paginated memory table view for the Cortex UI.
     */
    public MemoryTableResponse getMemoryTable(int page, int pageSize, String tierFilter,
                                              boolean showTombstoned) {
        if (!isAvailable()) {
            var emptyCounts = Map.of("WORKING", 0, "EPISODIC", 0, "SEMANTIC", 0, "PROCEDURAL", 0);
            var emptyRatios = Map.of("WORKING", 0f, "EPISODIC", 0f, "SEMANTIC", 0f, "PROCEDURAL", 0f);
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
            long baseOffset = ats.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0;

            for (int i = 0; i < visibleCount; i++) {
                long offset = baseOffset + (long) i * layout.stride();
                var header = layout.readHeader(ats.segment(), offset);
                byte flags = header.flags();

                boolean tombstoned = SynapticHeaderConstants.isTombstoned(flags);
                if (!showTombstoned && tombstoned) continue;

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
                allRows.add(new MemoryTableRow(
                        effectiveId,
                        text != null ? text : "",
                        textPreview != null ? textPreview : "",
                        type.name(),
                        source != null ? source.name() : "OBSERVED",
                        header.importance(), header.valence(), arousal,
                        header.timestampMs(), header.agentRecallCount(), header.agentRecallCount(),
                        tombstoned,
                        // suppressed: not in 64-byte header — engine filters suppressed from recall
                        false,
                        pinned, resolved, consolidated,
                        tags != null ? Arrays.asList(tags) : List.of(),
                        header.synapticTags(),
                        Instant.ofEpochMilli(header.timestampMs()).toString(),
                        metadata
                ));
            }
        }

        allRows.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));

        int totalCount = allRows.size();
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        List<MemoryTableRow> pageRows = fromIndex < totalCount
                ? allRows.subList(fromIndex, toIndex) : List.of();

        Map<String, Float> tombstoneRatios = new LinkedHashMap<>();
        admin.tombstoneRatios().forEach((k, v) -> tombstoneRatios.put(k.name(), v));

        return new MemoryTableResponse(pageRows, totalCount, page, pageSize,
                tierCounts, tombstoneRatios);
    }

    /**
     * Returns cognitive memory status (tier counts, graph stats).
     */
    public MemoryStatusResponse getStatus() {
        if (!isAvailable()) {
            return new MemoryStatusResponse(0,
                    Map.of("WORKING", 0, "EPISODIC", 0, "SEMANTIC", 0, "PROCEDURAL", 0),
                    0, 0, 0, 0);
        }
        int total = memory.totalMemories();
        var counts = Map.of(
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
     * Triggers a sleep consolidation (reflect) cycle.
     */
    public ReflectResponse reflect() {
        if (!isAvailable()) {
            return new ReflectResponse(0, 0L, "Reflect skipped — engine not available");
        }
        try {
            ReflectReport report = memory.reflect();
            long durationMs = report.duration().toMillis();
            log.info("[MemoryBridge] Reflect: tombstoned={}, duration={}ms",
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
     */
    public CompactionResult vacuum(String tierName) {
        if (!isAvailable()) return new CompactionResult(tierName, 0, 0, 0, 0L, 0L);
        try {
            MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
            long start = System.currentTimeMillis();
            var result = memory.admin().vacuum(tier);
            long durationMs = System.currentTimeMillis() - start;
            if (result == null) return new CompactionResult(tierName, 0, 0, 0, 0L, durationMs);
            return new CompactionResult(tierName, result.beforeCount(), result.afterCount(),
                    result.tombstonesRemoved(), result.bytesReclaimed(), durationMs);
        } catch (Exception e) {
            log.error("[MemoryBridge] Vacuum failed for tier={}: {}", tierName, e.getMessage(), e);
            throw new IllegalStateException("Vacuum failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ingest file content into memory asynchronously.
     */
    public AcceptedResponse ingestFile(String content, String originalName,
                                       String tierName, String sourceName) {
        String documentId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        if (!isAvailable()) {
            log.warn("[MemoryBridge] Ingest-file in stub mode — file={}", originalName);
            return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
        }
        MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
        MemorySource source = safeMemorySource(sourceName, MemorySource.OBSERVED);
        CompletableFuture.runAsync(() -> {
            try {
                memory.remember(documentId, content, tier, source,
                        (IngestionHints) null, new String[]{originalName}).join();
                log.info("[MemoryBridge] File ingested: name={}, id={}", originalName, documentId);
            } catch (Exception e) {
                log.error("[MemoryBridge] File ingest failed name={}: {}", originalName, e.getMessage(), e);
            }
        });
        return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static MemoryType safeMemoryType(String name, MemoryType fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return MemoryType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    private static MemorySource safeMemorySource(String name, MemorySource fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return MemorySource.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}
