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

import com.spectrayan.spector.synapse.memory.MemoryDto.AcceptedResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReinforceByIdRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Spector cognitive memory API.
 *
 * <p>All endpoints are under {@code /api/v1/memory}. This controller
 * delegates entirely to {@link MemoryService} — no engine calls here.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   MemoryController  (HTTP: request/response, validation)
 *        │
 *   MemoryService     (orchestration, input validation)
 *        │
 *   MemoryBridge      (MAO: engine integration, SpectorMemory calls)
 *        │
 *   SpectorMemory     (cognitive engine)
 * </pre>
 *
 * <h3>API Contract</h3>
 * <p>This API is designed for full compatibility with the Cortex UI
 * Angular service ({@code MemoryTableService}). The endpoint paths,
 * HTTP methods, and DTO shapes are kept in sync with the node module's
 * {@code MemoryEndpoint} for API parity.</p>
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    // ══════════════════════════════════════════════════════════════
    // TABLE VIEW — primary endpoint for Cortex memory table page
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns a paginated memory table view for the Cortex UI.
     *
     * <p>Maps to: {@code MemoryTableService.getMemoryTable()} in Angular.</p>
     *
     * @param page           page number (0-based, default 0)
     * @param pageSize       rows per page (default 50, max 500)
     * @param tier           optional tier filter (WORKING/EPISODIC/SEMANTIC/PROCEDURAL)
     * @param tombstoned     whether to include tombstoned records (default false)
     */
    @GetMapping("/table")
    public ResponseEntity<MemoryTableResponse> getMemoryTable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "false") boolean tombstoned) {
        String tierFilter = (tier != null && !tier.isBlank()) ? tier : null;
        return ResponseEntity.ok(memoryService.getMemoryTable(page, pageSize, tierFilter, tombstoned));
    }

    // ══════════════════════════════════════════════════════════════
    // STORE / REMEMBER
    // ══════════════════════════════════════════════════════════════

    /**
     * Store a memory (legacy simple form — used by MCP tools).
     *
     * <p>{@code POST /api/v1/memory}</p>
     */
    @PostMapping
    public ResponseEntity<StoreResponse> store(@RequestBody StoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.store(request));
    }

    /**
     * Remember a memory via the full Cortex UI flow with cognitive hints.
     *
     * <p>Maps to: {@code MemoryTableService.rememberMemory()} in Angular.
     * Returns 202 Accepted — memory is stored asynchronously.</p>
     *
     * <p>{@code POST /api/v1/memory/remember}</p>
     */
    @PostMapping("/remember")
    public ResponseEntity<AcceptedResponse> remember(@RequestBody RememberRequest request) {
        return ResponseEntity.accepted().body(memoryService.remember(request));
    }

    // ══════════════════════════════════════════════════════════════
    // RECALL / SEARCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Semantic similarity search.
     *
     * <p>{@code POST /api/v1/memory/search}</p>
     */
    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> search(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(memoryService.search(request));
    }

    /**
     * Cognitive recall (vector + Hebbian + temporal fused pipeline).
     *
     * <p>{@code POST /api/v1/memory/recall}</p>
     */
    @PostMapping("/recall")
    public ResponseEntity<List<RecallResult>> recall(@RequestBody RecallRequest request) {
        return ResponseEntity.ok(memoryService.recall(request));
    }

    // ══════════════════════════════════════════════════════════════
    // COGNITIVE OPERATIONS — per-memory
    // ══════════════════════════════════════════════════════════════

    /**
     * Tombstone (forget) a memory by ID.
     *
     * <p>{@code DELETE /api/v1/memory/{id}}</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> forget(@PathVariable String id) {
        memoryService.forget(id);
        return ResponseEntity.ok(Map.of("status", "forgotten", "id", id));
    }

    /**
     * Reinforce a memory by ID via path variable (Cortex UI form).
     *
     * <p>Maps to: {@code MemoryTableService.reinforceMemory(id, valence)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/{id}/reinforce}</p>
     */
    @PostMapping("/{id}/reinforce")
    public ResponseEntity<Map<String, Object>> reinforce(
            @PathVariable String id,
            @RequestBody(required = false) ReinforceByIdRequest request) {
        int valence = request != null ? request.effectiveValence() : 0;
        memoryService.reinforce(id, valence);
        return ResponseEntity.ok(Map.of("status", "reinforced", "id", id, "valence", valence));
    }

    /**
     * Suppress or unsuppress a memory.
     *
     * <p>Maps to: {@code MemoryTableService.suppressMemory(id, action, reason)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/{id}/suppress}</p>
     */
    @PostMapping("/{id}/suppress")
    public ResponseEntity<Map<String, String>> suppress(
            @PathVariable String id,
            @RequestBody(required = false) SuppressRequest request) {
        memoryService.suppress(id, request);
        boolean isSuppressing = request == null || request.isSuppressing();
        return ResponseEntity.ok(Map.of(
                "status", isSuppressing ? "suppressed" : "unsuppressed",
                "id", id));
    }

    /**
     * Mark a memory as resolved or unresolved.
     *
     * <p>Maps to: {@code MemoryTableService.resolveMemory(id, resolved)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/{id}/resolve}</p>
     */
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @PathVariable String id,
            @RequestBody(required = false) ResolveRequest request) {
        memoryService.resolve(id, request);
        boolean resolving = request == null || request.isResolving();
        return ResponseEntity.ok(Map.of("status", resolving ? "resolved" : "unresolved", "id", id));
    }

    // ══════════════════════════════════════════════════════════════
    // SYSTEM OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Trigger a sleep consolidation (reflect) cycle.
     *
     * <p>Maps to: {@code MemoryTableService.reflect()} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/reflect}</p>
     */
    @PostMapping("/reflect")
    public ResponseEntity<ReflectResponse> reflect() {
        return ResponseEntity.ok(memoryService.reflect());
    }

    /**
     * Trigger vacuum compaction for a tier (removes tombstoned records).
     *
     * <p>Maps to: {@code MemoryTableService.vacuum(tier)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/vacuum}</p>
     */
    @PostMapping("/vacuum")
    public ResponseEntity<CompactionResult> vacuum(
            @RequestBody(required = false) VacuumRequest request) {
        return ResponseEntity.ok(memoryService.vacuum(request));
    }

    /**
     * Cognitive memory status — tier counts, graph stats.
     *
     * <p>Maps to: {@code MemoryTableService.getStatus()} in Angular.</p>
     *
     * <p>{@code GET /api/v1/memory/status}</p>
     */
    @GetMapping("/status")
    public ResponseEntity<MemoryStatusResponse> status() {
        return ResponseEntity.ok(memoryService.getStatus());
    }

    // ══════════════════════════════════════════════════════════════
    // FILE INGESTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Ingest a file into memory asynchronously (multipart/form-data).
     *
     * <p>Maps to: {@code MemoryTableService.ingestFile(file, tier, source)} in Angular.
     * Returns 202 Accepted — ingestion happens in the background.</p>
     *
     * <p>{@code POST /api/v1/memory/ingest-file}</p>
     *
     * @param file   the uploaded file
     * @param tier   target memory tier (default: SEMANTIC)
     * @param source provenance source (default: OBSERVED)
     */
    @PostMapping(value = "/ingest-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AcceptedResponse> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "SEMANTIC") String tier,
            @RequestParam(defaultValue = "OBSERVED") String source) {
        return ResponseEntity.accepted().body(memoryService.ingestFile(file, tier, source));
    }

    // ══════════════════════════════════════════════════════════════
    // BULK OPERATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Bulk forget memories by ID list.
     *
     * <p>Maps to: {@code MemoryTableService.bulkForget(ids)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/bulk/forget}</p>
     */
    @PostMapping("/bulk/forget")
    public ResponseEntity<Map<String, Object>> bulkForget(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        int count = 0;
        int failed = 0;
        for (String id : ids) {
            try {
                memoryService.forget(id);
                count++;
            } catch (Exception e) {
                log.warn("[MemoryController] Bulk forget failed for id={}: {}", id, e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("forgotten", count, "failed", failed, "total", ids.size()));
    }

    /**
     * Bulk reinforce memories by ID list.
     *
     * <p>Maps to: {@code MemoryTableService.bulkReinforce(ids)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/bulk/reinforce}</p>
     */
    @PostMapping("/bulk/reinforce")
    public ResponseEntity<Map<String, Object>> bulkReinforce(
            @RequestBody List<String> ids,
            @RequestParam(defaultValue = "0") int valence) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        int count = 0;
        int failed = 0;
        for (String id : ids) {
            try {
                memoryService.reinforce(id, valence);
                count++;
            } catch (Exception e) {
                log.warn("[MemoryController] Bulk reinforce failed for id={}: {}", id, e.getMessage());
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("reinforced", count, "failed", failed, "total", ids.size()));
    }

    /**
     * Bulk suppress memories by ID list.
     *
     * <p>Maps to: {@code MemoryTableService.bulkSuppress(ids)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/bulk/suppress}</p>
     */
    @PostMapping("/bulk/suppress")
    public ResponseEntity<Map<String, Object>> bulkSuppress(
            @RequestBody List<String> ids,
            @RequestParam(defaultValue = "SUPPRESS") String action) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        SuppressRequest req = new SuppressRequest(action, null);
        int count = 0;
        int failed = 0;
        for (String id : ids) {
            try {
                memoryService.suppress(id, req);
                count++;
            } catch (Exception e) {
                log.warn("[MemoryController] Bulk suppress failed for id={}: {}", id, e.getMessage());
                failed++;
            }
        }
        String result = req.isSuppressing() ? "suppressed" : "unsuppressed";
        return ResponseEntity.ok(Map.of(result, count, "failed", failed, "total", ids.size()));
    }
}
