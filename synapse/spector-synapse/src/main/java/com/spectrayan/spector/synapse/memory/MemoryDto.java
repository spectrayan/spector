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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Memory REST API data transfer objects.
 *
 * <p>All DTOs are Java records — immutable, compact, with built-in
 * serialization support for Jackson.</p>
 */
public final class MemoryDto {

    private MemoryDto() {}

    // ══════════════════════════════════════════════════════════════
    // REQUEST DTOs
    // ══════════════════════════════════════════════════════════════

    /**
     * Request to store a new memory (legacy simple store).
     *
     * @param text       the text content to memorize
     * @param tags       optional tags for categorization
     * @param importance optional importance weight (0.0-1.0)
     * @param metadata   optional key-value metadata
     */
    public record StoreRequest(
            String text,
            List<String> tags,
            Double importance,
            Map<String, String> metadata
    ) {}

    /**
     * Request to remember a new memory via the Cortex UI remember flow.
     *
     * <p>Aligns with the {@code RememberRequest} interface in the Angular
     * {@code MemoryTableService}. Cognitive hints (ICNU) are optional.</p>
     *
     * @param id        unique memory identifier (auto-generated if null/blank)
     * @param text      memory content (required)
     * @param tier      memory tier: WORKING, EPISODIC, SEMANTIC (default), PROCEDURAL
     * @param source    provenance: USER_STATED, OBSERVED (default), INFERRED, PROCEDURAL
     * @param tags      comma-separated contextual tags
     * @param interest  ICNU Interest (0.0–1.0)
     * @param challenge ICNU Challenge (0.0–1.0)
     * @param urgency   ICNU Urgency (0.0–1.0)
     * @param valence   emotional valence (-128 to +127)
     * @param arousal   emotional intensity (0 to 255)
     */
    public record RememberRequest(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("tier") String tier,
            @JsonProperty("source") String source,
            @JsonProperty("tags") String tags,
            @JsonProperty("interest") Float interest,
            @JsonProperty("challenge") Float challenge,
            @JsonProperty("urgency") Float urgency,
            @JsonProperty("valence") Integer valence,
            @JsonProperty("arousal") Integer arousal
    ) {
        /** Returns the tier or default {@code SEMANTIC}. */
        public String effectiveTier() {
            return tier != null && !tier.isBlank() ? tier.toUpperCase() : "SEMANTIC";
        }

        /** Returns the source or default {@code OBSERVED}. */
        public String effectiveSource() {
            return source != null && !source.isBlank() ? source.toUpperCase() : "OBSERVED";
        }

        /** Returns tags as array, splitting on commas. */
        public String[] tagsArray() {
            if (tags == null || tags.isBlank()) return new String[0];
            return tags.split(",");
        }

        /** Returns true if any ICNU/emotional hint was provided. */
        public boolean hasCognitiveHints() {
            return (interest != null && interest > 0)
                    || (challenge != null && challenge > 0)
                    || (urgency != null && urgency > 0)
                    || (valence != null && valence != 0)
                    || (arousal != null && arousal != 0);
        }
    }

    /**
     * Request for semantic search.
     *
     * @param query     search query text
     * @param topK      max results to return (default: 10)
     * @param threshold minimum similarity threshold (0.0-1.0)
     */
    public record SearchRequest(
            String query,
            Integer topK,
            Double threshold
    ) {
        public SearchRequest {
            if (topK == null || topK <= 0) topK = 10;
            if (threshold == null || threshold < 0) threshold = 0.0;
        }
    }

    /**
     * Request for cognitive recall.
     *
     * @param query  recall query
     * @param topK   max results
     * @param depth  recall depth (how many association hops)
     */
    public record RecallRequest(
            String query,
            Integer topK,
            Integer depth
    ) {
        public RecallRequest {
            if (topK == null || topK <= 0) topK = 10;
            if (depth == null || depth <= 0) depth = 1;
        }
    }

    /**
     * Request to reinforce a memory (legacy body-based form).
     *
     * @param id  memory ID to reinforce
     */
    public record ReinforceRequest(String id) {}

    /**
     * Request to reinforce a memory by path variable (Cortex UI form).
     *
     * @param valence emotional valence feedback (-128 to 127)
     */
    public record ReinforceByIdRequest(
            @JsonProperty("valence") Integer valence
    ) {
        public int effectiveValence() {
            return valence != null ? Math.clamp(valence, -128, 127) : 0;
        }
    }

