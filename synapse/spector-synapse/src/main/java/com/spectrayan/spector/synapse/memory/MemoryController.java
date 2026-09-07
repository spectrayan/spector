/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.synapse.memory.MemoryDto.AcceptedResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RecallResult;
import com.spectrayan.spector.memory.pathway.reflect.ReflectFilter;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.synapse.memory.MemoryDto.BrowseRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.BrowseResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReflectResponse;
import java.time.Instant;
import com.spectrayan.spector.synapse.memory.MemoryDto.ReinforceByIdRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.RememberRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.ResolveRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.SearchResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.StoreResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.SuppressRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.ScoringStats;
import com.spectrayan.spector.synapse.memory.MemoryDto.EnrichmentStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.EnrichmentTriggerResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.TopologyStatsResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.UpdateMemoryRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryVectorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Memory", description = "Cognitive Memory Operations (Remember, Recall, Search, Forget, Reinforce, Inspect)")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;
    private final FederatedRecallService federatedRecallService;

    @org.springframework.beans.factory.annotation.Autowired
    public MemoryController(
            MemoryService memoryService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) FederatedRecallService federatedRecallService) {
        this.memoryService = memoryService;
        this.federatedRecallService = federatedRecallService;
    }

    public MemoryController(MemoryService memoryService) {
        this(memoryService, null);
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
    @Operation(operationId = "getMemoryTable", summary = "Paginated memory table view for UI and exploration")
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
    @Operation(operationId = "storeMemory", summary = "Store a cognitive memory synchronously")
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
    @Operation(operationId = "rememberMemory", summary = "Remember a memory asynchronously with cognitive scoring hints")
    public ResponseEntity<AcceptedResponse> remember(@RequestBody RememberRequest request) {
        return ResponseEntity.accepted().body(memoryService.remember(request));
    }

    /**
     * Trigger manual memory consolidation.
     *
     * <p>{@code POST /api/v1/memory/consolidate}</p>
     */
    @PostMapping("/consolidate")
    @Operation(operationId = "consolidateMemories", summary = "Trigger manual memory consolidation")
    public ResponseEntity<Void> consolidate() {
        memoryService.consolidate();
        return ResponseEntity.ok().build();
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
    @Operation(operationId = "searchMemories", summary = "Semantic similarity search")
    public ResponseEntity<List<SearchResult>> search(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(memoryService.search(request));
    }

    /**
     * Cognitive recall (vector + Hebbian + temporal fused pipeline).
     *
     * <p>{@code POST /api/v1/memory/recall}</p>
     */
    @PostMapping("/recall")
    @Operation(operationId = "recallMemories", summary = "Cognitive recall with biological scoring")
    public ResponseEntity<List<RecallResult>> recall(@RequestBody RecallRequest request) {
        return ResponseEntity.ok(memoryService.recall(request));
    }

    /**
     * Cross-rememberer federated recall (ADR-0029 §7).
     *
     * <p>{@code POST /api/v1/memory/federated-recall}</p>
     */
    @PostMapping("/federated-recall")
    @Operation(operationId = "federatedRecallMemories", summary = "Cross-rememberer federated recall")
    public ResponseEntity<FederatedRecallResponse> federatedRecall(@RequestBody FederatedRecallRequest request) {
        if (federatedRecallService == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED).build();
        }
        String accountId = com.spectrayan.spector.synapse.security.SecurityUtils.getUserId();
        return ResponseEntity.ok(federatedRecallService.federatedRecall(accountId, request));
    }

    /**
     * Tag-based memory browsing (no vector search).
     *
     * <p>Uses the engine's inverted tag index for O(1) exact tag matching.
     * Useful for session history replay, auditing, and bulk operations.</p>
     *
     * <p>{@code POST /api/v1/memory/browse}</p>
     */
    @PostMapping("/browse")
    @Operation(operationId = "browseMemories", summary = "Tag-based memory browsing without vector search")
    public ResponseEntity<List<BrowseResult>> browse(@RequestBody BrowseRequest request) {
        return ResponseEntity.ok(memoryService.browse(request));
    }

    // ══════════════════════════════════════════════════════════════
    // GRAPH API — registered before /{id}/* to avoid path variable capture
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns a sampled overview of the memory graph for the Graph Explorer page.
     *
     * <p>Maps to: {@code MemoryTableService.getGraphOverview(maxNodes)} in Angular.</p>
     *
     * <p>IMPORTANT: This endpoint must be declared before {@code /{id}/graph}
     * so Spring does not capture "graph" as the {@code {id}} path variable.</p>
     *
     * <p>{@code GET /api/v1/memory/graph/overview?maxNodes=100}</p>
     *
     * @param maxNodes max nodes to return (default 100, capped at 500)
     */
    @GetMapping("/graph/overview")
    @Operation(operationId = "getGraphOverview", summary = "Sampled overview of associative memory graph")
    public ResponseEntity<MemoryGraphResponse> getGraphOverview(
            @RequestParam(defaultValue = "100") int maxNodes) {
        return ResponseEntity.ok(memoryService.getGraphOverview(maxNodes));
    }

    /**
     * Returns topology statistics (entity types, relation types with node/edge counts).
     *
     * <p>Maps to: {@code MemoryTableService.getTopologyStats()} in Angular.</p>
     *
     * <p>{@code GET /api/v1/memory/topology-stats}</p>
     */
    @GetMapping("/topology-stats")
    @Operation(operationId = "getTopologyStats", summary = "Topology statistics of entities and relationships")
    public ResponseEntity<TopologyStatsResponse> getTopologyStats() {
        return ResponseEntity.ok(memoryService.getTopologyStats());
    }

    /**
     * Trigger asynchronous offline graph enrichment in the background.
     *
     * <p>{@code POST /api/v1/memory/enrich-graph?limit=50}</p>
     */
    @PostMapping("/enrich-graph")
    @Operation(operationId = "enrichGraph", summary = "Trigger asynchronous offline graph enrichment in the background")
    public ResponseEntity<EnrichmentTriggerResponse> enrichGraph(
            @RequestParam(defaultValue = "50") int limit) {
        memoryService.enrichGraph(limit);
        return ResponseEntity.accepted().body(new EnrichmentTriggerResponse(
                "ACCEPTED", "Graph enrichment triggered in background", limit));
    }

    /**
     * Trigger full re-extraction of entities and relationships for ALL memories,
     * replacing existing graph data. Use after ontology changes, prompt updates,
     * or LLM upgrades.
     *
     * <p>{@code POST /api/v1/memory/reextract-graph?limit=50}</p>
     */
    @PostMapping("/reextract-graph")
    @Operation(operationId = "reextractGraph", summary = "Trigger full re-extraction of entities and relationships")
    public ResponseEntity<EnrichmentTriggerResponse> reextractGraph(
            @RequestParam(defaultValue = "50") int limit) {
        memoryService.reextractGraph(limit);
        return ResponseEntity.accepted().body(new EnrichmentTriggerResponse(
                "ACCEPTED", "Full graph re-extraction triggered in background", limit));
    }

    /**
     * Returns real-time status and telemetry for the offline graph enrichment daemon.
     *
     * <p>{@code GET /api/v1/memory/enrich-graph/status}</p>
     */
    @GetMapping("/enrich-graph/status")
    @Operation(operationId = "getEnrichmentStatus", summary = "Real-time telemetry for offline graph enrichment daemon")
    public ResponseEntity<EnrichmentStatusResponse> getEnrichmentStatus() {
        return ResponseEntity.ok(memoryService.getEnrichmentStatus());
    }

    /**
     * Returns memory health statistics.
     *
     * <p>{@code GET /api/v1/memory/stats}</p>
     */
    @GetMapping("/stats")
    @Operation(operationId = "getMemoryStats", summary = "Memory health statistics")
    public ResponseEntity<MemoryStats> getStats() {
        return ResponseEntity.ok(memoryService.getStats());
    }

    /**
     * Returns memory scoring metrics averages.
     *
     * <p>{@code GET /api/v1/memory/stats/scoring}</p>
     */
    @GetMapping("/stats/scoring")
    @Operation(operationId = "getScoringStats", summary = "Memory scoring metrics averages")
    public ResponseEntity<ScoringStats> getScoringStats() {
        return ResponseEntity.ok(memoryService.getScoringStats());
    }

    /**
     * Returns real 3D PCA vector space embedding projection.
     *
     * <p>{@code GET /api/v1/memory/vector-space/projection}</p>
     */
    @GetMapping("/vector-space/projection")
    @Operation(operationId = "getVectorSpaceProjection", summary = "3D PCA vector space embedding projection")
    public ResponseEntity<VectorSpaceProjectionService.ProjectionResult> getVectorSpaceProjection() {
        return ResponseEntity.ok(memoryService.getVectorSpaceProjection());
    }

    /**
     * Returns real memory diagnostics snapshot (tier counts, graph statistics, memory allocations).
     *
     * <p>{@code GET /api/v1/memory/diagnostics}</p>
     */
    @GetMapping("/diagnostics")
    @Operation(operationId = "getMemoryDiagnostics", summary = "Diagnostics snapshot (tier counts, allocations)")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        return ResponseEntity.ok(memoryService.getDiagnostics());
    }

    /**
     * Returns the Ebbinghaus forgetting & LTP retention curve for the active decay configuration.
     *
     * <p>{@code GET /api/v1/memory/diagnostics/decay}</p>
     */
    @GetMapping("/diagnostics/decay")
    @Operation(operationId = "getDecayCurve", summary = "Ebbinghaus forgetting and LTP retention decay curve")
    public ResponseEntity<List<Map<String, Object>>> getDecayCurve() {
        return ResponseEntity.ok(memoryService.getDecayCurve());
    }

    /**
     * Returns the latest consolidation snapshot diff.
     *
     * <p>{@code GET /api/v1/memory/consolidation/diff}</p>
     */
    @GetMapping("/consolidation/diff")
    @Operation(operationId = "getConsolidationDiff", summary = "Latest consolidation snapshot diff")
    public ResponseEntity<List<Map<String, Object>>> getConsolidationDiff() {
        return ResponseEntity.ok(memoryService.getConsolidationDiff());
    }

    /**
     * Returns system SIMD Vector API and hardware capabilities.
     *
     * <p>{@code GET /api/v1/memory/hardware}</p>
     */
    @GetMapping("/hardware")
    @Operation(operationId = "getHardwareInfo", summary = "System SIMD Vector API and hardware capabilities")
    public ResponseEntity<Map<String, Object>> getHardware() {
        return ResponseEntity.ok(memoryService.getHardwareInfo());
    }

    /**
     * Returns recent live rolling ops/sec metrics history.
     *
     * <p>{@code GET /api/v1/memory/metrics/live}</p>
     */
    @GetMapping("/metrics/live")
    @Operation(operationId = "getLiveMetrics", summary = "Recent live rolling ops/sec metrics history")
    public ResponseEntity<List<Map<String, Object>>> getLiveMetrics() {
        return ResponseEntity.ok(memoryService.getLiveMetricsHistory());
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
    @Operation(operationId = "forgetMemory", summary = "Tombstone (forget) a memory by ID")
    public ResponseEntity<Map<String, String>> forget(@PathVariable String id) {
        memoryService.forget(id);
        return ResponseEntity.ok(Map.of("status", "forgotten", "id", id));
    }

    // ══════════════════════════════════════════════════════════════
    // SINGLE MEMORY CRUD
    // ══════════════════════════════════════════════════════════════

    /**
     * Retrieve a single memory by ID.
     *
     * <p>{@code GET /api/v1/memory/{id}}</p>
     */
    @GetMapping("/{id}")
    @Operation(operationId = "getMemoryById", summary = "Retrieve a single memory by ID")
    public ResponseEntity<MemoryTableRow> getMemoryById(@PathVariable String id) {
        MemoryTableRow row = memoryService.getMemoryById(id);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(row);
    }

    /**
     * Update an existing memory.
     *
     * <p>{@code PUT /api/v1/memory/{id}}</p>
     */
    @PutMapping("/{id}")
    @Operation(operationId = "updateMemory", summary = "Update an existing memory by ID")
    public ResponseEntity<String> updateMemory(
            @PathVariable String id,
            @RequestBody UpdateMemoryRequest request) {
        memoryService.updateMemory(id, request);
        return ResponseEntity.ok("updated");
    }

    /**
     * Retrieve the INT8 quantized embedding vector for a memory.
     *
     * <p>{@code GET /api/v1/memory/{id}/vector}</p>
     */
    @GetMapping("/{id}/vector")
    @Operation(operationId = "getMemoryVector", summary = "Retrieve INT8 quantized embedding vector for a memory")
    public ResponseEntity<MemoryVectorResponse> getMemoryVector(@PathVariable String id) {
        return ResponseEntity.ok(memoryService.getMemoryVector(id));
    }

    /**
     * Reinforce a memory by ID via path variable (Cortex UI form).
     *
     * <p>Maps to: {@code MemoryTableService.reinforceMemory(id, valence)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/{id}/reinforce}</p>
     */
    @PostMapping("/{id}/reinforce")
    @Operation(operationId = "reinforceMemory", summary = "Reinforce memory via Long-Term Potentiation (LTP)")
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
    @Operation(operationId = "suppressMemory", summary = "Suppress or unsuppress a memory from recall")
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
     * Returns the Hebbian/Temporal/Entity graph neighborhood for a specific memory.
     *
     * <p>Maps to: {@code MemoryTableService.getMemoryGraph(id, depth)} in Angular.</p>
     *
     * <p>{@code GET /api/v1/memory/{id}/graph?depth=2}</p>
     *
     * @param id    the memory ID
     * @param depth BFS traversal depth (default 2, capped at 5 in service)
     */
    @GetMapping("/{id}/graph")
    @Operation(operationId = "getMemoryGraph", summary = "Hebbian, Temporal, and Entity graph neighborhood for a memory")
    public ResponseEntity<MemoryGraphResponse> getMemoryGraph(
            @PathVariable String id,
            @RequestParam(defaultValue = "2") int depth) {
        return ResponseEntity.ok(memoryService.getMemoryGraph(id, depth));
    }

    /**
     * Mark a memory as resolved or unresolved.
     *
     * <p>Maps to: {@code MemoryTableService.resolveMemory(id, resolved)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/{id}/resolve}</p>
     */
    @PostMapping("/{id}/resolve")
    @Operation(operationId = "resolveMemory", summary = "Resolve or unresolve a memory (Zeigarnik closure)")
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
     * Trigger a sleep consolidation (reflect) cycle or filtered sweep.
     *
     * <p>Maps to: {@code MemoryTableService.reflect()} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/reflect}</p>
     *
     * @param sweepId           optional unique sweep ID (generated if omitted)
     * @param sessionLimit      optional limit on sessions processed
     * @param sessionIdAfter    optional cursor for watermark continuation
     * @param from              optional turn timestamp lower bound (epoch millis)
     * @param to                optional turn timestamp upper bound (epoch millis)
     * @param consolidationOnly optional flag to skip companion relays (default false)
     * @param request           optional request body with additional parameters
     */
    @PostMapping("/reflect")
    @Operation(operationId = "reflectMemories", summary = "Trigger sleep consolidation (reflect) sweep")
    public ResponseEntity<ReflectResponse> reflect(
            @RequestParam(required = false) String sweepId,
            @RequestParam(required = false) Integer sessionLimit,
            @RequestParam(required = false) Long sessionIdAfter,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) Boolean consolidationOnly,
            @RequestBody(required = false) ReflectRequest request) {

        String effectiveSweepId = sweepId != null ? sweepId : (request != null ? request.sweepId() : null);
        Integer effectiveSessionLimit = sessionLimit != null ? sessionLimit : (request != null ? request.sessionLimit() : null);
        Long effectiveSessionIdAfter = sessionIdAfter != null ? sessionIdAfter : (request != null ? request.sessionIdAfter() : null);
        Long effectiveFrom = from != null ? from : (request != null ? request.from() : null);
        Long effectiveTo = to != null ? to : (request != null ? request.to() : null);
        Boolean effectiveConsolidationOnly = consolidationOnly != null ? consolidationOnly : (request != null ? request.consolidationOnly() : null);

        if (effectiveSweepId == null && effectiveSessionLimit == null && effectiveSessionIdAfter == null
                && effectiveFrom == null && effectiveTo == null && effectiveConsolidationOnly == null) {
            return ResponseEntity.ok(memoryService.reflect());
        }

        ReflectFilter.Builder filterBuilder = ReflectFilter.builder();
        if (effectiveSessionIdAfter != null) {
            filterBuilder.sessionIdAfter(effectiveSessionIdAfter);
        }
        if (effectiveFrom != null) {
            filterBuilder.from(Instant.ofEpochMilli(effectiveFrom));
        }
        if (effectiveTo != null) {
            filterBuilder.to(Instant.ofEpochMilli(effectiveTo));
        }

        ReflectSweepSpec.Builder specBuilder = ReflectSweepSpec.builder()
                .filter(filterBuilder.build())
                .sessionLimit(effectiveSessionLimit != null ? effectiveSessionLimit : 0)
                .runCompanionRelays(effectiveConsolidationOnly == null || !effectiveConsolidationOnly);

        if (effectiveSweepId != null && !effectiveSweepId.isBlank()) {
            specBuilder.sweepId(effectiveSweepId);
        }

        return ResponseEntity.ok(memoryService.reflect(specBuilder.build()));
    }

    /**
     * Poll reflection sweep progress telemetry.
     *
     * <p>{@code GET /api/v1/memory/reflect/progress/{sweepId}}</p>
     *
     * @param sweepId unique sweep identifier
     */
    @GetMapping("/reflect/progress/{sweepId}")
    @Operation(operationId = "getReflectProgress", summary = "Poll reflection sweep progress telemetry")
    public ResponseEntity<ReflectSweepProgress> getReflectProgress(@PathVariable String sweepId) {
        ReflectSweepProgress progress = memoryService.progress(sweepId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * Trigger vacuum compaction for a tier (removes tombstoned records).
     *
     * <p>Maps to: {@code MemoryTableService.vacuum(tier)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/vacuum}</p>
     */
    @PostMapping("/vacuum")
    @Operation(operationId = "vacuumMemories", summary = "Trigger vacuum compaction for a tier")
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
    @Operation(operationId = "getMemoryStatus", summary = "Cognitive memory status and tier counts")
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
    @Operation(operationId = "ingestMemoryFile", summary = "Ingest a file into memory asynchronously")
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
    @Operation(operationId = "bulkForgetMemories", summary = "Bulk forget memories by ID list")
    public ResponseEntity<Map<String, Object>> bulkForget(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        memoryService.bulkForget(ids);
        return ResponseEntity.ok(Map.of("forgotten", ids.size(), "failed", 0, "total", ids.size()));
    }

    /**
     * Bulk reinforce memories by ID list.
     *
     * <p>Maps to: {@code MemoryTableService.bulkReinforce(ids)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/bulk/reinforce}</p>
     */
    @PostMapping("/bulk/reinforce")
    @Operation(operationId = "bulkReinforceMemories", summary = "Bulk reinforce memories by ID list")
    public ResponseEntity<Map<String, Object>> bulkReinforce(
            @RequestBody List<String> ids,
            @RequestParam(defaultValue = "0") int valence) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        memoryService.bulkReinforce(ids, valence);
        return ResponseEntity.ok(Map.of("reinforced", ids.size(), "failed", 0, "total", ids.size()));
    }

    /**
     * Bulk suppress memories by ID list.
     *
     * <p>Maps to: {@code MemoryTableService.bulkSuppress(ids)} in Angular.</p>
     *
     * <p>{@code POST /api/v1/memory/bulk/suppress}</p>
     */
    @PostMapping("/bulk/suppress")
    @Operation(operationId = "bulkSuppressMemories", summary = "Bulk suppress or unsuppress memories by ID list")
    public ResponseEntity<Map<String, Object>> bulkSuppress(
            @RequestBody List<String> ids,
            @RequestParam(defaultValue = "SUPPRESS") String action) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No IDs provided"));
        }
        SuppressRequest req = new SuppressRequest(action, null);
        memoryService.bulkSuppress(ids, req);
        String result = req.isSuppressing() ? "suppressed" : "unsuppressed";
        return ResponseEntity.ok(Map.of(result, ids.size(), "failed", 0, "total", ids.size()));
    }
}
