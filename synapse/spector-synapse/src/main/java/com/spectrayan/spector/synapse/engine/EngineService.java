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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.spectrayan.spector.commons.TextChunk;
import com.spectrayan.spector.commons.WordTokenizer;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorApiException;
import com.spectrayan.spector.commons.error.SpectorEmbeddingException;
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.query.SearchQuery;
import com.spectrayan.spector.query.SearchResponse;
import com.spectrayan.spector.rag.ContextBuilder;
import com.spectrayan.spector.rag.ContextResult;
import com.spectrayan.spector.rag.ScoredChunk;
import com.spectrayan.spector.synapse.engine.EngineDto.BulkIngestRequest;
import com.spectrayan.spector.synapse.engine.EngineDto.BulkIngestResponse;
import com.spectrayan.spector.synapse.engine.EngineDto.EngineStatus;
import com.spectrayan.spector.synapse.engine.EngineDto.IngestRequest;
import com.spectrayan.spector.synapse.engine.EngineDto.RagRequest;
import com.spectrayan.spector.synapse.engine.EngineDto.RagResponse;
import com.spectrayan.spector.synapse.engine.EngineDto.SearchResult;

/**
 * Spring-managed engine service — replaces the node's hand-rolled service facades.
 *
 * <p>Wraps {@link SpectorEngine} with Spring lifecycle management. The engine
 * bean is auto-configured by {@code SpectorAutoConfiguration} in the
 * {@code spector-spring} module.</p>
 *
 * <p>Migrated from {@code spector-node} service package as part of the
 * node → synapse merge (GitHub #284).</p>
 */
@Service
public class EngineService {

    private static final Logger log = LoggerFactory.getLogger(EngineService.class);

    private final SpectorEngine engine;
    private final ContextBuilder contextBuilder;