    /**
     * Request to suppress or unsuppress a memory.
     *
     * @param action  {@code SUPPRESS} or {@code UNSUPPRESS}
     * @param reason  optional reason for suppression
     */
    public record SuppressRequest(
            @JsonProperty("action") String action,
            @JsonProperty("reason") String reason
    ) {
        public boolean isSuppressing() {
            return action == null || !"UNSUPPRESS".equalsIgnoreCase(action.trim());
        }

        public String effectiveReason() {
            return reason != null ? reason : "";
        }
    }

    /**
     * Request to mark a memory as resolved or unresolved.
     *
     * @param resolved {@code true} to resolve, {@code false} to unresolve
     */
    public record ResolveRequest(
            @JsonProperty("resolved") Boolean resolved
    ) {
        public boolean isResolving() {
            return resolved == null || resolved;
        }
    }

    /**
     * Request to trigger vacuum compaction for a tier.
     *
     * @param tier the memory tier to compact
     */
    public record VacuumRequest(
            @JsonProperty("tier") String tier
    ) {
        public String effectiveTier() {
            return tier != null && !tier.isBlank() ? tier.toUpperCase() : "SEMANTIC";
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RESPONSE DTOs
    // ══════════════════════════════════════════════════════════════

    /**
     * A single row in the memory table view.
     *
     * <p>Aligns exactly with the {@code MemoryRow} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param id               unique memory identifier
     * @param text             full text content
     * @param textPreview      truncated text content (max 200 chars)
     * @param tier             memory tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param source           provenance source (USER_STATED, OBSERVED, etc.)
     * @param importance       importance score (0.0 to 1.0)
     * @param valence          emotional valence (-128 to 127)
     * @param arousal          emotional intensity (unsigned 0-255)
     * @param timestampMs      creation timestamp (epoch millis)
     * @param agentRecallCount number of times recalled
     * @param recallCount      alias for agentRecallCount
     * @param tombstoned       true if soft-deleted
     * @param suppressed       true if suppressed from recall
     * @param pinned           true if pinned
     * @param resolved         true if resolved
     * @param consolidated     true if consolidated during reflect
     * @param tags             synaptic tag strings
     * @param synapticTags     64-bit Bloom filter as long
     * @param createdAt        ISO-8601 creation timestamp string
     * @param metadata         optional key-value metadata
     */
    public record MemoryTableRow(
            @JsonProperty("id") String id,
            @JsonProperty("text") String text,
            @JsonProperty("textPreview") String textPreview,
            @JsonProperty("tier") String tier,
            @JsonProperty("source") String source,
            @JsonProperty("importance") float importance,
            @JsonProperty("valence") int valence,
            @JsonProperty("arousal") int arousal,
            @JsonProperty("timestampMs") long timestampMs,
            @JsonProperty("agentRecallCount") int agentRecallCount,
            @JsonProperty("recallCount") int recallCount,
            @JsonProperty("tombstoned") boolean tombstoned,
            @JsonProperty("suppressed") boolean suppressed,
            @JsonProperty("pinned") boolean pinned,
            @JsonProperty("resolved") boolean resolved,
            @JsonProperty("consolidated") boolean consolidated,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("synapticTags") long synapticTags,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("metadata") Map<String, String> metadata
    ) {}

    /**
     * Paginated memory table response.
     *
     * <p>Aligns with the {@code MemoryTableResponse} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param rows            paginated list of memory rows
     * @param totalCount      total matching records (before pagination)
     * @param page            current page number (0-based)
     * @param pageSize        rows per page
     * @param tierCounts      count of records per tier
     * @param tombstoneRatios tombstone ratio per tier (0.0 to 1.0)
     */
    public record MemoryTableResponse(
            @JsonProperty("rows") List<MemoryTableRow> rows,
            @JsonProperty("totalCount") int totalCount,
            @JsonProperty("page") int page,
            @JsonProperty("pageSize") int pageSize,
            @JsonProperty("tierCounts") Map<String, Integer> tierCounts,
            @JsonProperty("tombstoneRatios") Map<String, Float> tombstoneRatios
    ) {}

    /**
     * Response for a single memory detail.
     */
    public record MemoryResponse(
            String id,
            String text,
            String tier,
            double score,
            List<String> tags,
            Map<String, String> metadata,
            Instant createdAt,
            Instant lastRecalledAt,
            int recallCount
    ) {}

    /**
     * Response for store operation.
     */
    public record StoreResponse(
            String id,
            String text,
            String tier,
            double score,
            String message
    ) {}

    /**
     * Response for async remember/ingest operations (202 Accepted).
     *
     * <p>Aligns with the {@code AcceptedResponse} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param taskId     background task identifier
     * @param id         memory ID (for remember operations)
     * @param fileName   original file name (for ingest-file operations)
     * @param documentId document identifier (for ingest-file operations)
     * @param status     always "accepted"
     */
    public record AcceptedResponse(
            @JsonProperty("taskId") String taskId,
            @JsonProperty("id") String id,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("status") String status
    ) {
        /** Convenience factory for remember responses. */
        public static AcceptedResponse forRemember(String taskId, String memoryId) {
            return new AcceptedResponse(taskId, memoryId, null, null, "accepted");
        }

        /** Convenience factory for file ingest responses. */
        public static AcceptedResponse forFileIngest(String taskId, String fileName, String documentId) {
            return new AcceptedResponse(taskId, null, fileName, documentId, "accepted");
        }
    }

    /**
     * Paginated list response (generic).
     */
    public record PageResponse<T>(
            List<T> items,
            int page,
            int pageSize,
            long totalItems,
            int totalPages
    ) {}

    /**
     * Search result with similarity score.
     */
    public record SearchResult(
            String id,
            String text,
            String tier,
            double score,
            double similarity,
            List<String> tags,
            Instant createdAt
    ) {}

    /**
     * Cognitive recall result with memory type annotation.
     */
    public record RecallResult(
            String id,
            String text,
            String tier,
            double cognitiveScore,
            String memoryType,
            String ageDescription,
            List<String> tags
    ) {}

    /**
     * Reinforce response.
     */
    public record ReinforceResponse(
            String id,
            double previousScore,
            double newScore,
            String tier,
            String message
    ) {}

    /**
     * Memory status response.
     *
     * <p>Aligns with the {@code MemoryStatus} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param totalMemories  total memory count across all tiers
     * @param tierCounts     breakdown by tier
     * @param hebbianEdges   active Hebbian co-activation associations
     * @param temporalLinks  temporal causal chain associations
     * @param entityNodes    entity graph node count
     * @param entityEdges    entity graph edge count
     */
    public record MemoryStatusResponse(
            @JsonProperty("totalMemories") int totalMemories,
            @JsonProperty("tierCounts") Map<String, Integer> tierCounts,
            @JsonProperty("hebbianEdges") int hebbianEdges,
            @JsonProperty("temporalLinks") int temporalLinks,
            @JsonProperty("entityNodes") int entityNodes,
            @JsonProperty("entityEdges") int entityEdges
    ) {}

    /**
     * Memory statistics (legacy admin stats form).
     */
    public record StatsResponse(
            long totalCount,
            Map<String, Long> tierDistribution,
            long storageBytes,
            Instant lastActivity
    ) {}

    public record MemoryStats(
            long totalCount,
            Map<String, Long> tierDistribution,
            long storageBytes,
            IndexStats indexStats,
            ConsolidationStats consolidationStats,
            Map<String, Long> growthOverTime,
            Map<String, Double> decayForecast
    ) {}

    public record IndexStats(
            long totalEntries,
            int levels,
            double recallEstimate
    ) {}

    public record ConsolidationStats(
            long lastRunTimestamp,
            int memoriesMerged,
            int duplicatesRemoved,
            int partitionsCompacted
        ) {
            public static ConsolidationStats empty() {
                return new ConsolidationStats(0L, 0, 0, 0);
            }
        }

    public record ScoringStats(
            double avgSimilarity,
            double avgRecency,
            double avgFrequency,
            double avgImportance,
            double avgValence
    ) {}

    /**
     * Reflect consolidation response.
     *
     * @param tombstonedCount  memories tombstoned during consolidation
     * @param durationMs       duration of the consolidation cycle in milliseconds
     * @param message          human-readable summary
     */
    public record ReflectResponse(
            @JsonProperty("tombstonedCount") int tombstonedCount,
            @JsonProperty("durationMs") long durationMs,
            @JsonProperty("message") String message
    ) {}

    /**
     * Vacuum compaction result for a single tier.
     *
     * <p>Aligns with the {@code CompactionResult} interface in the Angular
     * {@code MemoryTableService}.</p>
     */
    public record CompactionResult(
            @JsonProperty("tier") String tier,
            @JsonProperty("beforeCount") int beforeCount,
            @JsonProperty("afterCount") int afterCount,
            @JsonProperty("tombstonesRemoved") int tombstonesRemoved,
            @JsonProperty("bytesReclaimed") long bytesReclaimed,
            @JsonProperty("durationMs") long durationMs
    ) {}

    /**
     * Generic error response.
     */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            Instant timestamp
    ) {
        public ErrorResponse(int status, String error, String message) {
            this(status, error, message, Instant.now());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GRAPH API DTOs
    // ══════════════════════════════════════════════════════════════

    /**
     * A node in the memory graph.
     *
     * <p>Aligns with the {@code GraphNode} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param id           unique memory identifier
     * @param tier         memory tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param textPreview  first 120 characters of memory text
     * @param importance   importance score (0.0–1.0)
     * @param valence      emotional valence (-128 to +127)
     * @param timestampMs  creation timestamp in epoch milliseconds
     * @param entityNames  entity names co-occurring in this memory (may be null)
     */
    public record GraphNodeDto(
            @JsonProperty("id") String id,
            @JsonProperty("tier") String tier,
            @JsonProperty("textPreview") String textPreview,
            @JsonProperty("importance") double importance,
            @JsonProperty("valence") int valence,
            @JsonProperty("timestampMs") long timestampMs,
            @JsonProperty("entityNames") List<String> entityNames
    ) {}

    /**
     * An edge in the memory graph.
     *
     * <p>Aligns with the {@code GraphEdge} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param fromId         source memory ID
     * @param toId           target memory ID
     * @param type           edge type: HEBBIAN, TEMPORAL, or ENTITY
     * @param relation       relation label (entity edges only, null for others)
     * @param weight         edge weight (0.0–1.0)
     * @param fromEntityType source entity type (entity edges only, null for others)
     * @param toEntityType   target entity type (entity edges only, null for others)
     */
    public record GraphEdgeDto(
            @JsonProperty("fromId") String fromId,
            @JsonProperty("toId") String toId,
            @JsonProperty("type") String type,
            @JsonProperty("relation") String relation,
            @JsonProperty("weight") double weight,
            @JsonProperty("fromEntityType") String fromEntityType,
            @JsonProperty("toEntityType") String toEntityType
    ) {}

    /**
     * Memory graph response — used by both overview and per-memory neighborhood.
     *
     * <p>Aligns with the {@code MemoryGraphResponse} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param memoryId  the focal memory ID (null for overview)
     * @param nodes     graph nodes
     * @param edges     graph edges
     */
    public record MemoryGraphResponse(
            @JsonProperty("memoryId") String memoryId,
            @JsonProperty("nodes") List<GraphNodeDto> nodes,
            @JsonProperty("edges") List<GraphEdgeDto> edges
    ) {
        /** Returns an empty graph (stub mode or no memories). */
        public static MemoryGraphResponse empty(String memoryId) {
            return new MemoryGraphResponse(memoryId, List.of(), List.of());
        }
    }

    /**
     * Entity type statistics for the topology panel.
     *
     * @param type      entity type name (e.g. PERSON, ORG, TECH)
     * @param nodes     number of entity nodes of this type
     * @param edges     number of edges involving this entity type
     * @param memories  number of memories referencing this entity type
     */
    public record EntityTypeStatsDto(
            @JsonProperty("type") String type,
            @JsonProperty("nodes") int nodes,
            @JsonProperty("edges") int edges,
            @JsonProperty("memories") int memories
    ) {}

    /**
     * Relation type statistics for the topology panel.
     *
     * @param type      relation/edge type name (e.g. WORKS_AT, RELATED_TO)
     * @param edges     number of edges of this relation type
     * @param nodes     number of entity nodes involved in this relation type
     * @param memories  number of memories linked via this relation type
     */
    public record RelationTypeStatsDto(
            @JsonProperty("type") String type,
            @JsonProperty("edges") int edges,
            @JsonProperty("nodes") int nodes,
            @JsonProperty("memories") int memories
    ) {}

    /**
     * Topology statistics response.
     *
     * <p>Aligns with the {@code TopologyStatsResponse} interface in the Angular
     * {@code MemoryTableService}.</p>
     *
     * @param entityTypes    per-entity-type statistics
     * @param relationTypes  per-relation-type statistics
     */
    public record TopologyStatsResponse(
            @JsonProperty("entityTypes") List<EntityTypeStatsDto> entityTypes,
            @JsonProperty("relationTypes") List<RelationTypeStatsDto> relationTypes
    ) {
        /** Returns an empty topology stats (stub mode or no memories). */
        public static TopologyStatsResponse empty() {
            return new TopologyStatsResponse(List.of(), List.of());
        }
    }

    /**
     * Request to update an existing memory's text and/or tags.
     */
    public record UpdateMemoryRequest(
            @JsonProperty("text") String text,
            @JsonProperty("tags") List<String> tags
    ) {}

    /**
     * Response containing the INT8 quantized embedding vector for a memory.
     */
    public record MemoryVectorResponse(
            @JsonProperty("memoryId") String memoryId,
            @JsonProperty("dimension") int dimension,
            @JsonProperty("values") List<Float> values
    ) {}
}

