/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.node.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import com.spectrayan.spector.node.api.dto.MemoryStatusDto;
import com.spectrayan.spector.node.event.SpectorCortexMemorySnapshotEvent;
import com.spectrayan.spector.node.event.SpectorCortexReflectCycleEvent;
import com.spectrayan.spector.node.event.SpectorEventBus;

/**
 * Service facade that wraps {@link SpectorMemory} and emits cortex
 * dashboard events after key operations.
 *
 * <p>Sits at the spector-node layer so it can access both the memory
 * subsystem (via spector-memory dependency) and the event bus (via
 * spector-node). This avoids polluting the memory module with event
 * bus coupling.</p>
 *
 * <p>Latency is <em>not</em> manually timed here — the Micrometer
 * {@code MeteredSpectorMemory} decorator handles that. This service
 * uses the duration from {@link ReflectReport#duration()} which is
 * computed inside the reflect daemon itself.</p>
 *
 * <h3>Emitted Events</h3>
 * <ul>
 *   <li>{@link SpectorCortexReflectCycleEvent} — after each reflect() call</li>
 *   <li>{@link SpectorCortexMemorySnapshotEvent} — pre/post reflect snapshots for diff view</li>
 * </ul>
 */
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final SpectorMemory memory;
    private final SpectorEventBus eventBus;
    private final String nodeId;

    public MemoryService(SpectorMemory memory, SpectorEventBus eventBus, String nodeId) {
        this.memory = memory;
        this.eventBus = eventBus;
        this.nodeId = nodeId;
    }

    /**
     * Triggers a reflection cycle and emits cortex events.
     *
     * <p>Emits a pre-reflect snapshot, runs the consolidation cycle,
     * then emits a post-reflect snapshot and a reflect cycle summary.
     * The pre/post snapshots share a {@code reflectCycleId} so the
     * dashboard can compute deltas.</p>
     *
     * <p>Uses duration from the {@link ReflectReport} itself rather than
     * manually timing — Micrometer's {@code MeteredSpectorMemory} handles
     * latency recording separately.</p>
     *
     * @return the reflect report
     */
    public ReflectReport reflect() {
        String reflectCycleId = UUID.randomUUID().toString();

        // ── Pre-reflect snapshot ──
        eventBus.publish(captureSnapshot("pre-reflect", reflectCycleId));

        // ── Run consolidation ──
        ReflectReport report = memory().reflect();

        // ── Post-reflect snapshot ──
        eventBus.publish(captureSnapshot("post-reflect", reflectCycleId));

        // ── Reflect cycle summary event ──
        long durationMs = report.duration().toMillis();
        eventBus.publish(new SpectorCortexReflectCycleEvent(
                nodeId, Instant.now(),
                report.tombstonedCount(),
                report.temporalPrunedCount(),
                0.9, // decay factor used in reflect()
                durationMs));

        return report;
    }

    /**
     * Captures a memory snapshot for the diff view.
     *
     * <p>Reads current counts from the memory subsystem. Graph-level
     * stats (edges, nodes) are placeholders until tier-level APIs
     * are exposed by SpectorMemory.</p>
     */
    private SpectorCortexMemorySnapshotEvent captureSnapshot(String phase, String reflectCycleId) {
        int totalCount = memory().totalMemories();
        return new SpectorCortexMemorySnapshotEvent(
                nodeId, Instant.now(),
                phase, reflectCycleId,
                0,              // hebbianEdgeCount — TBD when graph stats API available
                0,              // temporalLinkCount — TBD
                0,              // entityNodeCount — TBD
                0,              // entityEdgeCount — TBD
                0L,             // offHeapBytes — TBD (from PanamaMemoryDetector)
                0,              // tombstoneCount — TBD
                0,              // coActivationPairs — TBD
                0);             // stdpEdges — TBD
    }

    /** Returns the underlying memory instance. */
    public SpectorMemory memory() { return memory; }

    /** Stores a memory with full cognitive metadata. */
    public CompletableFuture<Void> remember(String id, String text, MemoryType tier,
                                           MemorySource source, IngestionHints hints,
                                           String... tags) {
        return memory().remember(id, text, tier, source, hints, tags);
    }

    /**
     * Updates a memory in-place (reconsolidation) without chunking.
     *
     * <p>Directly re-embeds the text and writes to the cognitive store,
     * bypassing the chunking pipeline in {@link #remember}. The index text,
     * tags, and location are atomically updated.</p>
     *
     * @param id      existing memory ID
     * @param newText updated text (null = keep existing)
     * @param newTags updated tags (null = keep existing)
     */
    public void updateMemoryInPlace(String id, String newText, String[] newTags) {
        var admin = memory().admin();
        var index = admin.index();
        var loc = index.locate(id);
        if (loc == null) {
            throw new IllegalArgumentException("Memory not found: " + id);
        }

        // Capture existing metadata before removing from index
        String text = newText != null ? newText : index.text(id);
        String[] tags = newTags != null ? newTags : index.tags(id);
        var source = index.source(id);
        var type = loc.type();

        // Tombstone the old binary slot and remove from index
        // This is required because ingestCognitive has a dedup guard that
        // skips ingestion when index.locate(id) != null.
        var tierRouter = admin.tierRouter();
        var layout = tierRouter.layoutFor(loc.type());
        var segment = tierRouter.segmentFor(loc.type());
        if (layout != null && segment != null) {
            layout.tombstone(segment, loc.offset());
        }
        index.remove(id);

        // Get embedding provider from the concrete implementation
        com.spectrayan.spector.memory.DefaultSpectorMemory dsm =
                (com.spectrayan.spector.memory.DefaultSpectorMemory) memory();
        float[] vector = dsm.embeddingProvider().embed(text).vector();

        // Re-ingest: dedup guard passes (id removed), writes fresh record,
        // register() creates new index entry with updated text/tags/offset
        memory().target().ingestCognitive(id, text, vector, type, tags,
                source != null ? source : MemorySource.OBSERVED,
                (IngestionHints) null);
    }

    /** Performs cognitive recall and emits a cortex query trace event. */
    public List<CognitiveResult> recall(String query, RecallOptions options) {
        long startNanos = System.nanoTime();
        List<CognitiveResult> results = memory().recall(query, options);
        long latencyMicros = (System.nanoTime() - startNanos) / 1_000;

        // Emit cortex.query.trace SSE event — drives dashboard/graph particles
        int resultCount = results.size();
        eventBus.publish(new com.spectrayan.spector.node.event.SpectorCortexQueryTraceEvent(
                nodeId, Instant.now(),
                query,
                "default",
                0,                              // synapticTagMask
                memory().totalMemories(),         // totalRecords
                memory().totalMemories(),         // afterTombstone (no filter breakdown available)
                memory().totalMemories(),         // afterTagGate
                memory().totalMemories(),         // afterValence
                memory().totalMemories(),         // afterDecay
                resultCount,                    // afterVectorDistance
                resultCount,                    // finalTopK
                Math.max(1, resultCount / 2),   // hebbianActivated (estimated)
                Math.max(1, resultCount / 3),   // temporalLinked (estimated)
                Math.max(1, resultCount / 4),   // entityDiscovered (estimated)
                latencyMicros));

        log.debug("recall: query='{}', results={}, latency={}µs", query, resultCount, latencyMicros);
        return results;
    }

    /** Tombstones a memory by ID. */
    public void forget(String id) {
        memory().forget(id);
    }

    /** Reports outcome feedback for a memory. */
    public void reinforce(String id, byte valence) {
        memory().reinforce(id, valence);
    }

    /** Suppresses a memory from future recall. */
    public void suppress(String id, String reason) {
        if (reason != null && !reason.isBlank()) {
            memory().suppress(id, reason);
        } else {
            memory().suppress(id);
        }
    }

    /** Unsuppresses a memory. */
    public void unsuppress(String id) {
        memory().unsuppress(id);
    }

    /** Marks a memory as resolved. */
    public void markResolved(String id) {
        memory().markResolved(id);
    }

    /** Marks a memory as unresolved. */
    public void markUnresolved(String id) {
        memory().markUnresolved(id);
    }

    /** Returns comprehensive stats and status of the cognitive memory system. */
    public MemoryStatusDto getStatus() {
        int total = memory().totalMemories();
        var counts = Map.of(
                "WORKING", memory().memoryCount(MemoryType.WORKING),
                "EPISODIC", memory().memoryCount(MemoryType.EPISODIC),
                "SEMANTIC", memory().memoryCount(MemoryType.SEMANTIC),
                "PROCEDURAL", memory().memoryCount(MemoryType.PROCEDURAL)
        );
        int hebbian = memory().hebbianGraph() != null ? memory().hebbianGraph().totalEdges() : 0;
        int entityNodes = memory().entityGraph() != null ? memory().entityGraph().entityCount() : 0;
        int entityEdges = memory().entityGraph() != null ? memory().entityGraph().edgeCount() : 0;

        int temporalLinks = 0;
        if (memory().temporalChain() != null) {
            int cap = memory().temporalChain().capacity();
            for (int i = 0; i < cap; i++) {
                if (memory().temporalChain().isLinked(i)) {
                    temporalLinks++;
                }
            }
        }

        return new MemoryStatusDto(total, counts, hebbian, temporalLinks, entityNodes, entityEdges);
    }

    /** Returns statistics of the entity types and relationship types extracted. */
    public com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto getTopologyStats() {
        var admin = memory().admin();
        var entityGraph = admin.entityGraph();
        if (entityGraph == null) {
            return new com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto(java.util.List.of(), java.util.List.of());
        }

        int entityCount = entityGraph.entityCount();

        // Accumulators for Entity Types
        java.util.Map<String, java.util.Set<Integer>> entityNodesByType = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<Integer>> entityMemoriesByType = new java.util.HashMap<>();
        java.util.Map<String, Integer> entityEdgesByType = new java.util.HashMap<>();

        // Accumulators for Relation Types
        java.util.Map<String, Integer> relEdgesByType = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<Integer>> relNodesByType = new java.util.HashMap<>();
        java.util.Map<String, java.util.Set<Integer>> relMemoriesByType = new java.util.HashMap<>();

        for (int eid = 0; eid < entityCount; eid++) {
            String type = entityGraph.entityType(eid);
            if (type == null || type.isBlank()) {
                type = "OTHER";
            }
            type = type.toUpperCase(java.util.Locale.ROOT);

            entityNodesByType.computeIfAbsent(type, k -> new java.util.HashSet<>()).add(eid);

            int[] memRefs = entityGraph.memoriesForEntity(eid);
            if (memRefs != null) {
                var memSet = entityMemoriesByType.computeIfAbsent(type, k -> new java.util.HashSet<>());
                for (int ref : memRefs) {
                    memSet.add(ref);
                }
            }

            var edges = entityGraph.edges(eid);
            if (edges != null) {
                for (var edge : edges) {
                    int targetId = edge.targetEntityId();
                    String relType = edge.relationType();
                    if (relType == null || relType.isBlank()) {
                        relType = "RELATED_TO";
                    }
                    relType = relType.toUpperCase(java.util.Locale.ROOT);

                    // Count outbound edge for source entity's type
                    entityEdgesByType.put(type, entityEdgesByType.getOrDefault(type, 0) + 1);

                    // Relation type stats
                    relEdgesByType.put(relType, relEdgesByType.getOrDefault(relType, 0) + 1);
                    
                    var relNodes = relNodesByType.computeIfAbsent(relType, k -> new java.util.HashSet<>());
                    relNodes.add(eid);
                    if (targetId >= 0 && targetId < entityCount) {
                        relNodes.add(targetId);
                    }

                    var relMems = relMemoriesByType.computeIfAbsent(relType, k -> new java.util.HashSet<>());
                    if (memRefs != null) {
                        for (int ref : memRefs) {
                            relMems.add(ref);
                        }
                    }
                    if (targetId >= 0 && targetId < entityCount) {
                        int[] targetMemRefs = entityGraph.memoriesForEntity(targetId);
                        if (targetMemRefs != null) {
                            for (int ref : targetMemRefs) {
                                relMems.add(ref);
                            }
                        }
                    }
                }
            }
        }

        java.util.List<com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto.EntityTypeStatsDto> entityTypesList = new java.util.ArrayList<>();
        for (var entry : entityNodesByType.entrySet()) {
            String type = entry.getKey();
            int nodes = entry.getValue().size();
            int memories = entityMemoriesByType.getOrDefault(type, java.util.Set.of()).size();
            int edges = entityEdgesByType.getOrDefault(type, 0);
            entityTypesList.add(new com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto.EntityTypeStatsDto(type, nodes, edges, memories));
        }

        java.util.List<com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto.RelationTypeStatsDto> relationTypesList = new java.util.ArrayList<>();
        for (var entry : relEdgesByType.entrySet()) {
            String type = entry.getKey();
            int edges = entry.getValue();
            int nodes = relNodesByType.getOrDefault(type, java.util.Set.of()).size();
            int memories = relMemoriesByType.getOrDefault(type, java.util.Set.of()).size();
            relationTypesList.add(new com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto.RelationTypeStatsDto(type, edges, nodes, memories));
        }

        return new com.spectrayan.spector.node.api.dto.MemoryTopologyStatsDto(entityTypesList, relationTypesList);
    }


    /** Introspects the agent's knowledge about a topic. */
    public com.spectrayan.spector.memory.metamemory.MemoryInsight introspect(String topic) {
        return memory().introspect(topic);
    }

    /** Schedules a prospective memory reminder. */
    public com.spectrayan.spector.memory.prospective.Reminder scheduleReminder(
            String text, java.time.Duration delay, String... tags) {
        return memory().scheduleReminder(text, delay, tags);
    }

    /** Stores a note in working memory scratchpad. */
    public java.util.concurrent.CompletableFuture<Void> scratchpad(String text) {
        return memory().scratchpad(text);
    }

    /** Explains why a specific memory was not returned for a query. */
    public com.spectrayan.spector.memory.model.WhyNotExplanation whyNot(
            String memoryId, String query, RecallOptions options) {
        return memory().whyNot(memoryId, query, options);
    }

    // ══════════════════════════════════════════════════════════════
    // TABLE VIEW (Feature 5)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns a paginated table view of all memory records across tiers.
     *
     * <p>Reads headers from tier store segments using visibleCount() for
     * SWMR safety, joins with MemoryIndex for text and tags.</p>
     *
     * @param page        page number (0-based)
     * @param pageSize    rows per page
     * @param tierFilter  optional tier name filter (null = all tiers)
     * @param showTombstoned  whether to include tombstoned records
     * @return paginated table DTO
     */

    /**
     * Returns detailed information for a single memory by its ID.
     *
     * <p>Unlike the table view which truncates text to 200 chars, this method
     * returns the full text content along with all cognitive metadata.</p>
     *
     * @param memoryId the memory ID to look up
     * @return a MemoryRowDto with full text, or null if not found
     */
    public com.spectrayan.spector.node.api.dto.MemoryRowDto getMemoryById(String memoryId) {
        var admin = memory().admin();
        var index = admin.index();
        var loc = index.locate(memoryId);
        if (loc == null) return null;

        String text = index.text(memoryId);
        var source = index.source(memoryId);
        String[] tags = index.tags(memoryId);

        var store = admin.tierRouter().get(loc.type());
        if (store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats) {
            var header = ats.layout().readHeader(ats.segment(), loc.offset());
            byte flags = header.flags();
            boolean tombstoned = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isTombstoned(flags);
            boolean pinned = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isPinned(flags);
            boolean resolved = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isResolved(flags);
            boolean consolidated = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isConsolidated(flags);

            return new com.spectrayan.spector.node.api.dto.MemoryRowDto(
                    memoryId, text, loc.type().name(),
                    source != null ? source.name() : "OBSERVED",
                    header.importance(), header.valence(),
                    header.timestampMs(), header.agentRecallCount(),
                    tombstoned, pinned, resolved, consolidated, tags);
        }

        return new com.spectrayan.spector.node.api.dto.MemoryRowDto(
                memoryId, text, loc.type().name(),
                source != null ? source.name() : "OBSERVED",
                0f, 0, 0L, 0, false, false, false, false, tags);
    }

    public com.spectrayan.spector.node.api.dto.MemoryTableDto getMemoryTable(
            int page, int pageSize, String tierFilter, boolean showTombstoned) {

        var admin = memory().admin();
        var tierRouter = admin.tierRouter();
        var index = admin.index();

        // Collect all rows across tiers
        java.util.List<com.spectrayan.spector.node.api.dto.MemoryRowDto> allRows = new java.util.ArrayList<>();
        java.util.Map<String, Integer> tierCounts = new java.util.LinkedHashMap<>();

        for (MemoryType type : MemoryType.values()) {
            if (tierFilter != null && !tierFilter.equalsIgnoreCase(type.name())) continue;

            var store = tierRouter.get(type);
            if (!(store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats)) continue;

            int visibleCount = ats.visibleCount();
            tierCounts.put(type.name(), visibleCount);
            var layout = ats.layout();
            long baseOffset = ats.isPersistent()
                    ? com.spectrayan.spector.memory.cortex.AbstractTierStore.METADATA_HEADER_BYTES : 0;

            for (int i = 0; i < visibleCount; i++) {
                long offset = baseOffset + (long) i * layout.stride();
                var header = layout.readHeader(ats.segment(), offset);
                byte flags = header.flags();

                boolean tombstoned = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isTombstoned(flags);
                if (!showTombstoned && tombstoned) continue;

                String id = index.findIdByOffset(type, offset);
                String text = id != null ? index.text(id) : "";
                String textPreview = text.length() > 200 ? text.substring(0, 200) + "…" : text;
                var source = id != null ? index.source(id) : com.spectrayan.spector.memory.cortex.MemorySource.OBSERVED;
                String[] tags = id != null ? index.tags(id) : new String[0];

                boolean pinned = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isPinned(flags);
                boolean resolved = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isResolved(flags);
                boolean consolidated = com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isConsolidated(flags);
                int arousal = Byte.toUnsignedInt(header.arousal());
                var sourceModality = com.spectrayan.spector.memory.model.SourceModality.fromOrdinal(
                        com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.sourceModalityOrdinal(flags));
                String synapticTagsHex = "0x" + Long.toHexString(header.synapticTags());
                Map<String, String> metadata = id != null ? index.metadata(id) : null;
                if (metadata != null && metadata.isEmpty()) metadata = null;

                allRows.add(new com.spectrayan.spector.node.api.dto.MemoryRowDto(
                        id != null ? id : "unknown-" + type.name() + "-" + i,
                        textPreview, type.name(), source.name(),
                        header.importance(), header.valence(),
                        header.timestampMs(), header.agentRecallCount(),
                        tombstoned, pinned, resolved, consolidated, tags,
                        arousal, sourceModality.name(), header.exactNorm(),
                        header.storageStrength(), synapticTagsHex, metadata));
            }
        }

        // Sort by timestamp descending (newest first)
        allRows.sort((a, b) -> Long.compare(b.timestampMs(), a.timestampMs()));

        // Paginate
        int totalCount = allRows.size();
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        var pageRows = fromIndex < totalCount
                ? allRows.subList(fromIndex, toIndex) : java.util.List.<com.spectrayan.spector.node.api.dto.MemoryRowDto>of();

        // Tombstone ratios
        java.util.Map<String, Float> tombstoneRatios = new java.util.LinkedHashMap<>();
        var ratios = admin.tombstoneRatios();
        for (var entry : ratios.entrySet()) {
            tombstoneRatios.put(entry.getKey().name(), entry.getValue());
        }

        return new com.spectrayan.spector.node.api.dto.MemoryTableDto(
                pageRows, totalCount, page, pageSize, tierCounts, tombstoneRatios);
    }

    /**
     * Triggers vacuum compaction for a specific tier.
     *
     * @param tier the tier to compact
     * @return compaction result, or null if no compaction needed
     */
    public com.spectrayan.spector.memory.sync.CompactionResult vacuum(MemoryType tier) {
        return memory().admin().vacuum(tier);
    }

    // ══════════════════════════════════════════════════════════════
    // GRAPH VIEW (Phase 5)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the graph neighborhood for a specific memory.
     *
     * <p>Gathers Hebbian neighbors (spreading activation), temporal chain
     * (prev/next), and entity relationships to build a subgraph centered
     * on the given memory ID.</p>
     *
     * @param memoryId the focal memory ID
     * @param depth    activation depth (1–3)
     * @return graph DTO with nodes and edges, or null if memory not found
     */
    public com.spectrayan.spector.node.api.dto.MemoryGraphDto getMemoryGraph(String memoryId, int depth) {
        var admin = memory().admin();
        var index = admin.index();
        var loc = index.locate(memoryId);
        if (loc == null) return null;

        // Build slot index mapping: iterate all memories in insertion order to find
        // the slot index for this memory and for resolving neighbor indices back to IDs
        java.util.Map<Integer, String> slotToId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> idToSlot = new java.util.LinkedHashMap<>();
        buildSlotMappings(admin, slotToId, idToSlot);

        Integer focalSlot = idToSlot.get(memoryId);
        if (focalSlot == null) return null;

        // Collect nodes and edges
        java.util.Map<String, com.spectrayan.spector.node.api.dto.GraphNodeDto> nodeMap = new java.util.LinkedHashMap<>();
        java.util.List<com.spectrayan.spector.node.api.dto.GraphEdgeDto> edges = new java.util.ArrayList<>();

        // Add focal node
        nodeMap.put(memoryId, buildGraphNode(memoryId, admin));

        // ── Hebbian neighbors ──
        var hebbianGraph = admin.hebbianGraph();
        if (hebbianGraph != null) {
            for (var edge : hebbianGraph.activateNeighbors(focalSlot, depth)) {
                String neighborId = slotToId.get(edge.neighborIndex());
                if (neighborId != null) {
                    nodeMap.putIfAbsent(neighborId, buildGraphNode(neighborId, admin));
                    edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                            memoryId, neighborId, "HEBBIAN", null, edge.weight()));
                }
            }
        }

        // ── Temporal chain (forward + backward) ──
        var temporalChain = admin.temporalChain();
        if (temporalChain != null) {
            int[] forward = temporalChain.followForward(focalSlot, depth * 2);
            for (int i = 0; i < forward.length; i++) {
                String fromId = i == 0 ? memoryId : slotToId.get(forward[i - 1]);
                String toId = slotToId.get(forward[i]);
                if (fromId != null && toId != null) {
                    nodeMap.putIfAbsent(toId, buildGraphNode(toId, admin));
                    edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                            fromId, toId, "TEMPORAL", null, 1.0f));
                }
            }
            int[] backward = temporalChain.followBackward(focalSlot, depth * 2);
            for (int i = 0; i < backward.length; i++) {
                String toId = i == 0 ? memoryId : slotToId.get(backward[i - 1]);
                String fromId = slotToId.get(backward[i]);
                if (fromId != null && toId != null) {
                    nodeMap.putIfAbsent(fromId, buildGraphNode(fromId, admin));
                    edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                            fromId, toId, "TEMPORAL", null, 1.0f));
                }
            }
        }

        // ── Entity graph ──
        var entityGraph = admin.entityGraph();
        if (entityGraph != null) {
            // Find entities linked to this memory's slot
            var nameIndex = entityGraph.nameIndex();
            for (var entry : nameIndex.entrySet()) {
                int entityId = entry.getValue();
                int[] memRefs = entityGraph.memoriesForEntity(entityId);
                boolean linked = false;
                for (int ref : memRefs) {
                    if (ref == focalSlot) { linked = true; break; }
                }
                if (!linked) continue;

                // This entity is linked to our focal memory — find other memories it references
                for (int ref : memRefs) {
                    if (ref == focalSlot) continue;
                    String otherId = slotToId.get(ref);
                    if (otherId != null) {
                        nodeMap.putIfAbsent(otherId, buildGraphNode(otherId, admin));
                        edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                                memoryId, otherId, "ENTITY",
                                entry.getKey().toUpperCase(), 1.0f));
                    }
                }
            }
        }

        // ── Deduplicate edges (same fromId+toId+type = keep first) ──
        var seenEdges = new java.util.LinkedHashMap<String, com.spectrayan.spector.node.api.dto.GraphEdgeDto>();
        for (var edge : edges) {
            String lo = edge.fromId().compareTo(edge.toId()) < 0 ? edge.fromId() : edge.toId();
            String hi = edge.fromId().compareTo(edge.toId()) < 0 ? edge.toId() : edge.fromId();
            String key = lo + "|" + hi + "|" + edge.type();
            seenEdges.putIfAbsent(key, edge);
        }

        return new com.spectrayan.spector.node.api.dto.MemoryGraphDto(
                memoryId, new java.util.ArrayList<>(nodeMap.values()),
                new java.util.ArrayList<>(seenEdges.values()));
    }

    /**
     * Returns a sampled overview of the entire memory graph.
     *
     * <p>Samples the top-N memories by importance across all tiers,
     * then gathers their Hebbian edges to build an overview graph.</p>
     *
     * @param maxNodes maximum number of nodes to include
     * @return graph DTO with sampled nodes and edges
     */
    public com.spectrayan.spector.node.api.dto.MemoryGraphDto getGraphOverview(int maxNodes) {
        var admin = memory().admin();

        // Build slot mappings
        java.util.Map<Integer, String> slotToId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> idToSlot = new java.util.LinkedHashMap<>();
        buildSlotMappings(admin, slotToId, idToSlot);

        // Collect all memories with importance, sort by importance desc
        java.util.List<java.util.Map.Entry<String, Float>> ranked = new java.util.ArrayList<>();
        var tierRouter = admin.tierRouter();
        for (MemoryType type : MemoryType.values()) {
            var store = tierRouter.get(type);
            if (!(store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats)) continue;
            int visibleCount = ats.visibleCount();
            var layout = ats.layout();
            long baseOffset = ats.isPersistent()
                    ? com.spectrayan.spector.memory.cortex.AbstractTierStore.METADATA_HEADER_BYTES : 0;
            for (int i = 0; i < visibleCount; i++) {
                long offset = baseOffset + (long) i * layout.stride();
                var header = layout.readHeader(ats.segment(), offset);
                byte flags = header.flags();
                if (com.spectrayan.spector.memory.synapse.SynapticHeaderConstants.isTombstoned(flags)) continue;
                String id = admin.index().findIdByOffset(type, offset);
                if (id != null) {
                    ranked.add(java.util.Map.entry(id, header.importance()));
                }
            }
        }
        ranked.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        int nodeLimit = Math.min(maxNodes, ranked.size());

        // Build nodes
        java.util.Map<String, com.spectrayan.spector.node.api.dto.GraphNodeDto> nodeMap = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> includedSlots = new java.util.HashSet<>();
        for (int i = 0; i < nodeLimit; i++) {
            String id = ranked.get(i).getKey();
            nodeMap.put(id, buildGraphNode(id, admin));
            Integer slot = idToSlot.get(id);
            if (slot != null) includedSlots.add(slot);
        }

        // Gather edges between included nodes
        java.util.List<com.spectrayan.spector.node.api.dto.GraphEdgeDto> edges = new java.util.ArrayList<>();
        var hebbianGraph = admin.hebbianGraph();
        if (hebbianGraph != null) {
            for (int slot : includedSlots) {
                String fromId = slotToId.get(slot);
                if (fromId == null) continue;
                for (var edge : hebbianGraph.neighbors(slot)) {
                    if (includedSlots.contains(edge.neighborIndex())) {
                        String toId = slotToId.get(edge.neighborIndex());
                        if (toId != null && fromId.compareTo(toId) < 0) { // avoid duplicates
                            edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                                    fromId, toId, "HEBBIAN", null, edge.weight()));
                        }
                    }
                }
            }
        }

        // Temporal edges between included nodes
        var temporalChain = admin.temporalChain();
        if (temporalChain != null) {
            for (int slot : includedSlots) {
                int[] fwd = temporalChain.followForward(slot, 1);
                if (fwd.length > 0 && includedSlots.contains(fwd[0])) {
                    String fromId = slotToId.get(slot);
                    String toId = slotToId.get(fwd[0]);
                    if (fromId != null && toId != null) {
                        edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                                fromId, toId, "TEMPORAL", null, 1.0f));
                    }
                }
            }
        }

        // Entity graph edges between included nodes
        // Use slot-based lookup via memoriesForEntity() instead of text-preview
        // substring matching, which missed most entities due to 120-char truncation.
        var entityGraph = admin.entityGraph();
        if (entityGraph != null) {
            java.util.Set<String> skipNames = java.util.Set.of(
                    "jarvis", "mike", "mike thompson", "hey", "hey jarvis");
            var nameIdx = entityGraph.nameIndex();
            java.util.Set<String> entityEdgesSeen = new java.util.HashSet<>();

            for (var entry : nameIdx.entrySet()) {
                String entityName = entry.getKey();
                int eid = entry.getValue();
                if (entityName.length() < 2 || skipNames.contains(entityName)) continue;

                // Get the memory slots linked to this entity
                int[] memRefs = entityGraph.memoriesForEntity(eid);
                if (memRefs.length < 2) continue; // need ≥2 memories to form edges

                // Map slots to IDs and filter to only those in the sampled node set
                java.util.List<String> includedMemIds = new java.util.ArrayList<>();
                for (int memSlot : memRefs) {
                    if (includedSlots.contains(memSlot)) {
                        String memId = slotToId.get(memSlot);
                        if (memId != null) {
                            includedMemIds.add(memId);
                        }
                    }
                }

                if (includedMemIds.size() < 2) continue;

                // Get relation type from entity edges
                var eEdges = entityGraph.edges(eid);
                String relType = eEdges.isEmpty() ? "RELATED_TO" : eEdges.get(0).relationType();

                // Create edges between included memories sharing this entity
                for (int i = 0; i < includedMemIds.size(); i++) {
                    for (int j = i + 1; j < includedMemIds.size(); j++) {
                        String fromId = includedMemIds.get(i);
                        String toId = includedMemIds.get(j);
                        if (fromId.compareTo(toId) > 0) { String tmp = fromId; fromId = toId; toId = tmp; }
                        String edgeKey = fromId + "|" + toId;
                        if (entityEdgesSeen.add(edgeKey)) {
                            edges.add(new com.spectrayan.spector.node.api.dto.GraphEdgeDto(
                                    fromId, toId, "ENTITY", entityName.toUpperCase(java.util.Locale.ROOT), 1.0f));
                        }
                    }
                }
            }
        }

        log.debug("getGraphOverview: {} nodes, {} edges (slots={}, slotMap={})",
                nodeMap.size(), edges.size(), includedSlots.size(), slotToId.size());

        // ── Deduplicate edges ──
        var seenEdges = new java.util.LinkedHashMap<String, com.spectrayan.spector.node.api.dto.GraphEdgeDto>();
        for (var edge : edges) {
            String lo = edge.fromId().compareTo(edge.toId()) < 0 ? edge.fromId() : edge.toId();
            String hi = edge.fromId().compareTo(edge.toId()) < 0 ? edge.toId() : edge.fromId();
            String key = lo + "|" + hi + "|" + edge.type();
            seenEdges.putIfAbsent(key, edge);
        }

        return new com.spectrayan.spector.node.api.dto.MemoryGraphDto(
                null, new java.util.ArrayList<>(nodeMap.values()),
                new java.util.ArrayList<>(seenEdges.values()));
    }

    /**
     * Builds a GraphNodeDto from a memory ID by reading its header data.
     */
    private com.spectrayan.spector.node.api.dto.GraphNodeDto buildGraphNode(
            String id, com.spectrayan.spector.memory.SpectorMemoryAdmin admin) {
        var index = admin.index();
        var loc = index.locate(id);
        if (loc == null) {
            return new com.spectrayan.spector.node.api.dto.GraphNodeDto(id, "UNKNOWN", "", 0f, 0, 0L);
        }

        String text = index.text(id);
        String preview = text.length() > 120 ? text.substring(0, 120) + "…" : text;

        // Read header for importance/valence
        var store = admin.tierRouter().get(loc.type());
        if (store instanceof com.spectrayan.spector.memory.cortex.AbstractTierStore ats) {
            var header = ats.layout().readHeader(ats.segment(), loc.offset());
            return new com.spectrayan.spector.node.api.dto.GraphNodeDto(
                    id, loc.type().name(), preview, header.importance(), header.valence(), header.timestampMs());
        }

        return new com.spectrayan.spector.node.api.dto.GraphNodeDto(id, loc.type().name(), preview, 0f, 0, 0L);
    }

    /**
     * Builds bidirectional slot ↔ ID mappings by iterating all memories
     * in the MemoryIndex in a stable order.
     *
     * <p>The graph subsystems (HebbianGraph, TemporalChain) use integer
     * slot indices assigned at ingestion time via {@code index.size() - 1}.
     * To resolve these back to string IDs, we rebuild the mapping by
     * iterating the index's location map sorted by offset within each tier.</p>
     */
    private void buildSlotMappings(
            com.spectrayan.spector.memory.SpectorMemoryAdmin admin,
            java.util.Map<Integer, String> slotToId,
            java.util.Map<String, Integer> idToSlot) {

        var locationMap = admin.index().locationMap();

        // Sort all entries by a global ordering: tier ordinal then offset
        // This reconstructs the original insertion order (slot indices)
        java.util.List<java.util.Map.Entry<String, com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation>> entries =
                new java.util.ArrayList<>(locationMap.entrySet());
        entries.sort((a, b) -> {
            int tierCmp = Integer.compare(a.getValue().type().ordinal(), b.getValue().type().ordinal());
            if (tierCmp != 0) return tierCmp;
            return Long.compare(a.getValue().offset(), b.getValue().offset());
        });

        int slot = 0;
        for (var entry : entries) {
            slotToId.put(slot, entry.getKey());
            idToSlot.put(entry.getKey(), slot);
            slot++;
        }
        log.debug("buildSlotMappings: mapped {} slots from {} index entries", 
                idToSlot.size(), locationMap.size());
    }

    // ══════════════════════════════════════════════════════════════
    // BULK IMPORT — Hebbian Edges, Temporal Chains, Entity Relations
    // ══════════════════════════════════════════════════════════════

    /**
     * Bulk-imports Hebbian edges into the graph.
     *
     * @param edges list of edge definitions [{memoryIdA, memoryIdB, coActivationCount}]
     * @return import result counts
     */
    public Map<String, Integer> bulkImportHebbianEdges(List<Map<String, Object>> edges) {
        var hebbianGraph = memory().hebbianGraph();
        if (hebbianGraph == null) {
            return Map.of("loaded", 0, "skipped", edges.size(), "error", 0);
        }

        java.util.Map<Integer, String> slotToId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> idToSlot = new java.util.LinkedHashMap<>();
        buildSlotMappings(memory().admin(), slotToId, idToSlot);

        int loaded = 0, skipped = 0;
        for (var edge : edges) {
            String idA = String.valueOf(edge.get("memoryIdA"));
            String idB = String.valueOf(edge.get("memoryIdB"));
            Integer slotA = idToSlot.get(idA);
            Integer slotB = idToSlot.get(idB);

            if (slotA == null || slotB == null) {
                skipped++;
                continue;
            }

            int count = edge.containsKey("coActivationCount")
                    ? ((Number) edge.get("coActivationCount")).intValue() : 1;
            hebbianGraph.strengthen(slotA, slotB, (float) count);
            loaded++;
        }

        log.info("Imported {} Hebbian edges ({} skipped)", loaded, skipped);
        return Map.of("loaded", loaded, "skipped", skipped);
    }

    /**
     * Bulk-imports temporal chains into the chain graph.
     *
     * @param chains list of chain definitions [{sessionId, orderedMemoryIds: [...]}]
     * @return import result counts
     */
    public Map<String, Integer> bulkImportTemporalChains(List<Map<String, Object>> chains) {
        var temporalChain = memory().temporalChain();
        if (temporalChain == null) {
            int total = chains.stream()
                    .mapToInt(c -> ((List<?>) c.getOrDefault("orderedMemoryIds", List.of())).size())
                    .sum();
            return Map.of("linked", 0, "chains", 0, "skipped", total);
        }

        java.util.Map<Integer, String> slotToId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> idToSlot = new java.util.LinkedHashMap<>();
        buildSlotMappings(memory().admin(), slotToId, idToSlot);

        int linkedCount = 0, skipped = 0;

        for (var chainDef : chains) {
            String sessionId = String.valueOf(chainDef.get("sessionId"));
            @SuppressWarnings("unchecked")
            List<String> orderedIds = (List<String>) chainDef.getOrDefault("orderedMemoryIds", List.of());
            int sessionHash = sessionId.hashCode();

            Integer previousSlot = null;
            for (String memoryId : orderedIds) {
                Integer currentSlot = idToSlot.get(memoryId);
                if (currentSlot == null) {
                    skipped++;
                    previousSlot = null;
                    continue;
                }
                if (previousSlot != null) {
                    temporalChain.link(currentSlot, previousSlot, sessionHash);
                    linkedCount++;
                }
                previousSlot = currentSlot;
            }
        }

        log.info("Imported {} temporal links across {} chains ({} skipped)",
                linkedCount, chains.size(), skipped);
        return Map.of("linked", linkedCount, "chains", chains.size(), "skipped", skipped);
    }

    /**
     * Bulk-imports entity relations into the entity graph.
     *
     * @param relations list of relation definitions [{fromEntity: {name, type}, toEntity: {name, type}, relationType, sourceMemoryIds}]
     * @return import result counts
     */
    public Map<String, Integer> bulkImportEntityRelations(List<Map<String, Object>> relations) {
        var entityGraph = memory().entityGraph();
        if (entityGraph == null) {
            return Map.of("loaded", 0, "skipped", relations.size(), "entities", 0);
        }

        java.util.Map<Integer, String> slotToId = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> idToSlot = new java.util.LinkedHashMap<>();
        buildSlotMappings(memory().admin(), slotToId, idToSlot);

        int relationsLoaded = 0;

        for (var relation : relations) {
            @SuppressWarnings("unchecked")
            var fromEntity = (Map<String, String>) relation.get("fromEntity");
            @SuppressWarnings("unchecked")
            var toEntity = (Map<String, String>) relation.get("toEntity");
            if (fromEntity == null || toEntity == null) continue;

            // Pass type strings directly — TypeRegistry auto-registers unknown types
            String fromType = fromEntity.getOrDefault("type", "OTHER");
            int fromId = entityGraph.addEntity(fromEntity.get("name"), fromType);
            if (fromId < 0) continue;

            String toType = toEntity.getOrDefault("type", "OTHER");
            int toId = entityGraph.addEntity(toEntity.get("name"), toType);
            if (toId < 0) continue;

            String relType = String.valueOf(relation.getOrDefault("relationType", "OTHER"));
            entityGraph.addRelation(fromId, toId, relType);
            relationsLoaded++;

            @SuppressWarnings("unchecked")
            List<String> sourceMemoryIds = (List<String>) relation.getOrDefault("sourceMemoryIds", List.of());
            for (String memId : sourceMemoryIds) {
                Integer memSlot = idToSlot.get(memId);
                if (memSlot != null) {
                    entityGraph.linkEntityToMemory(fromId, memSlot);
                    entityGraph.linkEntityToMemory(toId, memSlot);
                }
            }
        }

        log.info("Imported {} entity relations (entities={})", relationsLoaded, entityGraph.entityCount());
        return Map.of("loaded", relationsLoaded, "entities", entityGraph.entityCount());
    }
}

