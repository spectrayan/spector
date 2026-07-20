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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.UpdateMemoryRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryVectorResponse;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.model.GraphNeighborhood;
import com.spectrayan.spector.memory.model.TopologyStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory Access Object (DAO) for the Spector cognitive memory engine.
 *
 * <p>Responsible strictly for data-access operations using {@link SpectorMemory},
 * including reading/writing memories, indices, and graphs. Contains no business logic
 * like chunking, async task orchestration, or SSE event publishing.</p>
 */
@Repository
public class MemoryAccessObject {

    private static final Logger log = LoggerFactory.getLogger(MemoryAccessObject.class);

    private final SpectorMemory memory;
    private final TsidGenerator tsid;

    @Autowired
    public MemoryAccessObject(ObjectProvider<SpectorMemory> memoryProvider, TsidGenerator tsid) {
        this.memory = memoryProvider.getIfAvailable();
        this.tsid = tsid;
        if (this.memory != null) {
            log.info("[MemoryAccessObject] SpectorMemory available — DAO ready");
        } else {
            log.warn("[MemoryAccessObject] SpectorMemory bean not available — running in stub mode.");
        }
    }

    /** Constructor for unit testing. */
    public MemoryAccessObject(ObjectProvider<SpectorMemory> memoryProvider) {
        this(memoryProvider, new TsidGenerator());
    }

    public boolean isAvailable() {
        return memory != null;
    }

    public TsidGenerator tsid() {
        return tsid;
    }

    public SpectorMemory getMemory() {
        return memory;
    }

    /**
     * Store/remember a memory synchronously.
     */
    public String remember(String id, String text, MemoryType type, MemorySource source, IngestionHints hints, String[] tags) {
        if (!isAvailable()) {
            log.warn("[MemoryAccessObject] Stub mode: remember ignored (id={})", id);
            return id;
        }
        try {
            memory.remember(id, text, type, source, (IngestionHints) hints, tags).join();
            log.debug("[MemoryAccessObject] Remembered memory: id={}", id);
            return id;
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Remember failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Memory store failed: " + e.getMessage(), e);
        }
    }

