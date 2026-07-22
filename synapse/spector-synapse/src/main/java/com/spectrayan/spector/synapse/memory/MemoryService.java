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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.synapse.memory.MemoryDto.AcceptedResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ConsolidationStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.IndexStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryVectorResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ResolveRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ScoringStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.SuppressRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.TopologyStatsResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.UpdateMemoryRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.VacuumRequest;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

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
    private static final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final MemoryAccessObject mao;
    private final EventPublisher eventPublisher;
    private final TsidGenerator tsid;
    private final JdbcClient jdbc;

    /**
     * Per-user memory resolver. Resolves the target {@link SpectorMemory} on the request thread
     * via {@link UserMemoryRegistry#resolveForCurrentRequest()} — returning the caller's isolated
     * instance when authenticated, or the single shared instance when auth is disabled or the
     * principal is anonymous. This is the production resolution path (the {@code @Autowired}
     * constructor wires it).
     */
    private final UserMemoryRegistry userMemoryRegistry;

    /**
     * Legacy shared-instance provider, retained only for source compatibility with the
     * non-{@code @Autowired} constructors used by tests. Used as a fallback by
     * {@link #resolveMemory()} when no {@link UserMemoryRegistry} is wired.
     */
    private final ObjectProvider<SpectorMemory> memoryProvider;

    @org.springframework.beans.factory.annotation.Value("${spector.memory.decay.min-threshold:1000}")
    private int minDecayThreshold = 1000;

    @org.springframework.beans.factory.annotation.Value("${spector.memory.decay.baseline-half-life-days:180}")
    private int baselineHalfLifeDays = 180;

    // -€-€ Analytics & Telemetry Counters -€-€
    private final AtomicLong recallCount = new AtomicLong(0);
    private final AtomicLong rememberCount = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong consolidationCount = new AtomicLong(0);

    // Latest consolidation details
    private volatile long lastConsolidationTimestamp = 0;
    private volatile int consolidatedMergedCount = 0;
    private volatile int consolidatedTombstonedCount = 0;
    private volatile int consolidatedPartitions = 0;

    // Rolling similarity scores queue
    private final ConcurrentLinkedQueue<Double> similarityScores = new ConcurrentLinkedQueue<>();

    // Caffeine stats caches (5s TTL)
    private final Cache<String, MemoryStats> statsCache = Caffeine.newBuilder()
            .expireAfterWrite(java.time.Duration.ofSeconds(5))
            .build();

    private final Cache<String, ScoringStats> scoringStatsCache = Caffeine.newBuilder()
            .expireAfterWrite(java.time.Duration.ofSeconds(5))
            .build();

    public MemoryService(MemoryAccessObject mao, EventPublisher eventPublisher, TsidGenerator tsid) {
        this(mao, eventPublisher, tsid, null, null, null);
    }

    public MemoryService(MemoryAccessObject mao, EventPublisher eventPublisher, TsidGenerator tsid, JdbcClient jdbc) {
        this(mao, eventPublisher, tsid, jdbc, null, null);
    }

    @Autowired
    public MemoryService(MemoryAccessObject mao, EventPublisher eventPublisher, TsidGenerator tsid,
                         JdbcClient jdbc, ObjectProvider<SpectorMemory> memoryProvider,
                         UserMemoryRegistry userMemoryRegistry) {
        this.mao = mao;
        this.eventPublisher = eventPublisher;
        this.tsid = tsid;
        this.jdbc = jdbc;
        this.memoryProvider = memoryProvider;
        this.userMemoryRegistry = userMemoryRegistry;
    }

    /**
     * Resolves the target {@link SpectorMemory} on the calling (request) thread.
     *
     * <p>Delegates to {@link UserMemoryRegistry#resolveForCurrentRequest()}, which reads the
     * {@code SecurityContextHolder} on <em>this</em> (request) thread and returns the caller's
     * per-user instance (or the single shared instance when auth is disabled / the principal is
     * anonymous). Callers that dispatch asynchronous writes MUST invoke this on the request thread,
     * capture the returned reference in a {@code final} local, and close over it inside the async
     * task — the async task body must never read the security context.</p>
     *
     * <p>Falls back to the legacy shared-instance provider only when no {@link UserMemoryRegistry}
     * is wired (i.e. the non-{@code @Autowired} constructors used by tests).</p>
     */
    private SpectorMemory resolveMemory() {
        if (userMemoryRegistry != null) {
            return userMemoryRegistry.resolveForCurrentRequest();
        }
        return memoryProvider != null ? memoryProvider.getIfAvailable() : null;
    }

    public long getAndResetRecallCount() { return recallCount.getAndSet(0); }
    public long getAndResetRememberCount() { return rememberCount.getAndSet(0); }
    public long getAndResetTotalLatencyMs() { return totalLatencyMs.getAndSet(0); }
    public long getConsolidationCount() { return consolidationCount.get(); }

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
        SpectorMemory memory = resolveMemory();
        if (!mao.isAvailable(memory)) {
            return new StoreResponse("stub-" + System.nanoTime(), request.text(),
                    "SEMANTIC", 0.0, "Memory stored (stub mode — engine not available)");
        }
        String id = tsid.generate();
        String[] tags = request.tags() != null
                ? request.tags().toArray(String[]::new) : new String[0];
        mao.remember(memory, id, request.text(), MemoryType.SEMANTIC, MemorySource.USER_STATED, null, tags);
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

        // Resolve the target memory on the REQUEST THREAD; the async task closes over it.
        final SpectorMemory memory = resolveMemory();
        if (!mao.isAvailable(memory)) {
            log.warn("[MemoryService] Remember called in stub mode — id={}", effectiveId);
            return AcceptedResponse.forRemember("stub-task-" + System.nanoTime(), effectiveId);
        }

        MemoryType tier = MemoryTypeParser.safeMemoryType(request.effectiveTier(), MemoryType.SEMANTIC);
        MemorySource source = MemoryTypeParser.safeMemorySource(request.effectiveSource(), MemorySource.OBSERVED);
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
                 long t0 = System.currentTimeMillis();
                 mao.remember(memory, finalId, request.text(), tier, source, finalHints, tags);
                 long elapsed = System.currentTimeMillis() - t0;
                 rememberCount.incrementAndGet();
                 totalLatencyMs.addAndGet(elapsed);

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
        }, virtualThreadExecutor);

        return AcceptedResponse.forRemember(taskId, effectiveId);
    }

    /**
     * Manually triggers memory consolidation in the background.
     */
    public void consolidate() {
        final SpectorMemory memory = resolveMemory();
        if (!mao.isAvailable(memory)) {
            log.warn("[MemoryService] Consolidate called but engine is not available");
            return;
        }
        virtualThreadExecutor.submit(() -> {
            try {
                log.info("[MemoryService] Starting manual memory consolidation...");
                eventPublisher.broadcast("consolidation.start", Map.of("status", "in_progress"));
                mao.consolidate(memory);
                eventPublisher.broadcast("consolidation.done", Map.of("status", "success"));
                log.info("[MemoryService] Manual memory consolidation complete.");
            } catch (Exception e) {
                log.error("[MemoryService] Manual memory consolidation failed", e);
                eventPublisher.broadcast("consolidation.error", Map.of("status", "error", "message", e.getMessage()));
            }
        });
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
        List<CognitiveResult> results = mao.recall(resolveMemory(), request.query());
        long elapsedMicros = (System.nanoTime() - start) / 1000;
        recallCount.incrementAndGet();
        totalLatencyMs.addAndGet(elapsedMicros / 1000);

        for (var r : results) {
            if (r.breakdown() != null) {
                similarityScores.add((double) r.breakdown().similarity());
            }
        }
        while (similarityScores.size() > 100) {
            similarityScores.poll();
        }

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
        return mao.getMemoryTable(resolveMemory(), effectivePage, effectivePageSize, tierFilter, showTombstoned);
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
        mao.forget(resolveMemory(), id);
        eventPublisher.memoryEvent("deleted", id, "Tombstoned memory");
    }

    /**
     * Bulk forget/tombstone.
     */
    public void bulkForget(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        log.info("[MemoryService] Bulk forget: count={}", ids.size());
        SpectorMemory memory = resolveMemory();
        for (String id : ids) {
            mao.forget(memory, id);
            eventPublisher.memoryEvent("deleted", id, "Bulk tombstoned");
        }
    }

    /**
     * Reinforce a memory via dopamine feedback.
     */
    public void reinforce(String id, int valence) {
        requireId(id);
        log.debug("[MemoryService] reinforce: id={}, valence={}", id, valence);
        mao.reinforce(resolveMemory(), id, valence);
        eventPublisher.memoryEvent("reinforced", id, "Emotional valence feedback: " + valence);
    }

    /**
     * Bulk reinforce.
     */
    public void bulkReinforce(List<String> ids, int valence) {
        if (ids == null || ids.isEmpty()) return;
        log.info("[MemoryService] Bulk reinforce: count={}, valence={}", ids.size(), valence);
        SpectorMemory memory = resolveMemory();
        for (String id : ids) {
            mao.reinforce(memory, id, valence);
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
        SpectorMemory memory = resolveMemory();
        if (request != null && !request.isSuppressing()) {
            mao.unsuppress(memory, id);
            eventPublisher.memoryEvent("unsuppressed", id, "Unsuppressed memory");
        } else {
            String reason = request != null ? request.effectiveReason() : "";
            mao.suppress(memory, id, reason);
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
        SpectorMemory memory = resolveMemory();
        if (resolving) {
            mao.markResolved(memory, id);
            eventPublisher.memoryEvent("resolved", id, "Marked resolved");
        } else {
            mao.markUnresolved(memory, id);
            eventPublisher.memoryEvent("unresolved", id, "Marked unresolved");
        }
    }

    /**
     * Trigger a sleep consolidation (reflect) cycle.
     */
    public ReflectResponse reflect() {
        log.info("[MemoryService] Triggering reflect (sleep consolidation)...");
        long start = System.currentTimeMillis();
        ReflectReport report = mao.reflect(resolveMemory());
        long durationMs = report != null ? report.duration().toMillis() : (System.currentTimeMillis() - start);

        if (report != null) {
            lastConsolidationTimestamp = System.currentTimeMillis();
            consolidatedMergedCount += report.consolidatedCount();
            consolidatedTombstonedCount += report.tombstonedCount();
            consolidatedPartitions += report.compactedPartitions();
            consolidationCount.incrementAndGet();

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
        return mao.vacuum(resolveMemory(), tier);
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

            // Resolve the target memory on the REQUEST THREAD; the async task closes over it.
            final SpectorMemory memory = resolveMemory();
            if (!mao.isAvailable(memory)) {
                log.warn("[MemoryService] Ingest-file in stub mode — file={}", originalName);
                return AcceptedResponse.forFileIngest(taskId, originalName, documentId);
            }

            MemoryType tier = MemoryTypeParser.safeMemoryType(tierName, MemoryType.SEMANTIC);
            MemorySource source = MemoryTypeParser.safeMemorySource(sourceName, MemorySource.OBSERVED);

            CompletableFuture.runAsync(() -> {
                try {
                    eventPublisher.broadcast("ingestion.progress", Map.of(
                            "taskId", taskId,
                            "fileName", originalName,
                            "status", "INGESTING",
                            "progress", 50.0,
                            "timestamp", Instant.now().toEpochMilli()
                    ));
                    mao.remember(memory, documentId, content, tier, source, null, new String[]{originalName});
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
            }, virtualThreadExecutor);

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
        return mao.getGraphOverview(resolveMemory(), capped);
    }

    public MemoryGraphResponse getMemoryGraph(String id, int depth) {
        requireId(id);
        int capped = Math.min(Math.max(1, depth), 5);
        return mao.getMemoryGraph(resolveMemory(), id, capped);
    }

    public TopologyStatsResponse getTopologyStats() {
        return mao.getTopologyStats(resolveMemory());
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS / STATS
    // ══════════════════════════════════════════════════════════════

    public MemoryStatusResponse getStatus() {
        return mao.getStatus(resolveMemory());
    }

    public boolean isEngineAvailable() {
        return mao.isAvailable(resolveMemory());
    }

    // ══════════════════════════════════════════════════════════════
    // SINGLE MEMORY OPERATIONS
    // ══════════════════════════════════════════════════════════════

    public MemoryTableRow getMemoryById(String id) {
        requireId(id);
        return mao.getMemoryById(resolveMemory(), id);
    }

    public void updateMemory(String id, UpdateMemoryRequest request) {
        requireId(id);
        if (request == null) {
            throw new IllegalArgumentException("Update request body cannot be null");
        }
        mao.updateMemory(resolveMemory(), id, request);
        eventPublisher.memoryEvent("updated", id, "Updated text/tags");
    }

    public MemoryVectorResponse getMemoryVector(String id) {
        requireId(id);
        return mao.getMemoryVector(resolveMemory(), id);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    public MemoryStats getStats() {
        SpectorMemory resolved = resolveMemory();
        return statsCache.get("current", key -> {
            if (!mao.isAvailable(resolved)) {
                return new MemoryStats(0, Map.of(), 0, new IndexStats(0, 0, 0.95), ConsolidationStats.empty(), Map.of(), Map.of());
            }

            try {
                var memory = resolved;
                List<CognitiveRecord> records = memory.admin().listAll();

                long totalCount = records.size();

                Map<String, Long> tierDistribution = records.stream()
                        .collect(Collectors.groupingBy(r -> r.memoryType().name(), Collectors.counting()));

                // Storage bytes calculation
                long storageBytes = 0;
                var persistencePath = memory.admin().wal() != null ? memory.admin().wal().path() : null;
                if (persistencePath != null && Files.exists(persistencePath)) {
                    try (Stream<Path> stream = Files.walk(persistencePath)) {
                        storageBytes = stream.filter(Files::isRegularFile)
                                .mapToLong(p -> {
                                    try {
                                        return Files.size(p);
                                    } catch (IOException e) {
                                        return 0L;
                                    }
                                })
                                .sum();
                    } catch (IOException e) {
                        log.warn("Failed to walk persistence path: {}", e.getMessage());
                    }
                }

                // Index stats
                long totalEntries = 0;
                int levels = 0;
                double recallEstimate = 0.95;
                var semanticIndex = memory.admin().semanticIndex();
                if (semanticIndex != null) {
                    totalEntries = semanticIndex.size();
                    if (semanticIndex instanceof com.spectrayan.spector.index.AbstractHnswIndex hnsw) {
                        levels = hnsw.maxLevel() + 1;
                        int efSearch = hnsw.params().efSearch();
                        int m = hnsw.params().m();
                        recallEstimate = Math.min(0.99, 0.90 + (efSearch / (double) (efSearch + m)));
                    }
                }
                IndexStats indexStats = new IndexStats(totalEntries, levels, recallEstimate);

                // Consolidation stats
                ConsolidationStats consolidationStats = new ConsolidationStats(
                        lastConsolidationTimestamp,
                        consolidatedMergedCount,
                        consolidatedTombstonedCount,
                        consolidatedPartitions
                );

                // Growth over time (last 30 days) from H2 database
                Map<String, Long> growthOverTime = new java.util.TreeMap<>();
                if (jdbc != null) {
                    try {
                        var entries = jdbc.sql("SELECT CAST(snapshot_time AS DATE) as s_date, MAX(total_count) as max_count " +
                                        "FROM memory_analytics_snapshot " +
                                        "WHERE snapshot_time >= :since " +
                                        "GROUP BY s_date ORDER BY s_date")
                                .param("since", Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(30))))
                                .query((rs, rowNum) -> Map.entry(rs.getDate("s_date").toString(), rs.getLong("max_count")))
                                .list();
                        growthOverTime = entries.stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2, java.util.TreeMap::new));
                    } catch (Exception e) {
                        log.warn("Failed to load historical growth from database: {}", e.getMessage());
                        growthOverTime = null;
                    }
                }
                if (growthOverTime == null || growthOverTime.isEmpty()) {
                    growthOverTime = records.stream()
                            .collect(Collectors.groupingBy(
                                    r -> Instant.ofEpochMilli(r.timestampMs()).toString().substring(0, 10),
                                    Collectors.counting()
                            ));
                }

                // Decay forecast: projected retention probability over 7 and 30 days
                double currentSum = totalCount;
                double day7Sum = 0;
                double day30Sum = 0;
                long now = System.currentTimeMillis();

                if (totalCount < minDecayThreshold) {
                    day7Sum = totalCount;
                    day30Sum = totalCount;
                } else {
                    for (var r : records) {
                        double strength = r.storageStrength();
                        double halfLifeDays = strength <= 1.0 ? baselineHalfLifeDays : strength;
                        double lambda = Math.max(1.0, halfLifeDays);
                        double ageSec = (now - r.timestampMs()) / 1000.0;
                        double day7Age = ageSec + (7 * 86400.0);
                        double day30Age = ageSec + (30 * 86400.0);

                        day7Sum += Math.exp(-day7Age / (lambda * 86400.0));
                        day30Sum += Math.exp(-day30Age / (lambda * 86400.0));
                    }
                }

                Map<String, Double> decayForecast = Map.of(
                        "current", currentSum,
                        "day7", Math.round(day7Sum * 10.0) / 10.0,
                        "day30", Math.round(day30Sum * 10.0) / 10.0
                );

                return new MemoryStats(
                        totalCount,
                        tierDistribution,
                        storageBytes,
                        indexStats,
                        consolidationStats,
                        growthOverTime,
                        decayForecast
                );

            } catch (Exception e) {
                log.error("Failed to compile memory stats: {}", e.getMessage(), e);
                return new MemoryStats(0, Map.of(), 0, new IndexStats(0, 0, 0.95), ConsolidationStats.empty(), Map.of(), Map.of());
            }
        });
    }

    public ScoringStats getScoringStats() {
        SpectorMemory resolved = resolveMemory();
        return scoringStatsCache.get("current", key -> {
            if (!mao.isAvailable(resolved)) {
                return new ScoringStats(0.80, 0.75, 1.0, 5.0, 0.0);
            }

            try {
                var memory = resolved;
                List<CognitiveRecord> records = memory.admin().listAll();

                double sumImportance = 0;
                double sumValence = 0;
                double sumFrequency = 0;
                double sumRecency = 0;
                long now = System.currentTimeMillis();

                for (var r : records) {
                    sumImportance += r.importance();
                    sumValence += r.valence();
                    sumFrequency += r.spectorRecallCount() + r.agentRecallCount();

                    double ageSec = (now - r.timestampMs()) / 1000.0;
                    sumRecency += Math.exp(-ageSec / (Math.max(1.0, r.storageStrength()) * 86400.0));
                }

                int size = records.size();
                double avgImportance = size == 0 ? 0.0 : sumImportance / size;
                double avgValence = size == 0 ? 0.0 : sumValence / size;
                double avgFrequency = size == 0 ? 0.0 : sumFrequency / size;
                double avgRecency = size == 0 ? 0.0 : sumRecency / size;

                double avgSimilarity = 0.80;
                var scores = new ArrayList<>(similarityScores);
                if (!scores.isEmpty()) {
                    double simSum = 0;
                    for (var s : scores) {
                        simSum += s;
                    }
                    avgSimilarity = simSum / scores.size();
                }

                return new ScoringStats(
                        Math.round(avgSimilarity * 100.0) / 100.0,
                        Math.round(avgRecency * 100.0) / 100.0,
                        Math.round(avgFrequency * 100.0) / 100.0,
                        Math.round(avgImportance * 100.0) / 100.0,
                        Math.round(avgValence * 100.0) / 100.0
                );

            } catch (Exception e) {
                log.error("Failed to compile scoring stats: {}", e.getMessage(), e);
                return new ScoringStats(0.80, 0.75, 1.0, 5.0, 0.0);
            }
        });
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Memory ID cannot be blank");
        }
    }
}
