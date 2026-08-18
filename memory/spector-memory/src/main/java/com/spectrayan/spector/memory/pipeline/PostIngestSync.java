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
package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingResult;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.error.SpectorEntityGraphException;
import com.spectrayan.spector.memory.error.SpectorHebbianException;
import com.spectrayan.spector.memory.error.SpectorTemporalChainException;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Post-ingest index synchronization stage  --  handles steps 7b through 9d
 * of the cognitive ingestion pipeline.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Step 7b: HNSW index addition (SEMANTIC type only)</li>
 *   <li>Step 8: ID index registration</li>
 *   <li>Step 9: WAL append</li>
 *   <li>Step 9a: BM25 text index + text.dat persistence</li>
 *   <li>Step 9a-splade: SPLADE sparse index</li>
 *   <li>Step 9b: Hebbian edge strengthening (co-ingestion within session)</li>
 *   <li>Step 9c: Temporal chain linking (session-local sequence)</li>
 *   <li>Step 9d: Entity extraction and graph population</li>
 * </ul>
 *
 * <p>Extracted from {@link CognitiveIngestionTarget} to eliminate code duplication
 * across the three ingestion entry points (standard, migration, context-aware).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Stateless except for the subsystem references (all thread-safe).
 * The session tracking ({@code lastIngestedMemoryIdx}, {@code currentSessionId})
 * remains in {@link CognitiveIngestionTarget} since it is cross-invocation state.</p>
 */
public final class PostIngestSync {

    private static final Logger log = LoggerFactory.getLogger(PostIngestSync.class);

    //  Subsystem references (all nullable except index, wal) 
    private volatile CognitiveMemoryRouter cognitiveRouter;
    private final MemoryIndex index;
    private final MemoryWal wal;
    private final VectorIndex semanticIndex;
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityExtractor entityExtractor;
    private final EntityDirectory entityDirectory;
    private final MemoryBM25Index bm25Index;
    private volatile TextAppendMemory textDataStore;  // volatile: rolled with the partition (#443)
    private final int activePartitionIndex;           // BM25/SPLADE slot (always 0 — one partition slot)
    private volatile int activePartitionSeq;          // colocated-partition seq stamped on MemoryLocation (#443)
    private final MemorySpladeIndex spladeIndex;
    private final SparseEmbeddingProvider spladeProvider;
    private final DataEncryptor encryptor;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;

    public PostIngestSync(CognitiveMemoryRouter cognitiveRouter, MemoryIndex index, MemoryWal wal,
                   VectorIndex semanticIndex,
                   HebbianGraphBase hebbianGraph, TemporalChainMemory temporalChain,
                   EntityExtractor entityExtractor, EntityDirectory entityDirectory,
                   MemoryBM25Index bm25Index, TextAppendMemory textDataStore,
                   int activePartitionIndex,
                   MemorySpladeIndex spladeIndex, SparseEmbeddingProvider spladeProvider,
                   DataEncryptor encryptor,
                   HyperEntityGraphMemory hyperEntityGraph,
                   TemporalKnowledgeGraph temporalKnowledgeGraph) {
        this.cognitiveRouter = cognitiveRouter;
        this.index = index;
        this.wal = wal;
        this.semanticIndex = semanticIndex;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityExtractor = entityExtractor;
        this.entityDirectory = entityDirectory;
        this.bm25Index = bm25Index;
        this.textDataStore = textDataStore;
        this.activePartitionIndex = activePartitionIndex;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.activePartitionSeq = 0;
    }

    /** Called when the cognitive memory router is swapped after a partition roll. */
    public void updateCognitiveRouter(CognitiveMemoryRouter newRouter) {
        this.cognitiveRouter = newRouter;
    }

    /** Called when the partition-scoped {@code text.dat} is rolled (#443, D3b). */
    public void updateTextDataStore(TextAppendMemory newText) {
        this.textDataStore = newText;
    }

    /** Called when the active partition sequence changes on roll (#443). */
    public void updateActivePartitionSeq(int seq) {
        this.activePartitionSeq = seq;
    }

    /**
     * Parameters for post-ingest synchronization.
     */
    public record SyncParams(
            String id,
            String text,
            float[] vector,
            byte[] quantized,
            MemoryType type,
            String[] tags,
            MemorySource source,
            long offset,
            Map<String, String> metadata
    ) {
        public SyncParams(String id, String text, float[] vector, byte[] quantized,
                   MemoryType type, String[] tags, MemorySource source, long offset) {
            this(id, text, vector, quantized, type, tags, source, offset, null);
        }
    }

