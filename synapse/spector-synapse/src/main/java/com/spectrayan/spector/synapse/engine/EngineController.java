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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Spector vector engine API.
 *
 * <p>Provides low-level vector search, document ingestion, RAG, and status
 * endpoints under {@code /api/v1/engine}. These complement the cognitive
 * memory API ({@code /api/v1/memory}) by exposing raw engine operations.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   EngineController  (HTTP: request/response, validation)
 *        │
 *   EngineService     (orchestration, SpectorEngine calls)
 *        │
 *   SpectorEngine     (vector index, document store)
 * </pre>
 *
 * <p>Migrated from {@code spector-node} as part of the node → synapse merge
 * (GitHub #284). Endpoints preserve API compatibility with the old node
 * REST API.</p>
 */
@RestController
@RequestMapping("/api/v1/engine")
public class EngineController {

    private static final Logger log = LoggerFactory.getLogger(EngineController.class);

    private final EngineService engineService;

    public EngineController(EngineService engineService) {
        this.engineService = engineService;
    }

    // ══════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Executes a vector/keyword/hybrid search.
     *
     * <p>{@code POST /api/v1/engine/search}</p>
     */
    @PostMapping("/search")
    public ResponseEntity<EngineDto.SearchResponse> search(
            @RequestBody EngineDto.SearchRequest request) {
        return ResponseEntity.ok(engineService.search(request));
    }

    // ══════════════════════════════════════════════════════════════
    // INGEST
    // ══════════════════════════════════════════════════════════════

    /**
     * Ingests a document with a pre-computed vector.
     *
     * <p>{@code POST /api/v1/engine/ingest}</p>
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingest(
            @RequestBody EngineDto.IngestRequest request) {
        engineService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ingested", "id", request.id()));
    }

    /**
     * Ingests a document with automatic embedding.
     *
     * <p>{@code POST /api/v1/engine/ingest/auto}</p>
     */
    @PostMapping("/ingest/auto")
    public ResponseEntity<Map<String, String>> autoIngest(
            @RequestBody EngineDto.IngestRequest request) {
        engineService.autoIngest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "ingested", "id", request.id()));
    }

    /**
     * Bulk ingests multiple documents.
     *
     * <p>{@code POST /api/v1/engine/ingest/bulk}</p>
     */
    @PostMapping("/ingest/bulk")
    public ResponseEntity<EngineDto.BulkIngestResponse> bulkIngest(
            @RequestBody EngineDto.BulkIngestRequest request) {
        return ResponseEntity.ok(engineService.bulkIngest(request));
    }

    // ══════════════════════════════════════════════════════════════
    // DOCUMENT
    // ══════════════════════════════════════════════════════════════

    /**
     * Deletes a document by ID.
     *
     * <p>{@code DELETE /api/v1/engine/documents/{id}}</p>
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean deleted = engineService.delete(id);
        return ResponseEntity.ok(Map.of("id", id, "deleted", deleted));
    }

    // ══════════════════════════════════════════════════════════════
    // RAG
    // ══════════════════════════════════════════════════════════════

    /**
     * Executes the RAG pipeline: embed → search → assemble context.
     *
     * <p>{@code POST /api/v1/engine/rag}</p>
     */
    @PostMapping("/rag")
    public ResponseEntity<EngineDto.RagResponse> rag(
            @RequestBody EngineDto.RagRequest request) {
        return ResponseEntity.ok(engineService.retrieveContext(request));
    }

    // ══════════════════════════════════════════════════════════════
    // STATUS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns engine status and configuration.
     *
     * <p>{@code GET /api/v1/engine/status}</p>
     */
    @GetMapping("/status")
    public ResponseEntity<EngineDto.EngineStatus> status() {
        return ResponseEntity.ok(engineService.getStatus());
    }
}
