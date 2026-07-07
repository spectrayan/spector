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

    // ── Request DTOs ──

    /**
     * Request to store a new memory.
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
     * Request for semantic search.
     *
     * @param query  search query text
     * @param topK   max results to return (default: 10)
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
     * Request to reinforce a memory.
     *
     * @param id  memory ID to reinforce
     */
    public record ReinforceRequest(String id) {}

    // ── Response DTOs ──

    /**
     * Response for a single memory.
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
     * Paginated list response.
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
     * Memory statistics.
     */
    public record StatsResponse(
            long totalCount,
            Map<String, Long> tierDistribution,
            long storageBytes,
            Instant lastActivity
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
}