    public EngineService(SpectorEngine engine) {
        this.engine = engine;
        this.contextBuilder = new ContextBuilder();
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Executes a search query against the vector index.
     */
    public EngineDto.SearchResponse search(EngineDto.SearchRequest request) {
        SearchQuery query = buildQuery(request);
        SearchResponse response = engine.search(query);

        List<SearchResult> results = Arrays.stream(response.results())
                .map(r -> {
                    var doc = engine.documentStore().get(r.id());
                    return new SearchResult(
                            r.id(),
                            doc != null ? doc.content() : null,
                            doc != null ? doc.title() : null,
                            r.score(),
                            response.mode().name());
                })
                .toList();

        return new EngineDto.SearchResponse(
                results,
                response.totalHits(),
                response.queryTimeMs(),
                response.mode().name());
    }

    // ══════════════════════════════════════════════════════════════
    // INGEST
    // ══════════════════════════════════════════════════════════════

    /**
     * Ingests a document with a pre-computed vector.
     */
    public void ingest(IngestRequest request) {
        if (request.vector() == null || request.vector().length == 0) {
            throw new IllegalArgumentException("vector must not be empty for manual ingest");
        }
        engine.ingest(request.id(), request.titleOrEmpty(), request.content(), request.vector());
        log.debug("Ingested document: {}", request.id());
    }

    /**
     * Ingests a document with automatic embedding.
     */
    public void autoIngest(IngestRequest request) {
        if (!engine.hasEmbeddingProvider()) {
            throw SpectorApiException.conflict(ErrorCode.EMBEDDING_PROVIDER_MISSING);
        }
        if (request.title() != null && !request.title().isEmpty()) {
            engine.ingest(request.id(), request.title(), request.content());
        } else {
            engine.ingest(request.id(), request.content());
        }
        log.debug("Auto-ingested document: {}", request.id());
    }

    /**
     * Bulk ingests multiple documents.
     */
    public BulkIngestResponse bulkIngest(BulkIngestRequest request) {
        request.validate();
        int success = 0;
        int failed = 0;

        for (var doc : request.documents()) {
            try {
                if (doc.id() == null || doc.content() == null) {
                    failed++;
                    continue;
                }
                if (doc.vector() != null && doc.vector().length > 0) {
                    engine.ingest(doc.id(), doc.titleOrEmpty(), doc.content(), doc.vector());
                } else if (engine.hasEmbeddingProvider()) {
                    engine.ingest(doc.id(), doc.content());
                } else {
                    failed++;
                    continue;
                }
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("Bulk ingest failed for doc '{}': {}", doc.id(), e.getMessage());
            }
        }
        return new BulkIngestResponse(request.documents().size(), success, failed);
    }

    /**
     * Deletes a document by ID.
     */
    public boolean delete(String id) {
        return engine.delete(id);
    }

    // ══════════════════════════════════════════════════════════════
    // RAG
    // ══════════════════════════════════════════════════════════════

    /**
     * Executes the RAG pipeline: embed → search → assemble context.
     */
    public RagResponse retrieveContext(RagRequest request) {
        request.validate();

        if (!engine.hasEmbeddingProvider()) {
            throw SpectorApiException.serviceUnavailable(
                    ErrorCode.EMBEDDING_UNAVAILABLE, "No embedding provider configured");
        }

        // 1. Embed query
        float[] queryVector;
        try {
            queryVector = engine.embeddingProvider().embed(request.query()).vector();
        } catch (SpectorEmbeddingException e) {
            throw SpectorApiException.serviceUnavailable(
                    ErrorCode.EMBEDDING_UNAVAILABLE, e.getMessage());
        }

        // 2. Search
        SearchQuery query = request.isHybrid()
                ? SearchQuery.hybrid(request.query(), queryVector, request.resolvedTopK())
                : SearchQuery.vector(queryVector, request.resolvedTopK());

        SearchResponse searchResponse = engine.search(query);

        if (searchResponse.results() == null || searchResponse.results().length == 0) {
            return new RagResponse("", List.of(), "No matching documents were found");
        }

        // 3. Build scored chunks
        List<ScoredChunk> scoredChunks = Arrays.stream(searchResponse.results())
                .map(r -> {
                    var doc = engine.documentStore().get(r.id());
                    if (doc == null || doc.content() == null || doc.content().isBlank()) return null;
                    int tokens = WordTokenizer.countTokens(doc.content());
                    var chunk = new TextChunk(doc.content(), tokens, 0, doc.content().length(), r.id());
                    return new ScoredChunk(chunk, r.score());
                })
                .filter(Objects::nonNull)
                .toList();

        // 4. Assemble context
        ContextResult contextResult = contextBuilder.build(
                new ArrayList<>(scoredChunks), request.resolvedTokenLimit());

        if (contextResult.isEmpty()) {
            return new RagResponse("", List.of(), "No matching documents were found");
        }

        // 5. Build response
        var attributions = contextResult.attributions().stream()
                .map(a -> Map.<String, Object>of(
                        "documentId", a.documentId(),
                        "chunkOffset", a.chunkOffset()))
                .toList();

        return new RagResponse(contextResult.contextText(), attributions, null);
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns engine status and configuration.
     */
    public EngineStatus getStatus() {
        return new EngineStatus(
                engine.config().dimensions(),
                engine.documentStore().size(),
                engine.documentStore().size(),
                engine.hasEmbeddingProvider(),
                Map.of());
    }

    // ── private helpers ─────────────────────────────────────────────

    private SearchQuery buildQuery(EngineDto.SearchRequest request) {
        int topK = request.resolvedTopK();
        return switch (request.resolvedMode()) {
            case "KEYWORD" -> SearchQuery.keyword(request.query(), topK);
            case "VECTOR" -> SearchQuery.vector(request.vector(), topK);
            default -> {
                if (request.vector() != null && request.query() != null) {
                    yield SearchQuery.hybrid(request.query(), request.vector(), topK);
                } else if (request.vector() != null) {
                    yield SearchQuery.vector(request.vector(), topK);
                } else {
                    yield SearchQuery.keyword(request.query(), topK);
                }
            }
        };
    }
}