    /**
     * Forget/tombstone a memory by ID.
     */
    public void forget(String id) {
        if (!isAvailable()) return;
        try {
            memory.forget(id);
            log.info("[MemoryAccessObject] Forgot memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Forget failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Triggers manual memory consolidation.
     */
    public void consolidate() {
        if (!isAvailable()) return;
        try {
            memory.consolidate();
            log.info("[MemoryAccessObject] Triggered memory consolidation");
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Consolidation failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Memory consolidation failed: " + e.getMessage(), e);
        }
    }


    /**
     * Reinforce a memory via emotional valence feedback.
     */
    public void reinforce(String id, int valence) {
        if (!isAvailable()) return;
        try {
            memory.reinforce(id, (byte) Math.clamp(valence, -128, 127));
            log.info("[MemoryAccessObject] Reinforced memory id={} valence={}", id, valence);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Reinforce failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Suppress a memory with an optional reason.
     */
    public void suppress(String id, String reason) {
        if (!isAvailable()) return;
        try {
            if (reason != null && !reason.isBlank()) {
                memory.suppress(id, reason);
            } else {
                memory.suppress(id);
            }
            log.info("[MemoryAccessObject] Suppressed memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Suppress failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Unsuppress a previously suppressed memory.
     */
    public void unsuppress(String id) {
        if (!isAvailable()) return;
        try {
            memory.unsuppress(id);
            log.info("[MemoryAccessObject] Unsuppressed memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Unsuppress failed: {}", e.getMessage(), e);
        }
    }

    /** Mark a memory as resolved. */
    public void markResolved(String id) {
        if (!isAvailable()) return;
        try {
            memory.markResolved(id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Resolve failed: {}", e.getMessage(), e);
        }
    }

    /** Mark a memory as unresolved. */
    public void markUnresolved(String id) {
        if (!isAvailable()) return;
        try {
            memory.markUnresolved(id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Unresolve failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Call cognitive recall synchronously.
     */
    public List<CognitiveResult> recall(String query) {
        if (!isAvailable()) return List.of();
        try {
            return memory.recall(query);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Recall failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Triggers a sleep consolidation (reflect) cycle.
     */
    public ReflectReport reflect() {
        if (!isAvailable()) return null;
        try {
            return memory.reflect();
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Reflect failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Reflect consolidation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Triggers vacuum compaction for a specific tier.
     */
    public CompactionResult vacuum(String tierName) {
        if (!isAvailable()) return new CompactionResult(tierName, 0, 0, 0, 0L, 0L);
        try {
            MemoryType tier = MemoryTypeParser.safeMemoryType(tierName, MemoryType.SEMANTIC);
            long start = System.currentTimeMillis();
            var result = memory.admin().vacuum(tier);
            long durationMs = System.currentTimeMillis() - start;
            if (result == null) return new CompactionResult(tierName, 0, 0, 0, 0L, durationMs);
            return new CompactionResult(tierName, result.beforeCount(), result.afterCount(),
                    result.tombstonesRemoved(), result.bytesReclaimed(), durationMs);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Vacuum failed for tier={}: {}", tierName, e.getMessage(), e);
            throw new IllegalStateException("Vacuum failed: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve details for a single memory by ID.
     */
    public MemoryTableRow getMemoryById(String id) {
        if (!isAvailable()) return null;
        try {
            var record = memory.inspect(id);
            if (record == null) return null;
            return toTableRow(record, false);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Inspect failed for id={}: {}", id, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieve the INT8 quantized embedding vector for a memory.
     */
    public MemoryVectorResponse getMemoryVector(String id) {
        if (!isAvailable()) return new MemoryVectorResponse(id, 0, List.of());
        try {
            var record = memory.inspect(id);
            if (record == null || record.quantizedVector() == null) {
                return new MemoryVectorResponse(id, 0, List.of());
            }
            byte[] rawVector = record.quantizedVector();
            List<Float> floats = new ArrayList<>(rawVector.length);
            for (byte b : rawVector) {
                floats.add((float) b);
            }
            return new MemoryVectorResponse(id, rawVector.length, floats);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Get memory vector failed for id={}: {}", id, e.getMessage(), e);
            return new MemoryVectorResponse(id, 0, List.of());
        }
    }

    /**
     * Update an existing memory's text and tags.
     */
    public void updateMemory(String id, UpdateMemoryRequest request) {
        if (!isAvailable()) return;
        try {
            var record = memory.inspect(id);
            if (record == null) {
                throw new IllegalArgumentException("Memory ID not found: " + id);
            }
            String[] tags = request.tags() != null ? request.tags().toArray(String[]::new) : record.tags();

            // Re-store memory with the same ID, overwriting previous content
            memory.remember(id, request.text(), record.memoryType(), record.source(), (IngestionHints) null, tags).join();
            log.info("[MemoryAccessObject] Updated memory id={}", id);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Update memory failed for id={}: {}", id, e.getMessage(), e);
            throw new IllegalStateException("Update memory failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a paginated memory table view for the Cortex UI.
     */
    public MemoryTableResponse getMemoryTable(int page, int pageSize, String tierFilter, boolean showTombstoned) {
        if (!isAvailable()) {
            var emptyCounts = Map.of("WORKING", 0, "EPISODIC", 0, "SEMANTIC", 0, "PROCEDURAL", 0);
            var emptyRatios = Map.of("WORKING", 0f, "EPISODIC", 0f, "SEMANTIC", 0f, "PROCEDURAL", 0f);
            return new MemoryTableResponse(List.of(), 0, page, pageSize, emptyCounts, emptyRatios);
        }

        var admin = memory.admin();
        MemoryType targetTier = MemoryTypeParser.safeMemoryType(tierFilter, null);

        // Fetch all active cognitive records without loading large vector arrays into JVM memory
        List<CognitiveRecord> allRecords = admin.listAll();

        List<MemoryTableRow> allRows = new ArrayList<>();
        Map<String, Integer> tierCounts = new LinkedHashMap<>();

        // Initialize tier counts
        for (MemoryType type : MemoryType.values()) {
            tierCounts.put(type.name(), 0);
        }

        for (CognitiveRecord record : allRecords) {
            // Track per-tier active counts
            String tierName = record.memoryType().name();
            tierCounts.put(tierName, tierCounts.getOrDefault(tierName, 0) + 1);

            // Filter by target tier if specified
            if (targetTier != null && record.memoryType() != targetTier) {
                continue;
            }

            // Filter by tombstone if specified
            if (!showTombstoned && record.isTombstoned()) {
                continue;
            }

            allRows.add(toTableRow(record, true));
        }

        // Sort by creation time (newest first)
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
        var stats = memory.admin().graph().graphStats();
        int total = memory.totalMemories();
        var counts = Map.of(
                "WORKING", memory.memoryCount(MemoryType.WORKING),
                "EPISODIC", memory.memoryCount(MemoryType.EPISODIC),
                "SEMANTIC", memory.memoryCount(MemoryType.SEMANTIC),
                "PROCEDURAL", memory.memoryCount(MemoryType.PROCEDURAL)
        );
        return new MemoryStatusResponse(total, counts,
                stats.hebbianEdges(), stats.temporalLinks(),
                stats.entityNodes(), stats.entityEdges());
    }

    /**
     * Returns a sampled overview of the memory graph (nodes + edges).
     */
    public MemoryGraphResponse getGraphOverview(int maxNodes) {
        if (!isAvailable()) return MemoryGraphResponse.empty(null);
        var neighborhood = memory.admin().graph().overview(maxNodes, memory::inspect);
        return toGraphResponse(neighborhood);
    }

    /**
     * Returns the Hebbian/Temporal/Entity neighborhood for a specific memory.
     */
    public MemoryGraphResponse getMemoryGraph(String id, int depth) {
        if (!isAvailable()) return MemoryGraphResponse.empty(null);
        var neighborhood = memory.admin().graph().neighborhood(id, depth, memory::inspect);
        return toGraphResponse(neighborhood);
    }

    /**
     * Returns topology statistics.
     */
    public MemoryDto.TopologyStatsResponse getTopologyStats() {
        if (!isAvailable()) return MemoryDto.TopologyStatsResponse.empty();
        var stats = memory.admin().graph().topologyStats();
        return toTopologyResponse(stats);
    }

    // ════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * Converts a {@link CognitiveRecord} to a {@link MemoryTableRow} DTO.
     *
     * <p>Centralizes the 20-field row construction so that both
     * {@link #getMemoryById(String)} and {@link #getMemoryTable(int, int, String, boolean)}
     * share a single conversion path.</p>
     *
     * @param record           the cognitive record from inspect/listAll
     * @param truncatePreview  if true, truncates text preview to 200 characters
     * @return the table row DTO
     */
    private MemoryTableRow toTableRow(CognitiveRecord record, boolean truncatePreview) {
        String text = record.text() != null ? record.text() : "";
        String preview = truncatePreview ? truncate(text, 200) : text;
        Map<String, String> meta = record.metadata();
        if (meta != null && meta.isEmpty()) meta = null;

        return new MemoryTableRow(
                record.id(), text, preview,
                record.memoryType().name(),
                record.source().name(),
                record.importance(),
                record.valence(),
                Byte.toUnsignedInt(record.arousal()),
                record.timestampMs(),
                record.agentRecallCount(),
                record.totalRecallCount(),
                record.isTombstoned(),
                record.suppressed(),
                record.isPinned(),
                record.isResolved(),
                record.isConsolidated(),
                record.tags() != null ? Arrays.asList(record.tags()) : List.of(),
                record.synapticTags(),
                record.createdAt().toString(),
                meta
        );
    }

    /**
     * Converts a {@link GraphNeighborhood} from the core module to the synapse DTO.
     */
    private static MemoryGraphResponse toGraphResponse(GraphNeighborhood neighborhood) {
        if (neighborhood.error() != null && neighborhood.nodes().isEmpty()) {
            return MemoryGraphResponse.empty(neighborhood.centerId());
        }
        var nodes = neighborhood.nodes().stream()
                .map(n -> new MemoryDto.GraphNodeDto(
                        n.id(), n.tier(), n.textPreview(),
                        n.importance(), n.valence(), n.timestampMs(), n.entityNames()))
                .toList();
        var edges = neighborhood.edges().stream()
                .map(e -> new MemoryDto.GraphEdgeDto(
                        e.sourceId(), e.targetId(), e.type(), e.relationType(),
                        e.weight(), e.sourceEntityType(), e.targetEntityType()))
                .toList();
        return new MemoryGraphResponse(neighborhood.centerId(), nodes, edges);
    }

    /**
     * Converts {@link TopologyStats} from the core module to the synapse DTO.
     */
    private static MemoryDto.TopologyStatsResponse toTopologyResponse(TopologyStats stats) {
        var entityTypes = stats.entityTypes().stream()
                .map(e -> new MemoryDto.EntityTypeStatsDto(
                        e.type(), e.nodeCount(), e.edgeCount(), e.memoryRefCount()))
                .toList();
        var relationTypes = stats.relationTypes().stream()
                .map(r -> new MemoryDto.RelationTypeStatsDto(
                        r.type(), r.edgeCount(), r.nodeCount(), r.memoryRefCount()))
                .toList();
        return new MemoryDto.TopologyStatsResponse(entityTypes, relationTypes);
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "\u2026";
    }

    // ══════════════════════════════════════════════════════════════
    // RESCORE — recompute importance under current salience profile
    // ══════════════════════════════════════════════════════════════

    /**
     * Rescores all existing memories with the current salience profile.
     *
     * <p>Iterates every memory in the index, computes the topic boost and
     * self-relevance boost under the active salience profile, and updates
     * the importance score if it changed significantly (>1% delta).</p>
     *
     * <p>This operation uses the admin API to access the tier router and
     * memory index. It runs synchronously — callers should invoke from
     * a controller thread or wrap in a virtual thread.</p>
     *
     * @return the number of memories whose importance was adjusted
     */
    public int rescoreWithCurrentProfile() {
        if (memory == null) return 0;

        var admin = memory.admin();
        var index = admin.index();
        var tierRouter = admin.tierRouter();
        var profile = memory.salienceProfile();

        int rescored = 0;
        int errors = 0;

        for (var entry : index.locationMap().entrySet()) {
            try {
                String memoryId = entry.getKey();
                var record = memory.inspect(memoryId);
                if (record == null || record.isTombstoned()) continue;

                // Compute combined boost from topic interests + self-relevance
                float topicBoost = 1.0f;
                float selfBoost = 1.0f;

                if (record.quantizedVector() != null && record.quantizedVector().length > 0) {
                    // Dequantize INT8 → float for semantic matching
                    byte[] qvec = record.quantizedVector();
                    float[] fvec = new float[qvec.length];
                    for (int d = 0; d < qvec.length; d++) {
                        fvec[d] = ((qvec[d] & 0xFF) - 128) / 127.5f;
                    }
                    topicBoost = profile.computeTopicBoost(fvec);
                    selfBoost = profile.computeSelfRelevanceBoost(fvec);
                }

                float combinedBoost = topicBoost * selfBoost;
                if (Math.abs(combinedBoost - 1.0f) > 0.01f) {
                    var loc = entry.getValue();
                    var segment = tierRouter.segmentFor(loc.type());
                    var layout = tierRouter.layoutFor(loc.type());

                    if (segment != null && layout != null) {
                        float oldImportance = layout.readImportance(segment, loc.offset());
                        float newImportance = Math.clamp(oldImportance * combinedBoost, 0.05f, 10.0f);
                        layout.writeImportance(segment, loc.offset(), newImportance);
                        rescored++;
                    }
                }
            } catch (Exception e) {
                errors++;
                log.debug("[MemoryAccessObject] Rescore error for {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("[MemoryAccessObject] Rescore completed: {} memories rescored, {} errors", rescored, errors);
        return rescored;
    }
}
