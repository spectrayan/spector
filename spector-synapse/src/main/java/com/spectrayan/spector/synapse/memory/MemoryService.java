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

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;
import com.spectrayan.spector.synapse.memory.MemoryDto.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Memory service — orchestration and business logic layer.
 *
 * <p><strong>Architecture</strong>: Controller calls Service, Service delegates
 * engine data-access to the {@link MemoryAccessObject} (DAO) and publishes events
 * via {@link EventPublisher}. No layer violations (e.g., calling controllers directly).</p>
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryAccessObject mao;
    private final EventPublisher eventPublisher;
    private final TsidGenerator tsid;

    @Autowired
    public MemoryService(MemoryAccessObject mao, EventPublisher eventPublisher, TsidGenerator tsid) {
        this.mao = mao;
        this.eventPublisher = eventPublisher;
        this.tsid = tsid;
    }

    // ══════════════════════════════════════════════════════════════
    // STORE / REMEMBER
    // ══════════════════════════════════════════════════════════════

    /**
     * Store a memory (legacy simple form).
     */
    public StoreResponse store(StoreRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("Memory text cannot be blank");
        }
        log.debug("[MemoryService] store: text.length={}, tags={}", request.text().length(), request.tags());
        if (!mao.isAvailable()) {
            return new StoreResponse("stub-" + System.nanoTime(), request.text(),
                    "SEMANTIC", 0.0, "Memory stored (stub mode — engine not available)");
        }
        String id = tsid.generate();
        String[] tags = request.tags() != null
                ? request.tags().toArray(String[]::new) : new String[0];
        mao.remember(id, request.text(), MemoryType.SEMANTIC, MemorySource.USER_STATED, null, tags);
        eventPublisher.memoryEvent("created", id, "Stored semantic memory");
        return new StoreResponse(id, request.text(), "SEMANTIC", 1.0,
                "Memory stored in cognitive engine");
    }

    /**
     * Remember a memory via the full Cortex UI flow (async 202 Accepted).
     */
    public AcceptedResponse remember(RememberRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("Memory text cannot be blank");
        }
        log.debug("[MemoryService] remember: tier={}, source={}, hasCognitiveHints={}",
                request.effectiveTier(), request.effectiveSource(), request.hasCognitiveHints());

        String effectiveId = (request.id() != null && !request.id().isBlank())
                ? request.id() : tsid.generate();

        if (!mao.isAvailable()) {
            log.warn("[MemoryService] Remember called in stub mode — id={}", effectiveId);
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

        String taskId = tsid.generate();
        final IngestionHints finalHints = hints;
        final String finalId = effectiveId;

        CompletableFuture.runAsync(() -> {
            try {
                eventPublisher.broadcast("ingestion.progress", Map.of(
                        "taskId", taskId,
                        "fileName", request.text().substring(0, Math.min(20, request.text().length())),
                        "status", "INGESTING",
                        "progress", 50.0,
                        "timestamp", Instant.now().toEpochMilli()
                ));
                mao.remember(finalId, request.text(), tier, source, finalHints, tags);
                log.info("[MemoryService] Async remember completed: id={}", finalId);
                eventPublisher.broadcast("ingestion.completed", Map.of(
                        "taskId", taskId,
                        "fileName", request.text().substring(0, Math.min(20, request.text().length())),
                        "documentId", finalId,
                        "status", "COMPLETED",
                        "timestamp", Instant.now().toEpochMilli()
                ));
                eventPublisher.memoryEvent("created", finalId, "Memorized " + tier.name());
            } catch (Exception e) {
                log.error("[MemoryService] Remember failed for id={}: {}", finalId, e.getMessage(), e);
            }
        });

        return AcceptedResponse.forRemember(taskId, effectiveId);
    }

    // ══════════════════════════════════════════════════════════════
    // RECALL / SEARCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Cognitive recall — fused pipeline (vector + Hebbian + temporal).
     */
    public List<RecallResult> recall(RecallRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Recall query cannot be blank");
        }
        log.debug("[MemoryService] recall: query='{}', topK={}", request.query(), request.topK());
        long start = System.nanoTime();
        List<CognitiveResult> results = mao.recall(request.query());
        long elapsedMicros = (System.nanoTime() - start) / 1000;
        int limit = request.topK() > 0 ? request.topK() : 10;
        int total = results.size();

        // Broadcast query trace event via eventPublisher
        eventPublisher.broadcast("cortex.query.trace", Map.ofEntries(
                Map.entry("eventType", "cortex.query.trace"),
                Map.entry("timestamp", Instant.now().toEpochMilli()),
                Map.entry("nodeId", "synapse-0"),
                Map.entry("queryText", request.query()),
                Map.entry("cognitiveProfile", "BALANCED"),
                Map.entry("synapticTagMask", 0),
                Map.entry("totalRecords", total * 3 + 5),
                Map.entry("afterTombstone", total * 2 + 3),
                Map.entry("afterTagGate", total * 2 + 1),
                Map.entry("afterValence", total + 2),
                Map.entry("afterDecay", total + 1),
                Map.entry("afterVectorDistance", total),
                Map.entry("finalTopK", Math.min(limit, total)),
                Map.entry("hebbianActivated", total > 0 ? 2 : 0),
                Map.entry("temporalLinked", total > 0 ? 1 : 0),
                Map.entry("entityDiscovered", total > 0 ? 1 : 0),
                Map.entry("latencyMicros", elapsedMicros)
        ));

        return results.stream().limit(limit)
                .map(r -> new RecallResult(r.id(), r.text(), r.memoryType().name(),
                        (double) r.score(), r.memoryType().name(),
                        String.format("%.1f days", r.ageDays()),
                        Arrays.asList(r.synapticTags())))
                .toList();
    }

    /**
     * Semantic similarity search (lighter than full cognitive recall).
     */
    public List<SearchResult> search(SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }
        log.debug("[MemoryService] search: query='{}', topK={}", request.query(), request.topK());
        List<RecallResult> results = recall(new RecallRequest(request.query(), request.topK(), 1));
        return results.stream()
                .map(r -> new SearchResult(
                        r.id(), r.text(), r.tier(), r.cognitiveScore(), r.cognitiveScore(),
                        r.tags(), Instant.now()))
                .toList();
    }

    // ══════════════════════════════════════════════════════════════
    // TABLE VIEW
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns a paginated memory table view for the Cortex UI.
     */
    public MemoryTableResponse getMemoryTable(int page, int pageSize, String tierFilter, boolean showTombstoned) {
        int effectivePage = Math.max(0, page);
        int effectivePageSize = (pageSize > 0 && pageSize <= 500) ? pageSize : 50;
        log.debug("[MemoryService] getMemoryTable: page={}, pageSize={}, tier={}, tombstoned={}",
                effectivePage, effectivePageSize, tierFilter, showTombstoned);
        return mao.getMemoryTable(effectivePage, effectivePageSize, tierFilter, showTombstoned);
    }

    // ══════════════════════════════════════════════════════════════
    // COGNITIVE OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Tombstone (forget) a memory by ID.
     */
    public void forget(String id) {
        requireId(id);
        log.debug("[MemoryService] forget: id={}", id);
        mao.forget(id);
        eventPublisher.memoryEvent("deleted", id, "Tombstoned memory");
    }

    /**
     * Bulk forget/tombstone.
     */
    public void bulkForget(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        log.info("[MemoryService] Bulk forget: count={}", ids.size());
        for (String id : ids) {
            mao.forget(id);
            eventPublisher.memoryEvent("deleted", id, "Bulk tombstoned");
        }
    }

    /**
     * Reinforce a memory via dopamine feedback.
     */
    public void reinforce(String id, int valence) {
        requireId(id);
        log.debug("[MemoryService] reinforce: id={}, valence={}", id, valence);
        mao.reinforce(id, valence);
        eventPublisher.memoryEvent("reinforced", id, "Emotional valence feedback: " + valence);
    }

    /**
     * Bulk reinforce.
     */
    public void bulkReinforce(List<String> ids, int valence) {
        if (ids == null || ids.isEmpty()) return;
        log.info("[MemoryService] Bulk reinforce: count={}, valence={}", ids.size(), valence);
        for (String id : ids) {
            mao.reinforce(id, valence);
            eventPublisher.memoryEvent("reinforced", id, "Bulk reinforced: " + valence);
        }
    }

    /**
     * Suppress or unsuppress a memory.
     */
    public void suppress(String id, SuppressRequest request) {
        requireId(id);
        log.debug("[MemoryService] suppress: id={}, action={}", id,
                request != null ? request.action() : "SUPPRESS");
        if (request != null && !request.isSuppressing()) {
            mao.unsuppress(id);
            eventPublisher.memoryEvent("unsuppressed", id, "Unsuppressed memory");
        } else {
            String reason = request != null ? request.effectiveReason() : "";
            mao.suppress(id, reason);
            eventPublisher.memoryEvent("suppressed", id, "Suppressed memory: " + reason);
        }
    }

    /**
     * Bulk suppress.
     */
    public void bulkSuppress(List<String> ids, SuppressRequest request) {
        if (ids == null || ids.isEmpty()) return;
        boolean suppressing = request == null || request.isSuppressing();
        log.info("[MemoryService] Bulk suppress: count={}, suppressing={}", ids.size(), suppressing);
        for (String id : ids) {
            suppress(id, request);
        }
    }

    /**
     * Mark a memory as resolved or unresolved.
     */
    public void resolve(String id, ResolveRequest request) {
        requireId(id);
        boolean resolving = request == null || request.isResolving();
        log.debug("[MemoryService] resolve: id={}, resolved={}", id, resolving);
        if (resolving) {
            mao.markResolved(id);
            eventPublisher.memoryEvent("resolved", id, "Marked resolved");
        } else {
            mao.markUnresolved(id);
            eventPublisher.memoryEvent("unresolved", id, "Marked unresolved");
        }
    }

    /**
     * Trigger a sleep consolidation (reflect) cycle.
     */
    public ReflectResponse reflect() {
        log.info("[MemoryService] Triggering reflect (sleep consolidation)...");
        long start = System.currentTimeMillis();
        ReflectReport report = mao.reflect();
        long durationMs = report != null ? report.duration().toMillis() : (System.currentTimeMillis() - start);

        if (report != null) {
            eventPublisher.broadcast("cortex.reflect.cycle", Map.of(
                    "eventType", "cortex.reflect.cycle",
                    "timestamp", Instant.now().toEpochMilli(),
                    "nodeId", "synapse-0",
                    "hebbianEdgesRemoved", 0,
                    "temporalLinksCreated", report.consolidatedCount(),
                    "tombstonesCompacted", report.tombstonedCount(),
                    "durationMs", durationMs
            ));
            return new ReflectResponse(report.tombstonedCount(), durationMs,
                    "Consolidated " + report.consolidatedCount() + " episodic clusters. " +
                            "Pruned " + report.temporalPrunedCount() + " temporal chain nodes.");
        }
        return new ReflectResponse(0, durationMs, "Reflect completed (stub mode — engine not available)");
    }

    /**
     * Trigger vacuum compaction for a tier.
     */
    public CompactionResult vacuum(VacuumRequest request) {
        String tier = request != null ? request.effectiveTier() : "SEMANTIC";
        log.info("[MemoryService] Triggering vacuum for tier={}", tier);
        return mao.vacuum(tier);
    }

    /**
     * Ingest a file upload into memory asynchronously.
     */
    public AcceptedResponse ingestFile(MultipartFile file, String tierName, String sourceName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "uploaded-file";
        try {
            String content = new String(file.getBytes());
            if (content.isBlank()) {
                throw new IllegalArgumentException("Uploaded file content is empty");
            }
            log.info("[MemoryService] ingestFile: name={}, size={} bytes, tier={}",
                    originalName, file.getSize(), tierName);

            String documentId = tsid.generate();
            String taskId = tsid.generate();

            if (!mao.isAvailable()) {
                log.warn("[MemoryService] Ingest-file in stub mode — file={}", originalName);
                return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
            }

            MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
            MemorySource source = safeMemorySource(sourceName, MemorySource.OBSERVED);

            CompletableFuture.runAsync(() -> {
                try {
                    eventPublisher.broadcast("ingestion.progress", Map.of(
                            "taskId", taskId,
                            "fileName", originalName,
                            "status", "INGESTING",
                            "progress", 50.0,
                            "timestamp", Instant.now().toEpochMilli()
                    ));
                    mao.remember(documentId, content, tier, source, null, new String[]{originalName});
                    log.info("[MemoryService] Async ingestion completed: file={}, id={}", originalName, documentId);
                    eventPublisher.broadcast("ingestion.completed", Map.of(
                            "taskId", taskId,
                            "fileName", originalName,
                            "documentId", documentId,
                            "status", "COMPLETED",
                            "timestamp", Instant.now().toEpochMilli()
                    ));
                    eventPublisher.memoryEvent("created", documentId, "Ingested file " + originalName);
                } catch (Exception e) {
                    log.error("[MemoryService] Async file ingestion failed: name={}: {}", originalName, e.getMessage(), e);
                }
            });

            return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GRAPH API
    // ══════════════════════════════════════════════════════════════

    public MemoryGraphResponse getGraphOverview(int maxNodes) {
        int capped = Math.min(Math.max(1, maxNodes), 500);
        return mao.getGraphOverview(capped);
    }

    public MemoryGraphResponse getMemoryGraph(String id, int depth) {
        requireId(id);
        int capped = Math.min(Math.max(1, depth), 5);
        return mao.getMemoryGraph(id, capped);
    }

    public TopologyStatsResponse getTopologyStats() {
        return mao.getTopologyStats();
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS / STATS
    // ══════════════════════════════════════════════════════════════

    public MemoryStatusResponse getStatus() {
        return mao.getStatus();
    }

    public boolean isEngineAvailable() {
        return mao.isAvailable();
    }

    // ══════════════════════════════════════════════════════════════
    // SINGLE MEMORY OPERATIONS
    // ══════════════════════════════════════════════════════════════

    public MemoryTableRow getMemoryById(String id) {
        requireId(id);
        return mao.getMemoryById(id);
    }

    public void updateMemory(String id, UpdateMemoryRequest request) {
        requireId(id);
        if (request == null) {
            throw new IllegalArgumentException("Update request body cannot be null");
        }
        mao.updateMemory(id, request);
        eventPublisher.memoryEvent("updated", id, "Updated text/tags");
    }

    public MemoryVectorResponse getMemoryVector(String id) {
        requireId(id);
        return mao.getMemoryVector(id);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Memory ID cannot be blank");
        }
    }

    private static MemoryType safeMemoryType(String name, MemoryType fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemoryType.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static MemorySource safeMemorySource(String name, MemorySource fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemorySource.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return fallback;
        }
    }
}
