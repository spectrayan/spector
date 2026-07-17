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
package com.spectrayan.spector.synapse.engine;

import java.util.List;
import java.util.Map;

/**
 * DTOs for the engine REST API (search, ingest, RAG, status).
 *
 * <p>Sealed interface hierarchy to keep all engine DTOs in a single file,
 * mirroring the pattern used by {@code MemoryDto}.</p>
 */
public final class EngineDto {

    private EngineDto() {} // utility class

    // ── Search ──────────────────────────────────────────────────────

    /**
     * @param query   text query (for keyword/hybrid)
     * @param vector  float vector (for vector/hybrid)
     * @param topK    max results (default 10)
     * @param mode    search mode: KEYWORD, VECTOR, HYBRID (default HYBRID)
     */
    public record SearchRequest(
            String query,
            float[] vector,
            Integer topK,
            String mode
    ) {
        public int resolvedTopK() { return topK != null && topK > 0 ? topK : 10; }
        public String resolvedMode() { return mode != null ? mode.toUpperCase() : "HYBRID"; }
    }

    public record SearchResult(
            String id,
            String content,
            String title,
            float score,
            String mode
    ) {}

    public record SearchResponse(
            List<SearchResult> results,
            int totalHits,
            long queryTimeMs,
            String mode
    ) {}

    // ── Ingest ──────────────────────────────────────────────────────

    /**
     * @param id      document ID
     * @param content document text content
     * @param title   optional title
     * @param vector  optional pre-computed vector (null = auto-embed)
     */
    public record IngestRequest(
            String id,
            String content,
            String title,
            float[] vector
    ) {
        public String titleOrEmpty() { return title != null ? title : ""; }
    }

    public record BulkIngestRequest(List<IngestRequest> documents) {
        public void validate() {
            if (documents == null || documents.isEmpty()) {
                throw new IllegalArgumentException("documents list must not be empty");
            }
        }
    }

    public record BulkIngestResponse(int total, int success, int failed) {}

    // ── RAG ─────────────────────────────────────────────────────────

    /**
     * @param query      the user query
     * @param topK       max chunks to retrieve (default 5)
     * @param tokenLimit max tokens in assembled context (default 4096)
     * @param hybrid     use hybrid search (default false)
     */
    public record RagRequest(
            String query,
            Integer topK,
            Integer tokenLimit,
            Boolean hybrid
    ) {
        public int resolvedTopK() { return topK != null && topK > 0 ? topK : 5; }
        public int resolvedTokenLimit() { return tokenLimit != null && tokenLimit > 0 ? tokenLimit : 4096; }
        public boolean isHybrid() { return hybrid != null && hybrid; }

        public void validate() {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
        }
    }

    public record RagResponse(
            String context,
            List<Map<String, Object>> attributions,
            String message
    ) {}

    // ── Status ──────────────────────────────────────────────────────

    public record EngineStatus(
            int dimensions,
            long documentCount,
            long indexSize,
            boolean embeddingProviderAvailable,
            Map<String, Object> metrics
    ) {}
}