    /**
     * Executes post-ingest synchronization (Steps 7b-9a).
     *
     * <p>Write order is deliberate: text.dat is written FIRST so the byte position
     * can be captured and stored in MemoryLocation for off-heap random access.</p>
     *
     * @param params sync parameters
     * @return the monotonic global graph slot assigned to this memory
     */
    public int syncIndexes(SyncParams params) {
        boolean isParentChunk = params.metadata() != null && "parent".equals(params.metadata().get("chunk_role"));

        int storeIndex = -1;
        // #497: allocate a monotonic global graph slot BEFORE the try block so it's
        // in scope for the return. The slot is the sole authoritative node ID used by
        // Hebbian, Temporal, Entity, and Hypergraphs. Never reused.
        int graphSlot = index.allocateGraphSlot();
        try {
            // Step 7b: Add to HNSW index (SEMANTIC only, #445: use monotonic graphSlot)
            if (params.type() == MemoryType.SEMANTIC && semanticIndex != null
                    && !semanticIndex.isReadOnly()
                    && !isParentChunk) {
                storeIndex = graphSlot;
                semanticIndex.add(params.id(), storeIndex, params.vector());
            }

            // Step 9a (moved earlier): Write text.dat FIRST to capture byte position
            var textPos = syncTextIndex(params.id(), params.text(), params.type());

            // Step 8: Register in ID index (with text.dat byte offsets for off-heap reads)
            long textOffset = (textPos != null) ? textPos.textOffset() : -1L;
            int textLength = (textPos != null) ? textPos.textLength() : -1;
            // #443: stamp the colocated partition (activePartitionSeq) so recall, text
            // resolution and direct-resolve can locate this record's partition.
            var location = new MemoryLocation(params.type(), params.offset(), graphSlot,
                    activePartitionSeq, textOffset, textLength);

            if (params.metadata() != null) {
                index.register(params.id(), location,
                        params.text(), params.source(), params.tags(), params.metadata());
            } else {
                index.register(params.id(), location,
                        params.text(), params.source(), params.tags());
            }

            // Step 9: WAL append (encrypt payload when encryption is active)
            wal.appendRemember(params.id(), encryptor.encryptPayload(params.quantized()));

            // Step 9a-splade: SPLADE sparse index (if provider available)
            if (!isParentChunk) {
                syncSpladeIndex(params.id(), params.text());
            }
        } catch (Exception e) {
            log.warn("[Ingestion] Post-write sync failed for '{}', applying compensating tombstone: {}", params.id(), e.getMessage());
            compensateTombstone(params.id());
            throw new com.spectrayan.spector.commons.error.SpectorIngestionException(
                    com.spectrayan.spector.commons.error.ErrorCode.INGESTION_PIPELINE_FAILED,
                    e,
                    "Post-write sync failed: " + e.getMessage());
        }

        return graphSlot;
    }

    private void compensateTombstone(String id) {
        try {
            MemoryLocation loc = index.locate(id);
            if (loc != null) {
                if (cognitiveRouter != null) {
                    cognitiveRouter.tombstone(loc);
                }
                index.remove(id);
            }
        } catch (Exception e) {
            log.error("[Ingestion] Failed to apply compensating tombstone for '{}': {}", id, e.getMessage(), e);
        }
    }

    /**
     * Strengthens Hebbian edges and links temporal chains for co-ingestion.
     *
     * @param memoryIdx   the index of the current memory
     * @param previousIdx the index of the previously ingested memory (-1 if none)
     * @param sessionId   the current session ID
     */
    public void syncGraphEdges(int memoryIdx, int previousIdx, int sessionId) {
        // Step 9b: Hebbian edge strengthening
        if (hebbianGraph != null && previousIdx >= 0 && previousIdx != memoryIdx) {
            try {
                hebbianGraph.strengthen(memoryIdx, previousIdx, 1.0f);
            } catch (RuntimeException e) {
                SpectorHebbianException ex = new SpectorHebbianException("edge strengthening", e);
                log.warn(ex.getMessage());
            }
        }

        // Step 9c: Temporal chain linking (session-local sequence)
        if (temporalChain != null && previousIdx >= 0 && previousIdx != memoryIdx) {
            try {
                temporalChain.link(memoryIdx, previousIdx, sessionId);
            } catch (RuntimeException e) {
                SpectorTemporalChainException ex = new SpectorTemporalChainException("linking", e);
                log.warn(ex.getMessage());
            }
        }
    }

