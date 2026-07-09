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

import com.spectrayan.spector.synapse.bridge.MemoryBridge;
import com.spectrayan.spector.synapse.memory.MemoryDto.AcceptedResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ResolveRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.SuppressRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.VacuumRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Memory service — orchestration layer between the REST controller and the
 * {@link MemoryBridge} Memory Access Object.
 *
 * <p><strong>Architecture</strong>: Controller calls Service, Service calls
 * MAO ({@link MemoryBridge}). No engine calls from controllers; no HTTP
 * concerns in the MAO.</p>
 *
 * <p>This class is responsible for:</p>
 * <ul>
 *   <li>Input validation and normalization</li>
 *   <li>Orchestrating multi-step operations (e.g., recall + enrich)</li>
 *   <li>Converting MAO types to DTO types when needed</li>
 *   <li>Logging business-level events</li>
 * </ul>
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryBridge memoryBridge;

    public MemoryService(MemoryBridge memoryBridge) {
        this.memoryBridge = memoryBridge;
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
        return memoryBridge.store(request);
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
        return memoryBridge.remember(request);
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
        return memoryBridge.recall(request);
    }

    /**
     * Semantic similarity search (lighter than full cognitive recall).
     */
    public List<SearchResult> search(SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }
        log.debug("[MemoryService] search: query='{}', topK={}", request.query(), request.topK());
        // Search uses the same recall pipeline — results are top-K by cosine similarity
        List<RecallResult> results = memoryBridge.recall(
                new RecallRequest(request.query(), request.topK(), 1));
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
     *
     * @param page           page number (0-based)
     * @param pageSize       rows per page
     * @param tierFilter     optional tier name filter
     * @param showTombstoned whether to include tombstoned records
     */
    public MemoryTableResponse getMemoryTable(int page, int pageSize, String tierFilter,
                                              boolean showTombstoned) {
        int effectivePage = Math.max(0, page);
        int effectivePageSize = (pageSize > 0 && pageSize <= 500) ? pageSize : 50;
        log.debug("[MemoryService] getMemoryTable: page={}, pageSize={}, tier={}, tombstoned={}",
                effectivePage, effectivePageSize, tierFilter, showTombstoned);
        return memoryBridge.getMemoryTable(effectivePage, effectivePageSize, tierFilter, showTombstoned);
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
        memoryBridge.forget(id);
    }

    /**
     * Reinforce a memory via dopamine feedback.
     *
     * @param id      memory ID
     * @param valence emotional valence (-128 to 127)
     */
    public void reinforce(String id, int valence) {
        requireId(id);
        log.debug("[MemoryService] reinforce: id={}, valence={}", id, valence);
        memoryBridge.reinforce(id, valence);
    }

    /**
     * Suppress or unsuppress a memory.
     */
    public void suppress(String id, SuppressRequest request) {
        requireId(id);
        log.debug("[MemoryService] suppress: id={}, action={}", id,
                request != null ? request.action() : "SUPPRESS");
        if (request != null && !request.isSuppressing()) {
            memoryBridge.unsuppress(id);
        } else {
            String reason = request != null ? request.effectiveReason() : "";
            memoryBridge.suppress(id, reason);
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
            memoryBridge.markResolved(id);
        } else {
            memoryBridge.markUnresolved(id);
        }
    }

    /**
     * Trigger a sleep consolidation (reflect) cycle.
     */
    public ReflectResponse reflect() {
        log.info("[MemoryService] Triggering reflect (sleep consolidation)...");
        return memoryBridge.reflect();
    }

    /**
     * Trigger vacuum compaction for a tier.
     */
    public CompactionResult vacuum(VacuumRequest request) {
        String tier = request != null ? request.effectiveTier() : "SEMANTIC";
        log.info("[MemoryService] Triggering vacuum for tier={}", tier);
        return memoryBridge.vacuum(tier);
    }

    /**
     * Ingest a file upload into memory asynchronously.
     */
    public AcceptedResponse ingestFile(MultipartFile file, String tier, String source) {
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
                    originalName, file.getSize(), tier);
            return memoryBridge.ingestFile(content, originalName, tier, source);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS / STATS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns cognitive memory status (tier counts, graph stats).
     */
    public MemoryStatusResponse getStatus() {
        return memoryBridge.getStatus();
    }

    /** Check if the underlying memory engine is available. */
    public boolean isEngineAvailable() { return memoryBridge.isAvailable(); }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Memory ID cannot be blank");
        }
    }
}
