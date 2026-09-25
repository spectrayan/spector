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
package com.spectrayan.spector.memory;
import com.spectrayan.spector.kernel.store.CoActivationMemory;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.GraphEnrichmentEngine;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.scheduler.MemoryScheduler;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

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
    // REMEMBER PATHWAY
    // ══════════════════════════════════════════════════════════════

    /** Returns the cognitive remember pathway. */
    RememberPathway rememberPathway();

    // ══════════════════════════════════════════════════════════════
    // SUBSYSTEM ACCESSORS
    // ══════════════════════════════════════════════════════════════

    /** Returns the Hebbian co-activation tracker. */
    CoActivationMemory coActivation();

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

    /** Returns the partition registry view, or {@code null} if unpartitioned. */
    default com.spectrayan.spector.memory.cortex.PartitionRegistry partitionRegistry() {
        return null;
    }

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
    com.spectrayan.spector.kernel.store.HyperEntityGraphMemory hyperEntityGraph();

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
    com.spectrayan.spector.kernel.store.InsulaMemory insularCortex();

    /**
     * Returns the background graph enrichment engine (may be null if disabled).
     */
    GraphEnrichmentEngine graphEnricher();

    // ══════════════════════════════════════════════════════════════
    // OPERATIONAL
    // ══════════════════════════════════════════════════════════════

    /**
     * Conducts a direct kernel reflection sweep using the supplied specification.
     *
     * @param spec sweep configuration and filter parameters
     * @return resulting reflection report
     */
    default ReflectReport reflectKernel(ReflectSweepSpec spec) {
        return ReflectReport.empty();
    }

    /**
     * Returns the active reflection pathway kernel if configured.
     */
    default ReflectPathway reflectPathway() {
        return null;
    }

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
     * Compacts a tier store with explicit force flag.
     *
     * @param tier  the memory tier to compact
     * @param force if true, compacts unconditionally when tombstones exist
     * @return compaction result with statistics, or null if no compaction needed
     */
    default CompactionResult vacuum(MemoryType tier, boolean force) {
        return vacuum(tier);
    }

    /**
     * Surveys a tier store for tombstones without relocating records (census only).
     *
     * @param tier the memory tier to survey
     * @return census result with statistics, or null if no tombstones exist
     */
    default CompactionResult survey(MemoryType tier) {
        return vacuum(tier, false);
    }

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
