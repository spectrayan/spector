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
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.synapse.memory.MemoryDto.CompactionResult;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryGraphResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryStatusResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableResponse;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryTableRow;
import com.spectrayan.spector.synapse.memory.MemoryDto.UpdateMemoryRequest;
import com.spectrayan.spector.synapse.memory.MemoryDto.MemoryVectorResponse;
import com.spectrayan.spector.memory.id.TsidGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    public SpectorMemory engine() {
        return memory;
    }

    public TsidGenerator tsid() {
        return tsid;
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
            MemoryType tier = safeMemoryType(tierName, MemoryType.SEMANTIC);
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
            var admin = memory.admin();
            var index = admin.index();
            var store = admin.tierRouter().get(record.memoryType());
            var layout = store.layout();
            var loc = index.locate(id);
            if (loc == null) return null;
            long offset = loc.offset();

            // Since readHeader is protected, we can read layout stats from memory
            byte flags = 0; // Default flags
            float importance = record.importance();
            int valence = record.valence();
            int arousal = 0;

            // Optional: try to resolve header details via raw segment if available
            try {
                if (store instanceof AbstractTierStore ats) {
                    long baseOffset = ats.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0;
                    var header = layout.readHeader(ats.segment(), baseOffset + offset);
                    flags = header.flags();
                    arousal = Byte.toUnsignedInt(header.arousal());
                }
            } catch (Exception ignored) {}

            boolean tombstoned = SynapticHeaderConstants.isTombstoned(flags);
            boolean pinned = SynapticHeaderConstants.isPinned(flags);
            boolean resolved = SynapticHeaderConstants.isResolved(flags);
            boolean consolidated = SynapticHeaderConstants.isConsolidated(flags);

            String[] tags = index.tags(id);
            Map<String, String> metadata = index.metadata(id);
            if (metadata != null && metadata.isEmpty()) metadata = null;

            return new MemoryTableRow(
                    id,
                    record.text(),
                    record.text(),
                    record.memoryType().name(),
                    index.source(id).name(),
                    importance, valence, arousal,
                    record.timestampMs(), 1, 1,
                    tombstoned, false, pinned, resolved, consolidated,
                    tags != null ? Arrays.asList(tags) : List.of(),
                    0L,
                    Instant.ofEpochMilli(record.timestampMs()).toString(),
                    metadata
            );
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
            var admin = memory.admin();
            var index = admin.index();
            String tierStr = index.metadata(id).get("tier");
            MemoryType tier = safeMemoryType(tierStr, MemoryType.SEMANTIC);
            var store = admin.tierRouter().get(tier);

            if (store instanceof AbstractTierStore ats) {
                var loc = index.locate(id);
                if (loc == null) return new MemoryVectorResponse(id, 0, List.of());
                long offset = loc.offset();
                long baseOffset = ats.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0;
                long fullOffset = baseOffset + offset;
                var layout = ats.layout();
                int dims = layout.quantizedVecBytes();

                // Read raw quant bytes
                byte[] rawVector = new byte[dims];
                MemorySegment.copy(ats.segment(), ValueLayout.JAVA_BYTE, fullOffset + 64, rawVector, 0, dims);

                List<Float> floats = new ArrayList<>(dims);
                for (byte b : rawVector) {
                    floats.add((float) b);
                }
                return new MemoryVectorResponse(id, dims, floats);
            }
            return new MemoryVectorResponse(id, 0, List.of());
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Get memory vector failed for id={}: {}", id, e.getMessage());
            return new MemoryVectorResponse(id, 0, List.of());
        }
    }

    /**
     * Update an existing memory's text and tags.
     */
    public void updateMemory(String id, UpdateMemoryRequest request) {
        if (!isAvailable()) return;
        try {
            var admin = memory.admin();
            var index = admin.index();
            String tierStr = index.metadata(id).get("tier");
            MemoryType tier = safeMemoryType(tierStr, MemoryType.SEMANTIC);
            MemorySource source = index.source(id);
            String[] tags = request.tags() != null ? request.tags().toArray(String[]::new) : index.tags(id);

            // Re-store memory with the same ID, overwriting previous content
            memory.remember(id, request.text(), tier, source, null, tags).join();
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
     * Returns a sampled overview of the memory graph (nodes + edges).
     */
    public MemoryGraphResponse getGraphOverview(int maxNodes) {
        if (!isAvailable()) return MemoryGraphResponse.empty(null);
        try {
            var admin = memory.admin();
            var index = admin.index();
            var hebbianGraph = admin.hebbianGraph();
            var temporalChain = admin.temporalChain();
            var entityGraph = admin.entityGraph();

            var allIds = index.allIds().stream().limit(maxNodes).toList();
            if (allIds.isEmpty()) return MemoryGraphResponse.empty(null);

            Map<Integer, String> slotToId = new LinkedHashMap<>();
            Map<String, Integer> idToSlot = new LinkedHashMap<>();
            index.buildGraphSlotMappings(slotToId, idToSlot);

            List<MemoryDto.GraphNodeDto> nodes = new ArrayList<>();
            for (String id : allIds) {
                var record = memory.inspect(id);
                if (record == null) continue;
                var entityNames = entityNamesForMemory(entityGraph, idToSlot.getOrDefault(id, -1));
                nodes.add(new MemoryDto.GraphNodeDto(
                        id,
                        record.memoryType() != null ? record.memoryType().name() : "SEMANTIC",
                        truncate(record.text(), 120),
                        record.importance(),
                        record.valence(),
                        record.timestampMs(),
                        entityNames
                ));
            }

            List<MemoryDto.GraphEdgeDto> edges = new ArrayList<>();
            for (String id : allIds) {
                int slot = idToSlot.getOrDefault(id, -1);
                if (slot < 0) continue;

                // HEBBIAN edges
                try {
                    var hebbianNeighbors = hebbianGraph.neighbors(slot);
                    for (var edge : hebbianNeighbors) {
                        String neighborId = slotToId.get(edge.neighborIndex());
                        if (neighborId != null && allIds.contains(neighborId)) {
                            edges.add(new MemoryDto.GraphEdgeDto(
                                     id, neighborId, "HEBBIAN", null,
                                     Math.min(1.0, edge.weight()), null, null));
                        }
                    }
                } catch (Exception ignored) {}

                // TEMPORAL edges
                try {
                    int[] forward = temporalChain.followForward(slot, 1);
                    for (int neighborSlot : forward) {
                        String neighborId = slotToId.get(neighborSlot);
                        if (neighborId != null && allIds.contains(neighborId)) {
                            edges.add(new MemoryDto.GraphEdgeDto(
                                    id, neighborId, "TEMPORAL", null, 0.8, null, null));
                        }
                    }
                } catch (Exception ignored) {}
            }

            // ENTITY edges
            try {
                var nameIndex = entityGraph.nameIndex();
                for (var entry : nameIndex.entrySet()) {
                    int entityId = entry.getValue();
                    String entityType = safeEntityType(entityGraph, entityId);
                    var entityEdgeList = entityGraph.edges(entityId);
                    for (var ee : entityEdgeList) {
                        int[] fromMems = entityGraph.memoriesForEntity(entityId);
                        int[] toMems = entityGraph.memoriesForEntity(ee.targetEntityId());
                        String toEntityType = safeEntityType(entityGraph, ee.targetEntityId());
                        for (int fm : fromMems) {
                            String fromMemId = slotToId.get(fm);
                            if (fromMemId == null || !allIds.contains(fromMemId)) continue;
                            for (int tm : toMems) {
                                String toMemId = slotToId.get(tm);
                                if (toMemId == null || !allIds.contains(toMemId)) continue;
                                edges.add(new MemoryDto.GraphEdgeDto(
                                        fromMemId, toMemId, "ENTITY", ee.relationType(),
                                        (double) ee.weight(), entityType, toEntityType
                                ));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            return new MemoryGraphResponse(null, nodes, edges);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Graph overview failed: {}", e.getMessage(), e);
            return MemoryGraphResponse.empty("Overview failed: " + e.getMessage());
        }
    }

    /**
     * Returns the Hebbian/Temporal/Entity neighborhood for a specific memory.
     */
    public MemoryGraphResponse getMemoryGraph(String id, int depth) {
        if (!isAvailable()) return MemoryGraphResponse.empty(null);
        try {
            var admin = memory.admin();
            var index = admin.index();
            var hebbianGraph = admin.hebbianGraph();
            var temporalChain = admin.temporalChain();
            var entityGraph = admin.entityGraph();

            Map<Integer, String> slotToId = new LinkedHashMap<>();
            Map<String, Integer> idToSlot = new LinkedHashMap<>();
            index.buildGraphSlotMappings(slotToId, idToSlot);

            int startSlot = idToSlot.getOrDefault(id, -1);
            if (startSlot < 0) return MemoryGraphResponse.empty("Memory ID not found in index slot map");

            List<String> visitedIds = new ArrayList<>();
            List<MemoryDto.GraphEdgeDto> edges = new ArrayList<>();

            // Perform simple BFS traversal up to depth
            List<Integer> currentLevel = new ArrayList<>();
            currentLevel.add(startSlot);
            visitedIds.add(id);

            for (int d = 0; d < depth; d++) {
                List<Integer> nextLevel = new ArrayList<>();
                for (int slot : currentLevel) {
                    String currentId = slotToId.get(slot);
                    if (currentId == null) continue;

                    // HEBBIAN neighbors
                    try {
                        var neighbors = hebbianGraph.neighbors(slot);
                        for (var edge : neighbors) {
                            int nSlot = edge.neighborIndex();
                            String nId = slotToId.get(nSlot);
                            if (nId != null) {
                                edges.add(new MemoryDto.GraphEdgeDto(
                                         currentId, nId, "HEBBIAN", null,
                                         Math.min(1.0, edge.weight()), null, null));
                                if (!visitedIds.contains(nId)) {
                                    visitedIds.add(nId);
                                    nextLevel.add(nSlot);
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    // TEMPORAL neighbors
                    try {
                        int[] forward = temporalChain.followForward(slot, 1);
                        for (int nSlot : forward) {
                            String nId = slotToId.get(nSlot);
                            if (nId != null) {
                                edges.add(new MemoryDto.GraphEdgeDto(
                                         currentId, nId, "TEMPORAL", null, 0.8, null, null));
                                if (!visitedIds.contains(nId)) {
                                    visitedIds.add(nId);
                                    nextLevel.add(nSlot);
                                }
                            }
                        }
                        int[] backward = temporalChain.followBackward(slot, 1);
                        for (int nSlot : backward) {
                            String nId = slotToId.get(nSlot);
                            if (nId != null) {
                                edges.add(new MemoryDto.GraphEdgeDto(
                                         nId, currentId, "TEMPORAL", null, 0.8, null, null));
                                if (!visitedIds.contains(nId)) {
                                    visitedIds.add(nId);
                                    nextLevel.add(nSlot);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (nextLevel.isEmpty()) break;
                currentLevel = nextLevel;
            }

            // Inspect and build nodes
            List<MemoryDto.GraphNodeDto> nodes = new ArrayList<>();
            for (String vid : visitedIds) {
                var record = memory.inspect(vid);
                if (record == null) continue;
                var entityNames = entityNamesForMemory(entityGraph, idToSlot.getOrDefault(vid, -1));
                nodes.add(new MemoryDto.GraphNodeDto(
                        vid,
                        record.memoryType() != null ? record.memoryType().name() : "SEMANTIC",
                        truncate(record.text(), 120),
                        record.importance(),
                        record.valence(),
                        record.timestampMs(),
                        entityNames
                ));
            }

            return new MemoryGraphResponse(id, nodes, edges);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Graph fetch failed for id={}: {}", id, e.getMessage(), e);
            return MemoryGraphResponse.empty(id);
        }
    }

    /**
     * Returns topology statistics.
     */
    public MemoryDto.TopologyStatsResponse getTopologyStats() {
        if (!isAvailable()) return MemoryDto.TopologyStatsResponse.empty();
        try {
            var entityGraph = memory.admin().entityGraph();
            var nameIndex = entityGraph.nameIndex();

            Map<String, int[]> entityTypeAgg = new LinkedHashMap<>();
            Map<String, int[]> relationTypeAgg = new LinkedHashMap<>();

            for (var entry : nameIndex.entrySet()) {
                int entityId = entry.getValue();
                String entityType = safeEntityType(entityGraph, entityId);

                var eStats = entityTypeAgg.computeIfAbsent(entityType, k -> new int[3]);
                eStats[0]++; // node count
                int memCount = 0;
                try { memCount = entityGraph.memoryRefCount(entityId); } catch (Exception ignored) {}
                eStats[2] += memCount; // memory refs

                try {
                    var edgeList = entityGraph.edges(entityId);
                    eStats[1] += edgeList.size();
                    for (var ee : edgeList) {
                        String relType = ee.relationType() != null ? ee.relationType() : "UNKNOWN";
                        var rStats = relationTypeAgg.computeIfAbsent(relType, k -> new int[3]);
                        rStats[0]++; // edge count
                        rStats[1] += 2; // from + to node
                        try {
                            rStats[2] += entityGraph.memoryRefCount(ee.targetEntityId());
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            List<MemoryDto.EntityTypeStatsDto> entityTypes = entityTypeAgg.entrySet().stream()
                    .map(e -> new MemoryDto.EntityTypeStatsDto(
                            e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                    .toList();

            List<MemoryDto.RelationTypeStatsDto> relationTypes = relationTypeAgg.entrySet().stream()
                    .map(e -> new MemoryDto.RelationTypeStatsDto(
                            e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                    .toList();

            return new MemoryDto.TopologyStatsResponse(entityTypes, relationTypes);
        } catch (Exception e) {
            log.error("[MemoryAccessObject] Topology stats failed: {}", e.getMessage(), e);
            return MemoryDto.TopologyStatsResponse.empty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════════════════════

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

    private static List<String> entityNamesForMemory(com.spectrayan.spector.memory.graph.EntityGraph graph, int slot) {
        if (graph == null || slot < 0) return List.of();
        try {
            List<String> names = new ArrayList<>();
            var nameIndex = graph.nameIndex();
            for (var entry : nameIndex.entrySet()) {
                int[] mems = graph.memoriesForEntity(entry.getValue());
                for (int m : mems) {
                    if (m == slot) {
                        names.add(entry.getKey());
                        break;
                    }
                }
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String safeEntityType(com.spectrayan.spector.memory.graph.EntityGraph graph, int entityId) {
        try {
            return graph.entityType(entityId);
        } catch (Exception e) {
            return "ENTITY";
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }
}
