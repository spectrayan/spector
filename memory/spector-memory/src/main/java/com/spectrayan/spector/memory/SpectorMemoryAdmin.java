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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.RecallPathway;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
/**
 * Administrative interface for the Spector Cognitive Memory system.
 *
 * <p>Provides access to internal subsystems (WAL, tier router, Hebbian graph,
 * entity graph, temporal chain, quantizer, etc.) for operational monitoring,
 * tuning, and advanced integrations.</p>
 *
 * <p>This interface is <b>not intended for typical SDK consumers</b>.
 * Use {@link SpectorMemory} for the public API (remember, recall, forget, etc.).
 * Access this via {@link SpectorMemory#admin()}.</p>
 *
 * @since 1.0.0
 * @see SpectorMemory
 */
public interface SpectorMemoryAdmin {

    // ══════════════════════════════════════════════════════════════
    // INGESTION TARGET
    // ══════════════════════════════════════════════════════════════

    /** Returns the cognitive ingestion target for use with the unified IngestionPipeline. */
    RememberPathway target();

    /** Returns the cognitive ingestion target. */
    RememberPathway rememberPathway();

    // ══════════════════════════════════════════════════════════════
    // SUBSYSTEM ACCESSORS
    // ══════════════════════════════════════════════════════════════

    /** Returns the Hebbian co-activation tracker. */
    CoActivationRecordMemory coActivation();

    /** Returns the Write-Ahead Log. */
    MemoryWal wal();

    /** Returns the prospective memory scheduler. */
    ProspectiveScheduler prospective();

    /** Returns the namespace-scoped background task scheduler and audit manager. */
    com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler();

    /** Returns the suppression set. */
    SuppressionSet suppression();

    /** Returns the habituation penalty tracker. */
    HabituationPenalty habituation();

    /** Returns the scalar quantizer used for vector compression. */
    ScalarQuantizer quantizer();

    /** Returns the recall pathway. */
    RecallPathway recallPathway();

    /** Returns the cognitive memory router (Working, Episodic, Semantic, Procedural). */
    CognitiveMemoryRouter cognitiveRouter();



    /** Returns the memory index. */
    MemoryIndex index();

    /** Returns the lateral (neurodivergent) evaluator. */
    LateralEvaluator lateralEvaluator();

    // ══════════════════════════════════════════════════════════════
    // GRAPH SUBSYSTEM
    // ══════════════════════════════════════════════════════════════

    /** Returns the Temporal Knowledge Graph. */
    TemporalKnowledgeGraph temporalKnowledgeGraph();

    /** Returns the cognitive graph facade for high-level graph queries. */
    CognitiveGraphFacade graph();

    /** Returns the hyperentity graph memory. */
    com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph();

    /**
     * Returns the entity identity directory — the name&harr;id index, per-entity type, and the
     * authoritative entity&rarr;memory adjacency (including single-entity memories). This is the
     * graduated replacement for identity access (ADR-0003, #455/#456).
     * May be {@code null} when entity extraction is disabled.
     */
    EntityDirectory entityDirectory();

    /**
     * Returns the Insular Cortex self-model store.
     */
    com.spectrayan.spector.memory.insula.InsularCortex insularCortex();

    /**
     * Returns the background graph enrichment daemon (may be null if disabled).
     */
    com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon graphEnricher();

    // ══════════════════════════════════════════════════════════════
    // OPERATIONAL
    // ══════════════════════════════════════════════════════════════

    /** Explicitly decays importance of old episodic memories. */
    int decay(Duration olderThan, float factor);

    // ══════════════════════════════════════════════════════════════
    // VACUUM / COMPACTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Vacuums (compacts) a specific memory tier by removing tombstoned records.
     *
     * <p>Copies only live records to a new segment, updates the index,
     * and reclaims space. The operation is coordinated with writers via explicit locks.</p>
     *
     * @param tier the memory tier to compact
     * @return compaction result with statistics, or null if no compaction needed
     */
    CompactionResult vacuum(MemoryType tier);

    /**
     * Returns the tombstone ratio for each memory tier.
     *
     * @return map of tier → tombstone ratio (0.0 to 1.0)
     */
    Map<MemoryType, Float> tombstoneRatios();

    /**
     * Returns all active (not tombstoned) cognitive records without vectors.
     */
    List<CognitiveRecord> listAll();

    /**
     * Returns active cognitive records for a specific tier, sorted by timestamp descending, paginated.
     */
    List<CognitiveRecord> listAll(MemoryType tier, int offset, int limit);

    /**
     * Returns the underlying semantic vector index.
     */
    VectorIndex semanticIndex();
}