    /**
     * Extracts entities from text and populates the entity graph (Step 9d).
     *
     * @param id        memory ID
     * @param text      memory content
     * @param memoryIdx memory index for entity -> memory linking
     * @return list of extracted entities
     */
    public List<ExtractedEntity> syncEntityExtraction(String id, String text, int memoryIdx) {
        if (entityExtractor != null && entityDirectory != null && entityExtractor.isAvailable()) {
            try {
                List<ExtractedEntity> entities = entityExtractor.extract(id, text);
                populateEntities(entities, memoryIdx, id);
                return entities;
            } catch (RuntimeException e) {
                SpectorEntityGraphException ex = new SpectorEntityGraphException("extraction", e);
                log.warn(ex.getMessage());
            }
        } else if (entityDirectory != null) {
            log.debug("[Ingest] '{}' entity extraction skipped: extractor={}, available={}",
                    id, entityExtractor != null ? entityExtractor.getClass().getSimpleName() : "null",
                    entityExtractor != null && entityExtractor.isAvailable());
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Populates the entity graph with pre-extracted entities (Step 9d override).
     *
     * @param entities  pre-extracted entities
     * @param memoryIdx memory index for entity -> memory linking
     * @param id        memory ID (for logging)
     */
    public void syncPreExtractedEntities(List<ExtractedEntity> entities, int memoryIdx, String id) {
        if (entityDirectory == null) return;
        try {
            populateEntities(entities, memoryIdx, id);
        } catch (RuntimeException e) {
            log.warn(new SpectorEntityGraphException("pre-extracted entity population", e).getMessage());
        }
    }

    /**
     * Applies pre-computed Hebbian edge hints from IngestionContext.
     */
    public void syncHebbianEdgeHints(int memoryIdx, String id,
                              List<com.spectrayan.spector.memory.model.IngestionContext.HebbianEdgeHint> edges) {
        if (hebbianGraph == null) return;
        for (var edgeHint : edges) {
            try {
                MemoryLocation targetLoc = index.locate(edgeHint.targetMemoryId());
                if (targetLoc != null) {
                    int targetIdx = targetLoc.graphSlot() >= 0
                            ? targetLoc.graphSlot()
                            : (int) (targetLoc.offset() / 164);
                    hebbianGraph.strengthen(memoryIdx, targetIdx, edgeHint.weight());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to apply Hebbian edge hint {}  ->  {}: {}",
                        id, edgeHint.targetMemoryId(), e.getMessage());
            }
        }
    }

    /**
     * Applies pre-computed temporal link hints from IngestionContext.
     */
    public void syncTemporalLinkHints(int memoryIdx, String id,
                               List<com.spectrayan.spector.memory.model.IngestionContext.TemporalLinkHint> links) {
        if (temporalChain == null) return;
        for (var linkHint : links) {
            try {
                MemoryLocation predLoc = index.locate(linkHint.predecessorMemoryId());
                if (predLoc != null) {
                    int predIdx = predLoc.graphSlot() >= 0
                            ? predLoc.graphSlot()
                            : (int) (predLoc.offset() / 164);
                    temporalChain.linkNodes(predIdx, memoryIdx, linkHint.sessionId(), (int) (System.currentTimeMillis() / 1000));
                }
            } catch (RuntimeException e) {
                log.warn("Failed to apply temporal link hint {}  ->  {}: {}",
                        id, linkHint.predecessorMemoryId(), e.getMessage());
            }
        }
    }

    public void syncTemporalFacts(List<ExtractedEntity> entities, int memoryIdx, String memoryId, long ingestEpochSec) {
        if (temporalKnowledgeGraph == null || entities == null || entities.isEmpty()) return;
        
        for (ExtractedEntity entity : entities) {
            if (entity.relations() != null) {
                for (var relation : entity.relations()) {
                    int subjectId = entityDirectory.intern(entity.name(), entity.typeName());
                    int objectId = entityDirectory.intern(relation.targetEntityName(), "UNKNOWN");
                    
                    try {
                        temporalKnowledgeGraph.assertFact(
                            subjectId,
                            relation.relationTypeName(),
                            objectId,
                            -1L, (short) 0,
                            ingestEpochSec,
                            Long.MAX_VALUE,
                            0.8f,
                            false
                        );
                    } catch (RuntimeException e) {
                        log.debug("Failed to assert temporal fact for entity '{}' relation '{}': {}",
                                 entity.name(), relation.relationTypeName(), e.getMessage());
                    }
                }
            }
        }
    }

    // ==============================================================
    // PRIVATE HELPERS
    // ==============================================================

    private TextAppendMemory.TextPosition syncTextIndex(
            String id, String text, MemoryType type) {
        TextAppendMemory.TextPosition pos = null;
        if (textDataStore != null) {
            try {
                String textToStore = text;
                if (encryptor.isEnabled()) {
                    byte[] encBytes = encryptor.encryptText(text.getBytes(StandardCharsets.UTF_8));
                    textToStore = java.util.Base64.getEncoder().encodeToString(encBytes);
                }
                pos = textDataStore.write(id, type, textToStore);
            } catch (RuntimeException e) {
                log.warn("Failed to write text.dat entry for '{}': {}", id, e.getMessage());
            }
        }
        if (bm25Index != null && activePartitionIndex >= 0) {
            try {
                bm25Index.index(activePartitionIndex, id, text);
            } catch (RuntimeException e) {
                log.warn("Failed to index '{}' in BM25: {}", id, e.getMessage());
            }
        }
        return pos;
    }

    private void syncSpladeIndex(String id, String text) {
        if (spladeIndex != null && spladeProvider != null && activePartitionIndex >= 0) {
            try {
                SparseEmbeddingResult sparse = spladeProvider.encode(text);
                spladeIndex.index(activePartitionIndex, id, sparse.weights());
            } catch (RuntimeException e) {
                log.warn("Failed SPLADE index for '{}': {}", id, e.getMessage());
            }
        }
    }

    private void populateEntities(List<ExtractedEntity> entities, int memoryIdx, String id) {
        int entitiesAdded = 0;
        int relationsAdded = 0;
        var entityIds = new java.util.ArrayList<Integer>(entities.size());
        // ADR-0003 #456 (P2): the EntityDirectory is the sole entity-id provider and owns
        // entity→memory adjacency (including single-entity memories). No binary edges are written
        // at ingest anymore; topology is carried entirely by the hypergraph below.
        for (ExtractedEntity entity : entities) {
            int eid = entityDirectory.intern(entity.name(), entity.typeName());
            if (eid >= 0) {
                entityDirectory.linkEntityToMemory(eid, memoryIdx);
                entityIds.add(eid);
                entitiesAdded++;
            }
        }
        if (entitiesAdded > 0) {
            log.info("[Ingest] '{}'  ->  {} entities, {} relations added to EntityGraph",
                    id.length() > 60 ? "..." + id.substring(id.length() - 57) : id,
                    entitiesAdded, relationsAdded);

            // Create hyperedge for multi-entity co-occurrence (if >= 2 entities in this memory)
            if (hyperEntityGraph != null && entityIds.size() >= 2) {
                int[] vertexArr = entityIds.stream().mapToInt(Integer::intValue).toArray();
                if (vertexArr.length > HyperEntityLayout.MAX_VERTICES_PER_EDGE) {
                    int[] truncated = new int[HyperEntityLayout.MAX_VERTICES_PER_EDGE];
                    System.arraycopy(vertexArr, 0, truncated, 0, HyperEntityLayout.MAX_VERTICES_PER_EDGE);
                    vertexArr = truncated;
                }
                int[] roles = new int[vertexArr.length];
                // First entity gets SUBJECT role, rest get CONTEXT
                roles[0] = HyperEntityGraphMemory.ROLE_SUBJECT;
                for (int r = 1; r < roles.length; r++) roles[r] = HyperEntityGraphMemory.ROLE_CONTEXT;
                hyperEntityGraph.addHyperedge(vertexArr, roles,
                        0, 1.0f, memoryIdx, System.currentTimeMillis());
            }

            // Create typed binary hyperedges for explicit extracted relations (ADR-0010, #273)
            if (hyperEntityGraph != null) {
                for (ExtractedEntity entity : entities) {
                    if (entity.relations() != null) {
                        for (var relation : entity.relations()) {
                            int subjectId = entityDirectory.intern(entity.name(), entity.typeName());
                            int objectId = entityDirectory.intern(relation.targetEntityName(), "UNKNOWN");
                            int predType = temporalKnowledgeGraph != null && temporalKnowledgeGraph.predicateRegistry() != null
                                    ? temporalKnowledgeGraph.predicateRegistry().intern(relation.relationTypeName())
                                    : 0;
                            int[] vertexArr = new int[]{subjectId, objectId};
                            int[] roles = new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_OBJECT};
                            hyperEntityGraph.addHyperedge(vertexArr, roles, predType, 1.0f, memoryIdx, System.currentTimeMillis());
                            relationsAdded++;
                        }
                    }
                }
            }
        }
    }
}
