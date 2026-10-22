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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.concurrent.SpectorExecutors;
import com.spectrayan.spector.commons.concurrent.ThreadPlane;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import com.spectrayan.spector.config.properties.CircadianProperties;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.api.*;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.kernel.store.*;
import com.spectrayan.spector.memory.api.CognitiveProfileConfig;
import com.spectrayan.spector.memory.api.ImportanceEstimator;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.bootstrap.SpectorMemoryFactory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.consolidation.AbstractConsolidator;
import com.spectrayan.spector.memory.cortex.consolidation.ProvenanceKeys;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.cortex.consolidation.BatchConsolidator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryInsight;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.cortex.prospective.Reminder;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.GraphEnrichmentEngine;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.model.*;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.pathway.decide.DecidePathway;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractionResult;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractors;
import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.pathway.express.ExpressPathway;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReflectPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReinforcementHandler;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.wander.WanderPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.persist.PersistenceManager;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import com.spectrayan.spector.memory.sync.CheckpointEngine;
import com.spectrayan.spector.memory.sync.CompactionResult;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.VacuumCompactor;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.embedding.PipelineEmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default implementation of {@link SpectorMemory}  --  the Zero-GC Cognitive Backbone for Autonomous Agents.
 *
 * <h3>Subsystem Orchestration</h3>
 * <p>{@code DefaultSpectorMemory} composes and delegates to focused subsystems:</p>
 * <ul>
 *   <li>{@link RememberPathway}  --  multi-stage ingest (embed  ->  quantize  ->  route  ->  WAL)</li>
 *   <li>{@link RecallPathway}  --  multi-stage recall (embed  ->  score  ->  filter  ->  sort)</li>
 *   <li>{@link PartitionManager}  --  DISK partition discovery, creation, and rolling</li>
 *   <li>{@link ImportanceEstimator}  --  read-only novelty/ICNU/flashbulb pipeline</li>
 *   <li>{@link ReflectPathway}  --  sleep consolidation, graph decay, cross-layer promotion</li>
 *   <li>{@link ReinforcementHandler}  --  valence, LTP, ACT-R, Two-Factor, ICNU re-fusion</li>
 *   <li>{@link PersistenceManager}  --  flush-on-close and resource cleanup</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 *   var memory = DefaultSpectorMemory.builder()
 *       .dimensions(768)
 *       .embeddingProvider(ollamaProvider)
 *       .persistence(Path.of("/data/agent-memory"))
 *       .build();
 *
 *   memory.remember("user-pref", "User prefers dark mode.",
 *       MemoryType.SEMANTIC, MemorySource.USER_STATED, "ui", "preferences").join();
 *
 *   List<CognitiveResult> results = memory.recall("what theme?",
 *       RecallOptions.builder().topK(5).synapticFilter("preferences").build());
 * }</pre>
 */
public final class DefaultSpectorMemory implements SpectorMemory, SpectorMemoryAdmin {

    private static final Logger log = LoggerFactory.getLogger(DefaultSpectorMemory.class);

    //  Core Subsystems (Façade composition)
    private final RememberPathway rememberPathway;   // #561 relay-based remember engine (nullable)
    private final EmbeddingProvider embeddingProvider;
    private final RecallPathway recallPathway;       // #561 relay-based engine
    private final ReflectPathway reflectPathway;     // #503 relay-based sleep reflection engine
    private final ExpressPathway expressPathway;     // #602 relay-based express engine
    private final DreamPathway dreamPathway;         // #679 relay-based generative dreaming & thought experiment engine
    private final com.spectrayan.spector.memory.pathway.skill.SkillPathway skillPathway; // ADR-0086 procedural skill crystallization
    private final MemoryIndex index;
    private final ScalarQuantizer quantizer;

    //  Extracted Strategy/Handler Components 
    private final PartitionManager partitionManager;     // owns volatile cognitiveRouter
    private final ImportanceProvider importanceProvider;
    private final ReinforcementHandler reinforcementHandler;
    private final BatchConsolidator batchConsolidator;
    private final com.spectrayan.spector.memory.cortex.consolidation.EagerConsolidator eagerConsolidator;
    private final com.spectrayan.spector.memory.scheduler.MemoryScheduler memoryScheduler;

    //  Biological Subsystems 
    private final ValenceTracker valenceTracker;
    private final CoActivationMemory coActivationTracker;
    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;
    private final ProspectiveScheduler prospectiveScheduler;
    private final MemoryIntrospector introspector;
    private final LateralEvaluator lateralEvaluator;
    private final MemoryWal wal;

    //  3-Layer Cognitive Graph 
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final CognitiveGraphFacade graphFacade;

    //  Configuration 
    private final int dimensions;
    private final MemoryPersistenceMode persistenceMode;
    private final Path persistencePath;
    private final CircadianProperties circadianPolicy;
    private final com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor reflectSweepExecutor;
    private final CognitiveProfileConfig profileConfig;
    private volatile RecallOptions defaultRecallOptions;
    private volatile com.spectrayan.spector.config.model.LiveMemoryPatch liveMemoryPatch;

    //  Multi-Tenant Namespace 
    private final SpectorNamespaceManager namespaceManager;
    private final String namespaceId;

    /**
     * The host's deletion/write policy. Never null — {@code ALLOW_ALL} when none was installed, so no call
     * site needs a null check and none can skip the policy by forgetting one.
     */
    private final com.spectrayan.spector.memory.policy.MutationPolicy mutationPolicy;
    private final com.spectrayan.spector.memory.sync.QuiesceGuard quiesceGuard;
    private final java.util.concurrent.atomic.AtomicInteger activeLeases = new java.util.concurrent.atomic.AtomicInteger(0);
    private final boolean sharedPathways;

    //  ID Generation 
    private final MemoryIdGenerator idGenerator;

    //  Circadian trigger counter 
    private final AtomicInteger episodicIngestCount = new AtomicInteger(0);

    //  Automatic Checkpointing & Graph Enrichment
    private final CheckpointEngine checkpointEngine;
    private final GraphEnrichmentEngine graphEnrichmentEngine;

    //  Shutdown Hook (auto-registered for DISK mode) 
    private final Thread shutdownHook;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    //  BM25 Binary Persistence (P1: save on close for instant next startup) 
    private final MemoryBM25Index bm25Index;

    //  Forgotten-but-not-purged locations, durable across restarts (DISK mode only; null otherwise) 
    private final com.spectrayan.spector.memory.persist.TombstoneLedger tombstoneLedger;

    //  Chunking for remember() 
    private final com.spectrayan.spector.commons.chunker.TextChunker chunker;
    private volatile com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig;
    private final ParallelEmbeddingPipeline parallelPipeline;
    private final EmbedConfig embedConfig;

    //  Multimodal Attachment Processing 
    private final com.spectrayan.spector.memory.pathway.pipeline.AttachmentProcessor attachmentProcessor;

    //  Contextual Bandit (ProfileAdaptor) 
    private final ProfileAdaptor profileAdaptor;

    //  Semantic Index Reference 
    private final com.spectrayan.spector.index.VectorIndex semanticIndex;

    // Runtime Bundle & Insular Cortex
    private final RuntimeBundle runtimeBundle;
    private final com.spectrayan.spector.kernel.store.InsulaMemory insularCortex;
    private final WanderPathway wanderPathway;
    private final com.spectrayan.spector.kernel.store.ContinuityMemory continuityMemory;
    private final com.spectrayan.spector.kernel.store.ProvenanceMemory provenanceMemory;
    private final DecidePathway decidePathway;
    private final com.spectrayan.spector.memory.aisme.AismeBundle aismeBundle;

    private final com.spectrayan.spector.memory.session.SessionBufferManager sessionBufferManager = new com.spectrayan.spector.memory.session.SessionBufferManager();

    // ── Episodic Conversation Architecture (ADR-0006) ──
    private final EpisodicSessionIndex episodicSessionIndex;
    private final EpisodicMemory episodicMemory;

    private final MemoryObservationHook hook;
    private final com.spectrayan.spector.memory.index.IndexPlaneCoordinator indexPlaneCoordinator;
    private final com.spectrayan.spector.memory.index.IndexReconcileEngine indexReconcileEngine;

    DefaultSpectorMemory(SpectorMemoryBuilder builder) {
        var bundle = SpectorMemoryFactory.assemble(builder);
        this.indexPlaneCoordinator = bundle.indexPlaneCoordinator();
        this.rememberPathway = bundle.rememberPathway();
        this.reflectPathway = bundle.reflectPathway();
        this.expressPathway = bundle.expressPathway();
        this.embeddingProvider = bundle.embeddingProvider();
        this.recallPathway = bundle.recallPathway();
                this.index = bundle.index();
        this.quantizer = bundle.quantizer();
        this.partitionManager = bundle.partitionManager();
        this.importanceProvider = bundle.importanceProvider();
        this.reinforcementHandler = bundle.reinforcementHandler();
        var memProps = builder.properties() != null && builder.properties().memory() != null
                ? builder.properties().memory()
                : new com.spectrayan.spector.config.properties.MemoryProperties();
        var remProps = memProps.getRemember() != null
                ? memProps.getRemember()
                : new com.spectrayan.spector.config.properties.RememberProperties();
        var consProps = memProps.getConsolidation() != null
                ? memProps.getConsolidation()
                : new com.spectrayan.spector.config.properties.ConsolidationProperties();
        var aismeConfig = com.spectrayan.spector.config.properties.AismeProperties.fromProperties(
                memProps.getAisme());
 
        this.batchConsolidator = new BatchConsolidator(builder.llmProvider(), this.embeddingProvider);
        this.eagerConsolidator = new com.spectrayan.spector.memory.cortex.consolidation.EagerConsolidator(
                builder.namespaceId(),
                bundle.partitionManager().cognitiveRouter(),
                bundle.index(),
                bundle.quantizer(),
                bundle.entityDirectory(),
                bundle.hyperEntityGraph(),
                bundle.temporalKnowledgeGraph(),
                builder.llmProvider(),
                this.embeddingProvider,
                this::inspect,
                remProps.getDeduplicationRadius(),
                consProps.getEagerQueueCapacity()
        );

        this.valenceTracker = bundle.valenceTracker();
        this.coActivationTracker = bundle.coActivationTracker();
        this.suppressionSet = bundle.suppressionSet();
        this.habituationPenalty = bundle.habituationPenalty();
        this.prospectiveScheduler = bundle.prospectiveScheduler();
        this.introspector = bundle.introspector();
        this.lateralEvaluator = bundle.lateralEvaluator();
        this.wal = bundle.wal();
        this.hebbianGraph = bundle.hebbianGraph();
        this.temporalChain = bundle.temporalChain();
        this.temporalKnowledgeGraph = bundle.temporalKnowledgeGraph();
        this.entityDirectory = bundle.entityDirectory();
        this.hyperEntityGraph = bundle.hyperEntityGraph();
        this.graphFacade = bundle.graphFacade();
        if (this.coActivationTracker != null && this.index != null && this.coActivationTracker.invertedIndexEntryCount() == 0) {
            java.util.Map<Integer, java.util.Collection<String>> tagMap = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, com.spectrayan.spector.kernel.api.MemoryLocation> entry : this.index.locationMap().entrySet()) {
                String entryId = entry.getKey();
                int slot = entry.getValue().graphSlot();
                String[] memoryTags = this.index.tags(entryId);
                if (slot >= 0 && memoryTags != null && memoryTags.length > 0) {
                    tagMap.put(slot, java.util.Arrays.asList(memoryTags));
                }
            }
            if (!tagMap.isEmpty()) {
                this.coActivationTracker.rebuildInvertedIndex(tagMap);
            }
        }
        this.dimensions = memProps.getDimensions();
        this.persistenceMode = builder.persistenceMode();
        this.persistencePath = builder.persistencePath();
        this.circadianPolicy = memProps.getCircadian() != null ? memProps.getCircadian() : new CircadianProperties();
        String orchestratorName = memProps.getCircadian() != null ? memProps.getCircadian().getOrchestrator() : null;
        this.reflectSweepExecutor = builder.reflectSweepExecutor() != null
                ? builder.reflectSweepExecutor()
                : com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutors.getExecutor(orchestratorName);
        this.profileConfig = builder.profileConfig();
        this.defaultRecallOptions = builder.defaultRecallOptions() != null ? builder.defaultRecallOptions() : RecallOptions.DEFAULT;
        this.liveMemoryPatch = com.spectrayan.spector.config.model.LiveMemoryPatch.from(memProps);
        this.namespaceManager = bundle.namespaceManager();
        this.namespaceId = builder.namespaceId();
        this.mutationPolicy = builder.mutationPolicy();
        this.quiesceGuard = builder.quiesceGuard() != null
                ? builder.quiesceGuard()
                : new com.spectrayan.spector.memory.sync.QuiesceGuard();
        this.idGenerator = bundle.idGenerator();
        this.checkpointEngine = bundle.checkpointEngine();
        if (this.checkpointEngine != null) {
            this.checkpointEngine.setRouterSupplier(partitionManager::cognitiveRouter);
            this.checkpointEngine.setQuiesceGuard(this.quiesceGuard);
        }
        this.graphEnrichmentEngine = bundle.graphEnrichmentEngine();
        this.bm25Index = bundle.bm25Index();
        this.chunker = builder.chunker();
        this.chunkConfig = builder.chunkConfig();
        this.parallelPipeline = bundle.parallelPipeline();
        this.embedConfig = bundle.embedConfig();
        this.attachmentProcessor = bundle.attachmentProcessor();
        this.profileAdaptor = bundle.profileAdaptor();
        this.semanticIndex = builder.semanticIndex();
        this.runtimeBundle = bundle.runtimeBundle();
        this.insularCortex = bundle.insularCortex();
        this.wanderPathway = bundle.wanderPathway();
        this.continuityMemory = bundle.continuityMemory();
        this.provenanceMemory = bundle.provenanceMemory();
        this.batchConsolidator.attachProvenance(this.provenanceMemory);
        this.decidePathway = bundle.decidePathway();
        this.dreamPathway = bundle.dreamPathway();
        this.skillPathway = com.spectrayan.spector.memory.pathway.skill.SkillPathway.standard();
        this.aismeBundle = bundle.aismeBundle();
        this.hook = builder.hook() != null ? builder.hook() : MemoryObservationHook.NOOP;
        this.sharedPathways = builder.sharedPathways();

        var spladeIdx = this.indexPlaneCoordinator != null 
                ? this.indexPlaneCoordinator.get("SPLADE")
                        .filter(com.spectrayan.spector.memory.cortex.MemorySpladeIndex.class::isInstance)
                        .map(com.spectrayan.spector.memory.cortex.MemorySpladeIndex.class::cast)
                        .orElse(null)
                : null;
        this.indexReconcileEngine = new com.spectrayan.spector.memory.index.IndexReconcileEngine(
                this.entityDirectory,
                this.index,
                this.bm25Index,
                spladeIdx,
                this.hyperEntityGraph
        );

        // Wire quarantine filter into hypergraph for traversal isolation (Phase 2.1, #946)
        if (this.hyperEntityGraph != null && this.indexReconcileEngine.quarantineRegistry() != null) {
            this.hyperEntityGraph.setQuarantineFilter(
                    edgeId -> this.indexReconcileEngine.quarantineRegistry().isQuarantined(edgeId)
            );
        }

        // Bind reconcile engine to coordinator for quarantine admin endpoints (Phase 2.1, #946)
        if (this.indexPlaneCoordinator != null) {
            this.indexPlaneCoordinator.bindReconcileEngine(this.indexReconcileEngine);
        }

        //  Quartz Memory Scheduler (In-Memory Multi-Tenant Background Scheduling & Auditing)
        if (builder.scheduler() != null) {
            this.memoryScheduler = builder.scheduler();
        } else {
            this.memoryScheduler = com.spectrayan.spector.memory.scheduler.QuartzMemoryScheduler.builder()
                    .namespaceId(this.namespaceId)
                    .reflectAction(() -> this.reflect())
                    .circadianPolicy(circadianPolicy)
                    .dreamPathway(this.dreamPathway)
                    .partitionManager(this.partitionManager)
                    .aismeConfig(aismeConfig)
                    .checkpointEngine(this.checkpointEngine)
                    .graphEnrichmentEngine(this.graphEnrichmentEngine)
                    .dmnDaemon((this.wanderPathway != null && aismeConfig != null && aismeConfig.enabled() && aismeConfig.enableDmnSpontaneous())
                            ? new com.spectrayan.spector.memory.aisme.dmn.DmnSpontaneousDaemon(this.wanderPathway, this.partitionManager, System::currentTimeMillis) : null)
                    .decayDaemon((bundle.aismeBundle() != null && aismeConfig != null && aismeConfig.backgroundDecayEnabled())
                            ? new com.spectrayan.spector.memory.aisme.dmn.HomeostaticDecayDaemon(
                                    bundle.aismeBundle().mentalStateTracker(),
                                    bundle.aismeBundle().homeostaticCore(),
                                    aismeConfig.backgroundDecayFactor()) : null)
                    .checkpointIntervalSeconds(memProps.getCheckpointIntervalSeconds())
                    .indexReconcileEngine(this.indexReconcileEngine)
                    .suppliedExecutor(builder.suppliedExecutor())
                    .quartzScheduler(builder.customQuartzScheduler())
                    .build();
        }

        //  Tombstone ledger (DISK mode only): restore forgotten-but-not-purged locations so purge can
        //  still find them after a restart.
        if (persistenceMode == MemoryPersistenceMode.DISK && bundle.basePath() != null) {
            this.tombstoneLedger = new com.spectrayan.spector.memory.persist.TombstoneLedger(bundle.basePath());
            var restored = tombstoneLedger.load();
            restored.forEach(index::restoreTombstone);
            if (!restored.isEmpty()) {
                log.info("Restored {} tombstoned location(s) awaiting purge from {}",
                        restored.size(), tombstoneLedger.file());
            }
            persistTombstones();
        } else {
            this.tombstoneLedger = null;
        }

        //  JVM Shutdown Hook  (DISK mode only)
        if (persistenceMode == MemoryPersistenceMode.DISK && bundle.basePath() != null) {
            if (!builder.managedByRegistry()) {
                this.shutdownHook = new Thread(() -> {
                    if (closed.compareAndSet(false, true)) {
                        log.info("JVM shutdown hook: flushing SpectorMemory...");
                        doClose();
                        log.info("JVM shutdown hook: SpectorMemory flushed successfully");
                    }
                }, "spector-memory-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            } else {
                this.shutdownHook = null;
            }
        } else {
            this.shutdownHook = null;
        }

        // ── Episodic Session Index rebuild (ADR-0006) ──
        this.episodicMemory = partitionManager.cognitiveRouter().episodic();
        this.episodicSessionIndex = new EpisodicSessionIndex();
        if (episodicMemory != null) {
            int liveRecords = episodicMemory.rebuildSessionIndex(episodicSessionIndex);
            log.info("Episodic session index rebuilt: {} live records, {} sessions",
                    liveRecords, episodicSessionIndex.sessionCount());
        }
    }

    // ==============================================================
    // INGESTION TARGET  --  for unified IngestionPipeline
    // ==============================================================


    /** Returns the embedding provider for reconsolidation/update operations. */
    public EmbeddingProvider embeddingProvider() {
        return embeddingProvider;
    }

    // ==============================================================
    // CORE API  --  remember / recall / forget / reflect
    // ==============================================================

    @Override
    public void remember(String id, String text, MemoryType type,
                                              MemorySource source, String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.remember(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        remember(id, text, type, source,
                (com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints) null, tags);
            }
    }

    @Override
    public void remember(String id, String text, MemoryType type,
                                              MemorySource source,
                                              com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints hints,
                                              String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.remember(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            if (shouldChunk(text)) {
                rememberChunked(id, text, type, source, hints, null, tags);
            } else {
                String[] finalTags = tags;
                var finalHints = hints;
                var extraction = extractTagsFor(id, text, tags);
                if (extraction != null) {
                    finalTags = mergeTags(tags, extraction.tags());
                    finalHints = RememberHints.withAppraisedAffect(
                            hints, extraction.valence(), extraction.arousal());
                }
                float[] vector = embeddingProvider.embed(text).vector();
                String sessionId = MemoryScope.sessionId();
                if (sessionId != null) {
                    sessionBufferManager.add(sessionId, id, text, vector, type, System.currentTimeMillis());
                }
                rememberPathway.ingestCognitive(id, text, vector, type, finalTags, source, finalHints);
                
            }
            checkCircadianTrigger(type);
            if (eagerConsolidator != null && (type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL)) {
                eagerConsolidator.submit(id, type);
            }
        } catch (RuntimeException e) {
            if (isLlmAppraisalFailure(e)) {
                throw e; // already carries SPE-310-029 and a clear message; don't bury it in SPE-600-003
            }
            log.error("Failed to remember '{}': {}", id, e.getMessage(), e);
            throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void remember(String id, String text, MemoryType type,
                                              String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.remember(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        remember(id, text, type, MemorySource.OBSERVED, tags);
            }
    }

    @Override
    public String remember(String text, MemoryType type,
                                               MemorySource source, String... tags) {
        String generatedId = idGenerator.generate();
        remember(generatedId, text, type, source, tags);
        return generatedId;
    }

    @Override
    public String remember(String text, MemoryType type,
                                               MemorySource source,
                                               com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints hints,
                                               String... tags) {
        String generatedId = idGenerator.generate();
        remember(generatedId, text, type, source, hints, tags);
        return generatedId;
    }

    @Override
    public String remember(String text, MemoryType type,
                                               MemorySource source,
                                               RememberContext context,
                                               String... tags) {
        String generatedId = idGenerator.generate();
        remember(generatedId, text, type, source, context, tags);
        return generatedId;
    }

    @Override
    public void remember(String id, String text, MemoryType type,
                                              MemorySource source,
                                              RememberContext context,
                                              String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.remember(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            if (shouldChunk(text)) {
                rememberChunked(id, text, type, source, null, context, tags);
            } else {
                String[] finalTags = tags;
                var finalContext = context;
                var extraction = extractTagsFor(id, text, tags);
                if (extraction != null) {
                    finalTags = mergeTags(tags, extraction.tags());
                    finalContext = withAppraisedAffect(context, extraction);
                }
                float[] vector = embeddingProvider.embed(text).vector();
                rememberPathway.ingestCognitive(id, text, vector, type, finalTags, source, finalContext);
                
            }

            // Process attachments if present in context metadata
            if (context != null && context.hasAttachments()) {
                processAttachments(id, context, type, source, tags);
            }

            checkCircadianTrigger(type);
            if (eagerConsolidator != null && (type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL)) {
                eagerConsolidator.submit(id, type);
            }
        } catch (RuntimeException e) {
            if (isLlmAppraisalFailure(e)) {
                throw e; // already carries SPE-310-029 and a clear message; don't bury it in SPE-600-003
            }
            log.error("Failed to remember '{}': {}", id, e.getMessage(), e);
            throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void remember(String id, String text, float[] vector, MemoryType type,
                         MemorySource source,
                         RememberContext context,
                         String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.remember(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            String[] finalTags = tags;
            var finalContext = context;
            var extraction = extractTagsFor(id, text, tags);
            if (extraction != null) {
                finalTags = mergeTags(tags, extraction.tags());
                finalContext = withAppraisedAffect(context, extraction);
            }
            float[] finalVector = (vector != null && vector.length > 0)
                    ? vector
                    : embeddingProvider.embed(text).vector();
            String sessionId = MemoryScope.sessionId();
            if (sessionId != null) {
                sessionBufferManager.add(sessionId, id, text, finalVector, type, System.currentTimeMillis());
            }
            rememberPathway.ingestCognitive(id, text, finalVector, type, finalTags, source, finalContext);

            // Process attachments if present in context metadata
            if (context != null && context.hasAttachments()) {
                processAttachments(id, context, type, source, tags);
            }

            checkCircadianTrigger(type);
            if (eagerConsolidator != null && (type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL)) {
                eagerConsolidator.submit(id, type);
            }
        } catch (RuntimeException e) {
            if (isLlmAppraisalFailure(e)) {
                throw e; // already carries SPE-310-029 and a clear message; don't bury it in SPE-600-003
            }
            log.error("Failed to remember with vector '{}': {}", id, e.getMessage(), e);
            throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void remember(String id, String text, float[] vector, MemoryType type,
                         MemorySource source,
                         String... tags) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        remember(id, text, vector, type, source, (RememberContext) null, tags);
            }
    }

    /**
     * Checks if episodic ingestion volume has reached the circadian trigger threshold.
     * If so, fires a background reflection cycle.
     */
    private void checkCircadianTrigger(MemoryType type) {
        if (type == MemoryType.EPISODIC) {
            int count = episodicIngestCount.incrementAndGet();
            if (count >= circadianPolicy.volumeTrigger()) {
                episodicIngestCount.set(0);
                ConcurrentTasks.fireAndForget(ThreadPlane.PLATFORM_WRITER, "writer-reflect-" + namespaceId, null, namespaceId, () -> {
                    log.info("Circadian volume trigger: {} episodic memories  ->  auto-reflect", count);
                    reflect();
                });
            }
        }
    }

    // ==============================================================
    // EPISODIC CONVERSATION API  (ADR-0006)
    // ==============================================================

    /**
     * Appends a conversation turn to the episodic log.
     *
     * <p><b>Lightweight ingestion path:</b> bypasses embedding, quantization,
     * surprise detection, importance scoring, BM25, SPLADE, Hebbian,
     * entity extraction, and temporal chain. Only writes the 64B episodic
     * header + CBOR body to the log-structured mmap region, updates the
     * in-memory session index, and fires the circadian trigger.</p>
     *
     * @param role        conversation role (USER, ASSISTANT, SYSTEM, etc.)
     * @param sequenceId  monotonic turn counter per session
     * @param timestampMs epoch milliseconds
     * @param sessionId   8B TSID hash identifying the conversation session
     * @param body        raw CBOR body bytes (serialized by the synapse layer)
     * @param modelId     LLM model registry ID
     * @param tokenIn     input token count
     * @param tokenOut    output token count
     * @param latencyMs   response generation latency in milliseconds
     * @param userId      user/tenant 8B TSID hash
     * @param soulVersion agent soul configuration version
     * @param modality    source modality (TEXT, IMAGE, AUDIO, VIDEO)
     * @return the byte offset of the written record
     * @throws IllegalStateException if the episodic log store is not available
     */
    public long rememberEpisodic(ConversationRole role, int sequenceId,
                                  long timestampMs, long sessionId,
                                  byte[] body, short modelId,
                                  int tokenIn, int tokenOut,
                                  int latencyMs, long userId,
                                  short soulVersion, SourceModality modality) {
        if (episodicMemory == null) {
            throw new com.spectrayan.spector.commons.error.SpectorMemoryException(
                    com.spectrayan.spector.commons.error.ErrorCode.MEMORY_PATHWAY_FAILED, "Episodic store not available (store missing)");
        }

        SalienceProfile profile = salienceProfile();
        byte rawValence = (byte) 0;
        byte rawArousal = (byte) 0;
        byte valence = (profile != null) ? profile.modulateValence(rawValence) : rawValence;
        byte arousal = (profile != null) ? profile.modulateArousal(rawArousal) : rawArousal;

        float importance = 5.0f;
        if (importanceProvider != null) {
            try {
                String text = (body != null) ? new String(body, java.nio.charset.StandardCharsets.UTF_8) : "";
                importance = (float) importanceProvider.score(
                        new com.spectrayan.spector.memory.model.ImportanceContext(
                                text, new float[0], null, profile, MemoryType.EPISODIC, 0.0f, 0.0, false)
                ).importance();
                if (importance <= 0.0f) {
                    importance = 5.0f;
                }
            } catch (Exception e) {
                importance = 5.0f;
            }
        }

        long offset = episodicMemory.appendTurn(role, sequenceId,
                timestampMs, sessionId, body, modelId,
                tokenIn, tokenOut, latencyMs, userId,
                soulVersion, modality, importance, valence, arousal,
                com.spectrayan.spector.kernel.api.EngramSource.EXPERIENCED);

        // Update session index
        episodicSessionIndex.appendTurn(sessionId, offset);

        // Fire circadian trigger (episodic volume → auto-reflect)
        checkCircadianTrigger(MemoryType.EPISODIC);

        log.debug("Episodic turn appended: session={}, seq={}, role={}, offset={}, importance={}",
                Long.toHexString(sessionId), sequenceId, role, offset, importance);
        return offset;
    }

    /**
     * Reads paginated conversation turns for a session.
     *
     * @param sessionId 8B TSID hash
     * @param offset    zero-based start index
     * @param limit     maximum number of turns to return
     * @return list of decoded episodic records with CBOR bodies
     */
    public List<EpisodeRecord> browseEpisodic(long sessionId, int offset, int limit) {
        if (episodicMemory == null) {
            return List.of();
        }
        List<Long> offsets = episodicSessionIndex.paginate(sessionId, offset, limit);
        return episodicMemory.readTurns(offsets, true);
    }

    /**
     * Returns the last N turns for a session (for LLM context window assembly).
     *
     * @param sessionId 8B TSID hash
     * @param count     number of recent turns
     * @return list of decoded episodic records
     */
    public List<EpisodeRecord> tailEpisodic(long sessionId, int count) {
        if (episodicMemory == null) {
            return List.of();
        }
        List<Long> offsets = episodicSessionIndex.tailTurns(sessionId, count);
        return episodicMemory.readTurns(offsets, true);
    }

    /**
     * Returns the session index for external query by the synapse layer.
     */
    public EpisodicSessionIndex episodicSessionIndex() {
        return episodicSessionIndex;
    }

    /**
     * Runs the configured tag extractor, including the LLM appraisal of valence and arousal when the
     * extractor provides one (#1137).
     *
     * @return the extraction result, or {@code null} when no extractor is configured
     */
    private TagExtractionResult extractTags(String id, String text) {
        var tagExtractor = rememberPathway.tagExtractor();
        return tagExtractor != null ? tagExtractor.extractWithContext(id, text) : null;
    }

    /**
     * Runs the tag extractor for a remember call. Without caller tags the extractor always runs. With
     * caller tags only an appraising extractor ({@link com.spectrayan.spector.memory.pathway.pipeline.TagExtractor#appraises()}, e.g. the LLM one)
     * runs, so caller bookkeeping tags no longer switch off the valence/arousal appraisal.
     *
     * @return the extraction result, or {@code null} when extraction is skipped or unavailable
     */
    private TagExtractionResult extractTagsFor(String id, String text, String[] callerTags) {
        if (callerTags == null || callerTags.length == 0) {
            return extractTags(id, text);
        }
        var tagExtractor = rememberPathway.tagExtractor();
        if (tagExtractor == null || !tagExtractor.appraises()) {
            return null;
        }
        return tagExtractor.extractWithContext(id, text);
    }

    /**
     * Merges caller tags with extracted tags: caller tags first, then extracted ones, blank values and
     * duplicates removed (case-sensitive, first occurrence wins).
     */
    static String[] mergeTags(String[] callerTags, String[] extractedTags) {
        var merged = new java.util.LinkedHashSet<String>();
        for (String[] source : new String[][] {callerTags, extractedTags}) {
            if (source == null) {
                continue;
            }
            for (String tag : source) {
                if (tag != null && !tag.isBlank()) {
                    merged.add(tag.trim());
                }
            }
        }
        return merged.toArray(String[]::new);
    }

    /**
     * Folds an appraised valence and arousal into a context's hints. Values the caller supplied win.
     */
    private static RememberContext withAppraisedAffect(RememberContext context, TagExtractionResult extraction) {
        if (extraction == null || !extraction.hasEmotionalContext()) {
            return context;
        }
        RememberContext base = context != null ? context : RememberContext.EMPTY;
        RememberHints merged = RememberHints.withAppraisedAffect(
                base.hints(), extraction.valence(), extraction.arousal());
        return base.withHints(merged);
    }

    /**
     * Returns true if the text should be chunked before ingestion.
     */
    private boolean shouldChunk(String text) {
        return chunker != null && text != null && text.length() > chunkConfig.maxChunkSize();
    }

    /**
     * Chunks text, parallel-embeds all chunks, and ingests each with the caller's
     * cognitive metadata. The parent memory ID is added as a tag to all chunks.
     *
     * <p>If the caller provided tags, those tags are applied to every chunk.
     * Each chunk ID follows the convention {@code parentId::chunk-N}.</p>
     *
     * @param id      parent memory ID
     * @param text    full text to chunk
     * @param type    memory tier
     * @param source  memory source
     * @param hints   ICNU hints (nullable  --  used when context is null)
     * @param context ingestion context (nullable  --  used when hints is null)
     * @param tags    caller-provided tags to apply to all chunks
     */
    private void rememberChunked(String id, String text, MemoryType type,
                                  MemorySource source,
                                  com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints hints,
                                  RememberContext context,
                                  String... tags) {
        var chunks = chunker.chunk(id, text, chunkConfig);
        if (chunks.isEmpty()) {
            log.warn("[Remember] Chunker returned empty for '{}' ({} chars), skipping", id, text.length());
            return;
        }

        // Provenance tags from the caller (e.g., "file-ingest", filename)  --  shared across chunks
        String parentTag = sanitizeTag(id);
        String[] provenanceTags;
        if (tags != null && tags.length > 0) {
            provenanceTags = new String[tags.length + 1];
            System.arraycopy(tags, 0, provenanceTags, 0, tags.length);
            provenanceTags[tags.length] = parentTag;
        } else {
            provenanceTags = new String[]{ parentTag };
        }

        // Per-chunk tag extraction  --  each chunk gets its own content-derived tags
        // merged with the shared provenance tags.
        // Use the configured tag extractor (LLM when available, content-based fallback).
        var chunkTagExtractor = rememberPathway.tagExtractor();

        // Split chunks into parent and child chunks
        List<com.spectrayan.spector.commons.chunker.Chunk> parentChunks = new ArrayList<>();
        List<com.spectrayan.spector.commons.chunker.Chunk> childChunks = new ArrayList<>();
        for (var chunk : chunks) {
            if ("parent".equals(chunk.metadata().get("chunk_role"))) {
                parentChunks.add(chunk);
            } else {
                childChunks.add(chunk);
            }
        }

        // Parallel-embed only child chunks
        List<String> childTexts = childChunks.stream().map(com.spectrayan.spector.commons.chunker.Chunk::text).toList();
        List<PipelineEmbeddingResult> childEmbeddings = childTexts.isEmpty() ? List.of() : parallelPipeline.embed(childTexts, embedConfig);

        List<String> ingestedChunkIds = new ArrayList<>();
        int totalChunks = parentChunks.size() + childChunks.size();

        try {
            // 1. Ingest parent chunks (bypass embedding via zero vector)
            float[] dummyVector = new float[this.dimensions];
            for (var chunk : parentChunks) {
                var parentBuilder = RememberContext.builder()
                        .metadata(chunk.metadata());
                if (context != null && context.overrideTimestampMs() != null) {
                    parentBuilder.overrideTimestampMs(context.overrideTimestampMs());
                }
                rememberPathway.ingestCognitive(chunk.chunkId(), chunk.text(),
                            dummyVector, type, provenanceTags, source, parentBuilder.build());
                
                ingestedChunkIds.add(chunk.chunkId());
            }

            // 2. Ingest child chunks
            for (int i = 0; i < childChunks.size(); i++) {
                var chunk = childChunks.get(i);
                var embedding = childEmbeddings.get(i);

                if (!embedding.success()) {
                    throw new SpectorServerException(ErrorCode.EMBEDDING_REQUEST_FAILED,
                            "Embedding failed for chunk: " + embedding.error());
                }

                // Extract content-specific tags from this chunk's text (skip LLM extraction if caller tags provided)
                String[] contentTags;
                TagExtractionResult chunkExtraction = null;
                if (provenanceTags != null && provenanceTags.length > 0) {
                    contentTags = new String[0];
                } else {
                    chunkExtraction = chunkTagExtractor.extractWithContext(chunk.chunkId(), chunk.text());
                    contentTags = chunkExtraction.tags();
                }

                // Merge provenance + per-chunk content tags (deduplicated)
                var mergedSet = new java.util.LinkedHashSet<String>();
                for (String pt : provenanceTags) mergedSet.add(pt);
                for (String ct : contentTags) mergedSet.add(ct);
                String[] chunkTags = mergedSet.toArray(String[]::new);

                // Construct child RememberContext merging metadata
                RememberContext childContext;
                if (context != null) {
                    var mergedMeta = new java.util.HashMap<>(context.metadata());
                    mergedMeta.putAll(chunk.metadata());
                    var childBuilder = RememberContext.builder()
                            .hints(context.hints())
                            .entities(context.entities())
                            .hebbianEdges(context.hebbianEdges())
                            .temporalLinks(context.temporalLinks())
                            .metadata(mergedMeta);
                    if (context.overrideTimestampMs() != null) {
                        childBuilder.overrideTimestampMs(context.overrideTimestampMs());
                    }
                    childContext = childBuilder.build();
                } else {
                    childContext = RememberContext.builder()
                            .hints(hints)
                            .metadata(chunk.metadata())
                            .build();
                }

                if (chunkExtraction != null) {
                    childContext = withAppraisedAffect(childContext, chunkExtraction);
                }
                rememberPathway.ingestCognitive(chunk.chunkId(), chunk.text(),
                            embedding.embedding(), type, chunkTags, source, childContext);
                
                ingestedChunkIds.add(chunk.chunkId());
            }

            log.info("[Remember] Chunked '{}'  ->  {} chunks stored from {} chars (chunkSize={}, overlap={})",
                    id.length() > 60 ? "..." + id.substring(id.length() - 57) : id,
                    ingestedChunkIds.size(), text.length(), chunkConfig.maxChunkSize(), chunkConfig.overlap());

        } catch (Exception e) {
            log.warn("[RememberChunked] Chunk ingestion failed for '{}' after {}/{} chunks. Rolling back.", id, ingestedChunkIds.size(), totalChunks);
            for (String chunkId : ingestedChunkIds) {
                try {
                    tombstoneById(chunkId);
                } catch (Exception te) {
                    log.warn("Failed to tombstone chunk '{}' during rollback: {}", chunkId, te.getMessage());
                }
            }
            if (e instanceof RuntimeException re && isLlmAppraisalFailure(re)) {
                throw re;
            }
            throw new SpectorServerException(ErrorCode.INGESTION_PIPELINE_FAILED, e, id);
        }
    }

    /**
     * Removes a chunk written during an ingestion that subsequently failed.
     *
     * <p><b>Deliberately does not consult {@code mutationPolicy}.</b> This is rollback of a write that never
     * completed, not deletion of stored data. A legal hold protects data the tenant committed; it has no
     * interest in half-ingested chunks from a failed operation, and refusing the rollback would leave
     * exactly that garbage behind — orphaned chunks of a document that was never stored, which nobody can
     * read and nobody wants retained.</p>
     *
     * <p>This is the one deletion path in the engine that intentionally bypasses the policy. It is called out
     * here rather than left as an unexplained absence, so that a reader auditing R1.4b coverage finds a
     * reason instead of a gap.</p>
     */
    private void tombstoneById(String memoryId) {
        MemoryLocation loc = index.locate(memoryId);
        if (loc != null) {
            partitionManager.routerFor(loc.colocatedPartition()).tombstone(loc);
            index.remove(memoryId);
            wal.appendForget(memoryId);
        }
    }

    /**
     * Returns whether a tombstoned record's payload is still at its recorded location: the slot is
     * tombstoned, not yet purged, and no live id owns it.
     */
    private boolean tombstonedPayloadStillAt(MemoryLocation loc) {
        if (loc.offset() < 0) {
            return false;
        }
        try {
            var router = partitionManager.routerFor(loc.colocatedPartition());
            return router.isTombstoned(loc) && !router.isPurged(loc)
                    && index.findIdByOffset(loc.colocatedPartition(), loc.type(), loc.offset()) == null;
        } catch (RuntimeException e) {
            log.warn("Purge: could not verify tombstoned slot {}@{}: {}", loc.type(), loc.offset(), e.getMessage());
            return false;
        }
    }

    /** Whether {@code e} is the no-fallback LLM tagging failure, which must reach the caller unwrapped. */
    private static boolean isLlmAppraisalFailure(RuntimeException e) {
        return e instanceof com.spectrayan.spector.commons.error.SpectorMemoryException sme
                && sme.errorCode() == ErrorCode.MEMORY_LLM_APPRAISAL_FAILED;
    }

    /** Writes the current tombstone registry to the ledger (DISK mode only). */
    private void persistTombstones() {
        if (tombstoneLedger == null) {
            return;
        }
        try {
            tombstoneLedger.save(index.tombstones());
        } catch (java.io.IOException e) {
            log.error("Failed to persist tombstone ledger {}: {}", tombstoneLedger.file(), e.getMessage(), e);
        }
    }

    /** Sanitizes a memory ID into a valid tag (lowercase, hyphens, no special chars). */
    private static String sanitizeTag(String id) {
        if (id == null) return "unknown";
        return id.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Processes attachments from RememberContext and creates sub-memories.
     *
     * <p>Each extracted chunk from an attachment is stored as a separate memory
     * with a Hebbian edge linking it to the parent memory.</p>
     */
    private void processAttachments(String parentId, RememberContext context,
                                     MemoryType type, MemorySource source, String[] tags) {
        if (attachmentProcessor == null) {
            log.debug("No AttachmentProcessor configured  --  skipping attachments for '{}'", parentId);
            return;
        }

        List<com.spectrayan.spector.memory.pathway.pipeline.AttachmentProcessor.AttachmentResult> results =
                attachmentProcessor.processAttachments(parentId, context);

        if (results.isEmpty()) {
            log.debug("No attachment chunks produced for '{}'", parentId);
            return;
        }

        int ingested = 0;
        for (var result : results) {
            try {
                // Build sub-memory context with Hebbian edge to parent
                var subContext = RememberContext.builder()
                        .metadata(result.metadata())
                        .hebbianEdge(parentId, 0.8f)  // strong link to parent
                        .build();

                float[] vector = embeddingProvider.embed(result.text()).vector();
                rememberPathway.ingestCognitive(
                            result.chunkId(), result.text(), vector, type, tags, source, subContext);
                
                ingested++;
            } catch (RuntimeException e) {
                log.warn("[Attachment] Failed to ingest chunk '{}': {}", result.chunkId(), e.getMessage());
            }
        }

        log.info("[Attachment] Processed {} attachment chunks for parent '{}' ({} ingested)",
                results.size(), parentId, ingested);
    }

    @Override
    public ImportanceResult estimateImportance(String text,
                                                com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints hints) {
        try {
            // Step 1: Embed text
            float[] vector = embeddingProvider.embed(text).vector();

            // Step 1b: L2-normalize
            float norm = com.spectrayan.spector.core.similarity.VectorOps.magnitude(vector);
            if (norm > 0f && Math.abs(norm - 1.0f) > 1e-6f) {
                vector = com.spectrayan.spector.core.similarity.VectorOps.normalize(vector);
            }

            // Step 2: Nearest distance across working, semantic and procedural slabs, plus
            // vectors observed on ingest (episodic turns do not keep an embedding).
            var router = partitionManager.cognitiveRouter();
            float slab = com.spectrayan.spector.memory.neuromod.dopamine.NoveltyReference.nearest(
                    router, vector, quantizer.mins(), quantizer.scales());
            float ingested = importanceProvider != null
                    ? importanceProvider.nearestIngested(vector)
                    : Float.POSITIVE_INFINITY;
            float nearestDist = slab < ingested ? slab : ingested;

            // Step 3: Resolve effective salience profile
            com.spectrayan.spector.memory.model.SalienceProfile profile = rememberPathway.salienceProfile();

            // Step 4: Build context and delegate to provider
            // Note: zScore=0.0 placeholder — DefaultImportanceProvider computes it
            // internally from nearestDistance via its SurpriseDetector reference.
            ImportanceContext ctx = new ImportanceContext(
                    text, vector, hints, profile, null,
                    nearestDist, 0.0, true);
            return importanceProvider.score(ctx);
        } catch (Exception e) {
            log.error("Failed to estimate importance: {}", e.getMessage(), e);
            throw new com.spectrayan.spector.commons.error.SpectorServerException(
                    com.spectrayan.spector.commons.error.ErrorCode.INTERNAL_ERROR, e,
                    "Importance estimation failed");
        }
    }

    @Override
    public RecallOptions defaultRecallOptions() {
        return this.defaultRecallOptions;
    }

    @Override
    public List<CognitiveResult> recall(String queryText, RecallOptions options) {
        if (queryText == null) {
            throw new IllegalArgumentException("queryText cannot be null");
        }
        acquireLease();
        try {
            if (options == null) {
                options = this.defaultRecallOptions != null ? this.defaultRecallOptions : RecallOptions.DEFAULT;
            }
            // Auto-profile & query tag resolution: use ProfileAdaptor and TagExtractor
            if (options.autoProfile()) {
                var optBuilder = options.toBuilder();
                // Query tagging never calls the LLM: a recall must not pay a generation round-trip.
                var tagExtractor = rememberPathway != null
                        ? TagExtractors.forQueries(rememberPathway.tagExtractor())
                        : null;
                String[] extracted = tagExtractor != null ? tagExtractor.extract("query", queryText) : null;
                String[] tags = extracted != null ? extracted : new String[0];

                if (options.profile() == null) {
                    CognitiveProfile suggested = null;
                    if (profileAdaptor != null && tags.length > 0) {
                        suggested = profileAdaptor.suggest(tags);
                    }
                    if (suggested == null) {
                        SalienceProfile sp = salienceProfile();
                        if (sp != null) {
                            suggested = sp.defaultProfile();
                        }
                    }
                    if (suggested == null) {
                        suggested = CognitiveProfile.BALANCED;
                    }
                    log.debug("Auto-profile resolved to {} for query '{}'", suggested, queryText);
                    optBuilder.profile(suggested);
                }
                if (tags.length > 0 && options.synapticTagMask() == 0L && options.synapticTagMaskHi() == 0L) {
                    optBuilder.synapticFilter(tags);
                }
                options = optBuilder.build();
            }
            options = options.withKeyedTags(partitionManager != null ? partitionManager.encryptor() : null);
            var signal = com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal.forTextQuery(queryText, options);
            bindRecallSignalContext(signal);
            List<CognitiveResult> storeResults = recallPathway.execute(null, signal);
            String sessionId = MemoryScope.sessionId();
            storeResults = sessionBufferManager.merge(sessionId, queryText, options, storeResults, embeddingProvider, () -> 0);
            storeResults = applyReleaseGate(storeResults, options);
            if (signal.isTruncated()) {
                storeResults = storeResults.stream().map(r -> r.withTruncated(true)).toList();
            }
            return storeResults;
        } finally {
            releaseLease();
        }
    }

    /**
     * Re-reads consolidation flags from the live record and applies the release gate.
     * Scoring and the session buffer can rebuild a result without those flags; the gate has
     * to see the bits that are actually stored.
     */
    private List<CognitiveResult> applyReleaseGate(List<CognitiveResult> results, RecallOptions options) {
        if (results == null || results.isEmpty()) {
            return results;
        }
        List<CognitiveResult> stamped = new java.util.ArrayList<>(results.size());
        for (CognitiveResult result : results) {
            stamped.add(stampLiveFlags(result));
        }
        var signal = com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal.forTextQuery("", options);
        signal.setCandidates(stamped);
        new com.spectrayan.spector.memory.pathway.recall.relay.GovernedReleaseGateRelay().transmit(signal);
        return new java.util.ArrayList<>(signal.candidates());
    }

    private CognitiveResult stampLiveFlags(CognitiveResult result) {
        if (result == null || index == null || partitionManager == null) {
            return result;
        }
        MemoryLocation loc = index.locate(result.id());
        if (loc == null) {
            return result;
        }
        var body = partitionManager.routerFor(loc.colocatedPartition()).readRecordBody(loc, false);
        if (body == null || body.consolidationFlags() == result.consolidationFlags()) {
            return result;
        }
        return result.withConsolidationFlags(body.consolidationFlags());
    }

    @Override
    public List<CognitiveResult> recall(String queryText, CognitiveProfile profile) {
        CognitiveProfile effective = profileConfig.validate(profile);
        RecallOptions base = this.defaultRecallOptions != null ? this.defaultRecallOptions : RecallOptions.DEFAULT;
        return recall(queryText, base.toBuilder().profile(effective).build());
    }

    @Override
    public List<CognitiveResult> recall(String queryText) {
        return recall(queryText, this.defaultRecallOptions != null ? this.defaultRecallOptions : RecallOptions.DEFAULT);
    }

    @Override
    public void forget(String id) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.forget(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        forgetWithResult(id);
            }
    }

    @Override
    public com.spectrayan.spector.memory.model.ForgetResult forgetWithResult(String id) {
        return forgetWithResult(id, com.spectrayan.spector.memory.model.ForgetCascade.FLAG);
    }

    @Override
    public com.spectrayan.spector.memory.model.ForgetResult forgetWithResult(
            String id, com.spectrayan.spector.memory.model.ForgetCascade cascade) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.forget(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            if (id == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "id"); }
            MemoryLocation loc = index.locate(id);
            if (loc == null) {
                // Deliberately does NOT throw: idempotent delete is a contract Spring AI's VectorStore and
                // both chat-memory bulk paths rely on. The caller now distinguishes this case via the
                // returned result instead of being told the memory was forgotten (#983).
                log.warn("Forget: memory '{}' not found in index — nothing was tombstoned", id);
                return com.spectrayan.spector.memory.model.ForgetResult.notFound(id);
            }
            mutationPolicy.checkDeletion(
                    com.spectrayan.spector.memory.policy.DeletionRequest.forget(namespaceId, id));
            // Collect derived memories before the tombstone: the episodic turn is read for its lineage keys.
            java.util.Map<String, Boolean> derived = collectDerived(id, loc);
            tombstoneInternal(id, loc);
            long tombstonedAt = System.currentTimeMillis();

            java.util.List<String> retracted = new java.util.ArrayList<>();
            java.util.List<String> cascadeTombstoned = new java.util.ArrayList<>();
            var mode = cascade != null ? cascade : com.spectrayan.spector.memory.model.ForgetCascade.FLAG;
            if (mode != com.spectrayan.spector.memory.model.ForgetCascade.REPORT) {
                for (var entry : derived.entrySet()) {
                    String derivedId = entry.getKey();
                    MemoryLocation dloc = index.locate(derivedId);
                    if (dloc == null) {
                        continue;
                    }
                    if (mode == com.spectrayan.spector.memory.model.ForgetCascade.TOMBSTONE && entry.getValue()) {
                        tombstoneInternal(derivedId, dloc);
                        if (provenanceMemory != null) {
                            provenanceMemory.tombstoneByTarget(ProvenanceKeys.of(derivedId));
                        }
                        cascadeTombstoned.add(derivedId);
                    } else {
                        partitionManager.routerFor(dloc.colocatedPartition()).markRetracted(dloc);
                        if (wal != null) {
                            wal.appendMarkRetracted(derivedId, dloc.type(), dloc.offset());
                        }
                        retracted.add(derivedId);
                    }
                }
            }
            if (!cascadeTombstoned.isEmpty()) {
                persistTombstones();
            }
            if (!derived.isEmpty()) {
                log.info("Forget: '{}' cascade={} derived={} retracted={} tombstoned={}",
                        id, mode, derived.size(), retracted.size(), cascadeTombstoned.size());
            }
            log.debug("Forget: '{}' tombstoned", id);
            return new com.spectrayan.spector.memory.model.ForgetResult(id, true, tombstonedAt,
                    new java.util.ArrayList<>(derived.keySet()), retracted, cascadeTombstoned);
        } finally {
            releaseLease();
        }
            }
    }

    /** Tombstones one record: store flag, WAL entry, index retirement and the tombstone registry. */
    private void tombstoneInternal(String id, MemoryLocation loc) {
        partitionManager.routerFor(loc.colocatedPartition()).tombstone(loc);
        wal.appendForget(id);
        // retire, not remove: the bytes are still on disk, and purge needs this location to reach them.
        index.retire(id);
        persistTombstones();
    }

    /**
     * Finds every live memory derived from {@code id} through provenance, transitively.
     *
     * <p>Two kinds of lineage are followed: rows whose semantic or procedural source is the memory
     * (duplicate merges, #1145), and, for an episodic turn, reflection rows covering that turn in the
     * same session and partition.</p>
     *
     * @return derived ids in discovery order, each mapped to {@code true} when the memory has no other
     *         source than the one being forgotten
     */
    private java.util.Map<String, Boolean> collectDerived(String id, MemoryLocation loc) {
        java.util.Map<String, Boolean> derived = new java.util.LinkedHashMap<>();
        if (provenanceMemory == null) {
            return derived;
        }
        // target key -> single-source?
        java.util.Map<Long, Boolean> targets = new java.util.LinkedHashMap<>();
        if (loc.type() == MemoryType.EPISODIC) {
            collectEpisodicDerived(loc, targets);
        }
        java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        long rootKey = ProvenanceKeys.of(id);
        visited.add(rootKey);
        queue.add(rootKey);
        queue.addAll(targets.keySet());
        while (!queue.isEmpty()) {
            long key = queue.poll();
            if (key != rootKey && !visited.add(key)) {
                continue;
            }
            for (var row : provenanceMemory.findBySource(key)) {
                long target = row.targetTsid();
                if (target == rootKey) {
                    continue;
                }
                boolean single = provenanceMemory.findAllByTarget(target).size() <= 1;
                targets.merge(target, single, Boolean::logicalAnd);
                if (!visited.contains(target)) {
                    queue.add(target);
                }
            }
        }
        for (var entry : targets.entrySet()) {
            String derivedId = resolveProvenanceId(entry.getKey());
            if (derivedId != null && index.locate(derivedId) != null) {
                derived.merge(derivedId, entry.getValue(), Boolean::logicalAnd);
            }
        }
        return derived;
    }

    /** Adds the targets of reflection rows that cover the episodic turn at {@code loc}. */
    private void collectEpisodicDerived(MemoryLocation loc, java.util.Map<Long, Boolean> targets) {
        var router = partitionManager.routerFor(loc.colocatedPartition());
        var episodic = router != null ? router.episodic() : null;
        if (episodic == null) {
            return;
        }
        com.spectrayan.spector.kernel.api.EpisodeRecord turn;
        try {
            turn = episodic.readTurn(loc.offset(), false);
        } catch (RuntimeException e) {
            log.warn("Forget: could not read episodic turn at offset {} for cascade: {}", loc.offset(), e.getMessage());
            return;
        }
        if (turn == null) {
            return;
        }
        for (var row : provenanceMemory.findBySession(turn.sessionId())) {
            if (row.partitionSeq() != loc.colocatedPartition()) {
                continue;
            }
            boolean offsetMatch = loc.offset() >= row.firstOffsetHint() && loc.offset() <= row.lastOffsetHint();
            boolean seqMatch = turn.sessionId() != 0L
                    && turn.sequenceId() >= row.firstSeq() && turn.sequenceId() <= row.lastSeq();
            if (offsetMatch || seqMatch) {
                long target = row.targetTsid();
                boolean single = row.turnCount() <= 1 && provenanceMemory.findAllByTarget(target).size() <= 1;
                targets.merge(target, single, Boolean::logicalAnd);
            }
        }
    }

    @Override
    public com.spectrayan.spector.memory.model.PurgeResult purge(String id) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.purge(
            namespaceId, id, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            if (id == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "id"); }

            MemoryLocation loc = index.locate(id);
            boolean wasTombstoned = false;
            if (loc == null) {
                // A forgotten record is no longer in the live index, but its bytes are still on disk. Purge
                // is exactly the operation that has to reach them, so look in the tombstone registry too.
                loc = index.locateTombstoned(id);
                wasTombstoned = loc != null;
            }
            if (loc == null) {
                // Idempotent by contract, same as forget. Reported, not thrown.
                log.warn("Purge: memory '{}' not found in index or tombstones — nothing was destroyed", id);
                return com.spectrayan.spector.memory.model.PurgeResult.notFound(id, namespaceId);
            }

            // Policy first. Everything after this point is irreversible, so the refusal has to happen
            // before the first byte is touched — not after a partial destruction.
            mutationPolicy.checkDeletion(
                    com.spectrayan.spector.memory.policy.DeletionRequest.purge(namespaceId, id));

            // Ordering matters throughout and is the easiest thing to get wrong here.
            //
            // 1. WAL first. If the process dies mid-purge, recovery replays RECORD_WRITE and restores the
            //    payload from the log; only a recorded PURGE undoes that. Writing the WAL entry after the
            //    zeroing would leave a window in which a crash silently resurrects the content.
            boolean walRecorded = false;
            try {
                wal.appendPurge(id, loc.type(), loc.offset());
                walRecorded = true;
            } catch (RuntimeException e) {
                // Refuse rather than proceed. An unrecorded purge is a purge that a crash can undo, and
                // reporting it as done would be a false claim.
                log.error("Purge: failed to record WAL entry for '{}' — refusing to destroy data that "
                        + "recovery could resurrect", id, e);
                throw e;
            }

            // 2. Detach from the graphs before the payload goes. The graph planes are keyed by graphSlot,
            //    which is independent of the record's bytes, so order is not strictly forced — but doing it
            //    first means a failure leaves a readable record rather than a zeroed record still wired into
            //    spreading activation.
            var detach = graphFacade != null && loc.graphSlot() >= 0
                    ? graphFacade.detachMemory(loc.graphSlot())
                    : new com.spectrayan.spector.memory.graph.CognitiveGraphFacade.DetachReport(0, false, 0, 0);

            // 3. Overwrite the payload. For a tombstoned record, only when the slot still holds that record:
            //    vacuum may have compacted it away, and the offset may now belong to someone else.
            int payloadZeroed = 0;
            if (!wasTombstoned || tombstonedPayloadStillAt(loc)) {
                payloadZeroed = partitionManager.routerFor(loc.colocatedPartition()).purge(loc);
            } else {
                log.info("Purge: payload of tombstoned '{}' is no longer at its recorded slot "
                        + "(compacted by vacuum); erasing text and references only", id);
            }

            // 4. Erase the text. Two copies exist in a disk-backed store — the off-heap text.dat frame and
            //    an on-heap entry in the index — and an in-memory store has only the latter. Capture whether
            //    the on-heap copy was there before erasing, so the report can distinguish "no off-heap bytes
            //    existed" from "text survived". Deduplication can make the off-heap erase impossible; that
            //    outcome is disclosed rather than swallowed.
            //    A tombstoned record's on-heap copy was already dropped by forget; when it had no off-heap
            //    text position, that on-heap copy was the only one.
            boolean hadInlineText = index.hasInlineText(id) || (wasTombstoned && !loc.hasTextPosition());
            var textOutcome = index.eraseText(id, loc);

            // 5. Drop the record's terms from the lexical index, which holds tokens derived from the text.
            boolean lexicalCleared = false;
            if (bm25Index != null) {
                bm25Index.remove(id);
                lexicalCleared = true;
            }

            // 6. Only now drop the index entry and any tombstone. Every step above needed the location,
            //    which index.remove destroys.
            index.remove(id);
            if (index.clearTombstone(id) != null) {
                persistTombstones();
            }

            var result = new com.spectrayan.spector.memory.model.PurgeResult(
                    id,
                    namespaceId,
                    true,
                    java.time.Instant.now(),
                    payloadZeroed,
                    textOutcome.bytesZeroed(),
                    hadInlineText,
                    textOutcome.status() == com.spectrayan.spector.kernel.store.TextBlobMemory.EraseOutcome.Status.SHARED ? 1 : 0,
                    textOutcome.sharedWith(),
                    detach.hebbianEdges(),
                    detach.temporalUnlinked(),
                    detach.entityLinks(),
                    detach.hyperedges(),
                    walRecorded,
                    com.spectrayan.spector.memory.model.PurgeResult.RETAINED_HEADER_FIELDS,
                    com.spectrayan.spector.memory.model.PurgeResult.UNREACHABLE_COPIES,
                    com.spectrayan.spector.memory.model.PurgeResult.DISCLOSURE_TEXT,
                    wasTombstoned,
                    lexicalCleared);

            if (result.hasLocalRetention()) {
                log.warn("Purge: '{}' destroyed ({} payload bytes zeroed, {} graph refs removed), but its "
                                + "text bytes were RETAINED — identical content is shared with {} other live "
                                + "record(s) by deduplication and erasing it would destroy theirs",
                        id, payloadZeroed, result.graphReferencesRemoved(), textOutcome.sharedWith());
            } else {
                log.info("Purge: '{}' destroyed — {} payload bytes and {} text bytes zeroed, {} graph "
                                + "references removed",
                        id, payloadZeroed, textOutcome.bytesZeroed(), result.graphReferencesRemoved());
            }
            return result;
        } finally {
            releaseLease();
        }
            }
    }

    private volatile com.spectrayan.spector.memory.model.ReflectReport lastReflectReport;
    private volatile long lastReflectTimestamp;

    @Override
    public ReflectReport reflect() {
        return reflect(com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec.fullCycle());
    }

    @Override
    public ReflectReport reflect(com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec spec) {
        acquireLease();
        try {
            ReflectReport report = this.reflectSweepExecutor.execute(this, spec != null ? spec : com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec.fullCycle());
            this.lastReflectReport = report;
            this.lastReflectTimestamp = System.currentTimeMillis();
            return report;
        } finally {
            releaseLease();
        }
    }

    @Override
    public ReflectReport lastReflectReport() {
        return this.lastReflectReport;
    }

    @Override
    public long lastReflectTimestamp() {
        return this.lastReflectTimestamp;
    }

    @Override
    public ReflectReport reflectKernel(com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec spec) {
        acquireLease();
        try {
            if (reflectPathway != null) {
                // Non-deprecated path: no RememberPathway is threaded through. Soul version
                // resolves from the context's SoulVersionSource and nested gist writes go
                // through the PathwayCatalog (ADR-0035 R2.3, §8.1b).
                // Distilled facts are stored through RememberPathway, resolved from this catalog (#1166).
                com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog catalog = null;
                if (rememberPathway != null) {
                    catalog = new com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog();
                    catalog.register(com.spectrayan.spector.memory.pathway.remember.RememberPathway.class, rememberPathway);
                }
                ReflectReport report = reflectPathway.reflect(catalog, null, partitionManager, index,
                        salienceProfile(), episodicSessionIndex, spec, null);
                this.lastReflectReport = report;
                this.lastReflectTimestamp = System.currentTimeMillis();
                return report;
            }
            return ReflectReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public ReflectPathway reflectPathway() {
        return this.reflectPathway;
    }

    @Override
    public com.spectrayan.spector.memory.pathway.reflect.ReflectSweepProgress progress(String sweepId) {
        acquireLease();
        try {
            return this.reflectSweepExecutor.progress(sweepId);
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.express.relay.ExpressReport express(com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal signal) {
        acquireLease();
        try {
            if (expressPathway != null) {
                return expressPathway.express(signal);
            }
            return com.spectrayan.spector.memory.pathway.express.relay.ExpressReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.wander.relay.WanderReport wander() {
        acquireLease();
        try {
            if (wanderPathway != null) {
                return wanderPathway.wander(partitionManager, 0L);
            }
            return com.spectrayan.spector.memory.pathway.wander.relay.WanderReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.decide.relay.DecideReport decide(
            com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal signal) {
        acquireLease();
        try {
            if (decidePathway != null) {
                return decidePathway.decide(signal);
            }
            return com.spectrayan.spector.memory.pathway.decide.relay.DecideReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.skill.relay.SkillReport compileSkill(
            com.spectrayan.spector.memory.pathway.skill.relay.SkillSignal signal) {
        acquireLease();
        try {
            if (skillPathway != null) {
                if (signal.context() == null) {
                    var catalog = new com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog();
                    catalog.register(com.spectrayan.spector.memory.pathway.skill.SkillPathway.class, skillPathway);
                    if (rememberPathway != null) {
                        catalog.register(com.spectrayan.spector.memory.pathway.remember.RememberPathway.class, rememberPathway);
                    }
                    var ctx = com.spectrayan.spector.commons.pathway.DefaultPathwayContext.builder()
                            .catalog(catalog)
                            .build();
                    signal.bind(ctx);
                }
                return skillPathway.compile(signal);
            }
            return com.spectrayan.spector.memory.pathway.skill.relay.SkillReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.dream.relay.DreamReport dream(
            com.spectrayan.spector.kernel.api.DreamMode mode) {
        acquireLease();
        try {
            if (dreamPathway != null) {
                return dreamPathway.dream(mode != null ? mode : com.spectrayan.spector.kernel.api.DreamMode.REM, partitionManager, null);
            }
            return com.spectrayan.spector.memory.pathway.dream.relay.DreamReport.empty();
        } finally {
            releaseLease();
        }
    }

    @Override
    public com.spectrayan.spector.memory.pathway.dream.relay.DreamReport dream() {
        return dream(com.spectrayan.spector.kernel.api.DreamMode.REM);
    }

    @Override
    public List<com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot> continuityHistory(int limit) {
        if (continuityMemory != null) {
            return continuityMemory.readHistory(limit).stream()
                    .map(com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot::fromContinuityRecord)
                    .toList();
        }
        return List.of();
    }

    @Override
    public float calculateLongitudinalDrift() {
        if (continuityMemory != null) {
            return continuityMemory.calculateLongitudinalDrift();
        }
        return 0.0f;
    }

    @Override
    public java.util.Optional<com.spectrayan.spector.memory.model.MemoryProvenance> explain(String memoryId) {
        if (provenanceMemory == null || memoryId == null || memoryId.isEmpty()) {
            return java.util.Optional.empty();
        }
        return provenanceMemory.findByTarget(ProvenanceKeys.of(memoryId))
                .map(s -> toMemoryProvenance(memoryId, s));
    }

    @Override
    public java.util.List<com.spectrayan.spector.memory.model.MemoryProvenance> explainAll(String memoryId) {
        if (provenanceMemory == null || memoryId == null || memoryId.isEmpty()) {
            return java.util.List.of();
        }
        return provenanceMemory.findAllByTarget(ProvenanceKeys.of(memoryId)).stream()
                .map(s -> toMemoryProvenance(memoryId, s))
                .toList();
    }

    @Override
    public java.util.List<com.spectrayan.spector.memory.model.MemoryProvenance> sessionProvenance(long sessionId) {
        if (provenanceMemory == null) {
            return java.util.List.of();
        }
        return provenanceMemory.findBySession(sessionId).stream()
                .map(s -> toMemoryProvenance(
                        com.spectrayan.spector.kernel.id.TsidGenerator.encodeCrockford(s.targetTsid()), s))
                .toList();
    }

    private com.spectrayan.spector.memory.model.MemoryProvenance toMemoryProvenance(
            String targetId, com.spectrayan.spector.kernel.layout.ProvenanceLayout.ProvenanceState s) {
        String kind;
        if (s.sourceKind() == com.spectrayan.spector.kernel.layout.ProvenanceLayout.SOURCE_SEMANTIC) {
            kind = "SEMANTIC";
        } else if (s.sourceKind() == com.spectrayan.spector.kernel.layout.ProvenanceLayout.SOURCE_PROCEDURAL) {
            kind = "PROCEDURAL";
        } else {
            kind = com.spectrayan.spector.memory.model.MemoryProvenance.SOURCE_EPISODIC;
        }
        boolean memorySource = !com.spectrayan.spector.memory.model.MemoryProvenance.SOURCE_EPISODIC.equals(kind);
        return new com.spectrayan.spector.memory.model.MemoryProvenance(
                targetId, s.sessionId(), s.partitionSeq(),
                s.firstSeq(), s.lastSeq(), s.turnCount(),
                s.passNumber(), s.factIndex(), s.batchFactCount(),
                s.consolidatedAtMs(),
                kind,
                memorySource ? resolveProvenanceId(s.sourceTsid()) : null);
    }

    /**
     * Resolves a provenance key back to a memory ID, live or forgotten. TSID-shaped IDs resolve
     * directly; other IDs fall back to a scan of the index.
     */
    private String resolveProvenanceId(long key) {
        if (key == 0L) {
            return null;
        }
        String tsid = com.spectrayan.spector.kernel.id.TsidGenerator.encodeCrockford(key);
        for (String candidate : new String[] { tsid, AbstractConsolidator.MERGED_ID_PREFIX + tsid }) {
            if (index.locate(candidate) != null || index.locateTombstoned(candidate) != null) {
                return candidate;
            }
        }
        for (String id : index.allIds()) {
            if (ProvenanceKeys.of(id) == key) {
                return id;
            }
        }
        for (String id : index.tombstones().keySet()) {
            if (ProvenanceKeys.of(id) == key) {
                return id;
            }
        }
        return null;
    }

    @Override
    public void consolidate() {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        acquireLease();
        try {
            batchConsolidator.consolidate(
                    partitionManager,
                    index,
                    quantizer,
                    entityDirectory,
                    hyperEntityGraph,
                    temporalKnowledgeGraph,
                    rememberPathway,
                    wal,
                    this::inspect
            );
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void updateChunkConfig(com.spectrayan.spector.commons.chunker.ChunkConfig config) {
        if (config != null) {
            this.chunkConfig = config;
            log.info("Updated chunking configuration dynamically: maxChunkSize={}, overlap={}, parentChildLinking={}",
                    config.maxChunkSize(), config.overlap(), config.parentChildLinking());
        }
    }

    @Override
    public void updateRecallOptions(RecallOptions options) {
        if (options != null) {
            this.defaultRecallOptions = options;
            log.info("Updated default RecallOptions dynamically: textSearchMode={}, scoringMode={}, profile={}",
                    options.textSearchMode(), options.scoringMode(), options.profile());
        }
    }

    @Override
    public void applyLiveMemoryPatch(com.spectrayan.spector.config.model.LiveMemoryPatch patch) {
        if (patch != null) {
            this.liveMemoryPatch = patch;
            log.info("Applied LiveMemoryPatch dynamically: flashbulbThreshold={}, decayEnabled={}, vacuumThreshold={}, hebbianMaxDegree={}",
                    patch.flashbulbThreshold(), patch.decayEnabled(), patch.vacuumThreshold(), patch.hebbianMaxDegree());
        }
    }

    @Override
    public void updateHnswEfSearch(int efSearch) {
        if (efSearch > 0 && semanticIndex instanceof com.spectrayan.spector.index.hnsw.AbstractHnswIndex hnsw) {
            hnsw.setEfSearch(efSearch);
            log.info("Updated HNSW efSearch dynamically: efSearch={}", efSearch);
        }
    }

    /** Returns the current live memory patch snapshot (ADR-0085). */
    public com.spectrayan.spector.config.model.LiveMemoryPatch liveMemoryPatch() {
        return this.liveMemoryPatch;
    }

    // ==============================================================
    // EXTENDED API  --  reinforce / suppress / introspect
    // ==============================================================

    @Override
    public void reinforce(String memoryId, byte valence) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        mutationPolicy.checkWrite(com.spectrayan.spector.memory.policy.WriteRequest.reinforce(
            namespaceId, memoryId, com.spectrayan.spector.commons.concurrent.MemoryScope.fenceEpoch(), System.currentTimeMillis()));
        
        acquireLease();
        try {
            reinforcementHandler.reinforce(memoryId, valence,
                    partitionManager, index);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void reinforce(String memoryId, byte valence,
                           com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints updatedHints) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        acquireLease();
        try {
            reinforcementHandler.reinforceWithHints(memoryId, valence, updatedHints,
                    partitionManager, index);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void rehearse(String memoryId) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
        acquireLease();
        try {
            reinforcementHandler.rehearse(memoryId, partitionManager, index);
        } finally {
            releaseLease();
        }
            }
    }

    @Override
    public void suppress(String memoryId, String reason) {
        acquireLease();
        try {
            suppressionSet.suppress(memoryId, reason);
            MemoryLocation loc = index.locate(memoryId);
            if (loc != null) {
                suppressionSet.registerOffset(loc.type().ordinal(), loc.offset());
            }
            if (recallPathway.wasLateral(memoryId)) {
                lateralEvaluator.recordLateralSuppression();
                log.debug("Lateral suppression: '{}' (reason={})", memoryId, reason);
            }
        } finally {
            releaseLease();
        }
    }

    @Override
    public void suppress(String memoryId) { suppress(memoryId, null); }

    @Override
    public void unsuppress(String memoryId) {
        acquireLease();
        try {
            suppressionSet.unsuppress(memoryId);
        } finally {
            releaseLease();
        }
    }

    @Override
    public void markResolved(String memoryId) {
        acquireLease();
        try {
            var loc = index.locate(memoryId);
            if (loc == null) return;
            partitionManager.routerFor(loc.colocatedPartition()).markResolved(loc);
            log.debug("Zeigarnik: marked '{}' as RESOLVED", memoryId);
        } finally {
            releaseLease();
        }
    }

    @Override
    public void markUnresolved(String memoryId) {
        acquireLease();
        try {
            var loc = index.locate(memoryId);
            if (loc == null) return;
            partitionManager.routerFor(loc.colocatedPartition()).markUnresolved(loc);
            log.debug("Zeigarnik: marked '{}' as UNRESOLVED", memoryId);
        } finally {
            releaseLease();
        }
    }

    @Override
    public MemoryInsight introspect(String topic) {
        acquireLease();
        try {
            List<CognitiveResult> results = recall(topic, RecallOptions.builder().topK(20).build());
            return introspector.analyze(topic, results);
        } finally {
            releaseLease();
        }
    }

    @Override
    public WhyNotExplanation whyNot(String memoryId, String queryText, RecallOptions options) {
        acquireLease();
        try {
            var loc = index.locate(memoryId);
            if (loc == null) {
                return new WhyNotExplanation(memoryId, queryText, false, false,
                        null, 0f, WhyNotExplanation.Reason.NOT_FOUND,
                        "Memory '" + memoryId + "' does not exist in the index.");
            }

            if (partitionManager.routerFor(loc.colocatedPartition()).isTombstoned(loc)) {
                return new WhyNotExplanation(memoryId, queryText, true, false,
                        null, 0f, WhyNotExplanation.Reason.TOMBSTONED,
                        "Memory '" + memoryId + "' has been deleted (tombstone flag set).");
            }

            if (suppressionSet.isSuppressed(memoryId)) {
                return new WhyNotExplanation(memoryId, queryText, true, true,
                        null, 0f, WhyNotExplanation.Reason.SUPPRESSED,
                        "Memory '" + memoryId + "' is in the suppression set. "
                        + "Use unsuppress(\"" + memoryId + "\") to allow recall.");
            }

            int originalTopK = options != null ? options.topK() : 5;
            RecallOptions observeOptions = RecallOptions.builder()
                    .recallMode(RecallMode.OBSERVE)
                    .topK(Math.max(originalTopK, 20))
                    .build();

            List<CognitiveResult> results = recall(queryText, observeOptions);

            CognitiveResult found = null;
            for (CognitiveResult r : results) {
                if (memoryId.equals(r.id())) {
                    found = r;
                    break;
                }
            }

            if (found != null) {
                return new WhyNotExplanation(memoryId, queryText, true, false,
                        found.breakdown(), 0f, WhyNotExplanation.Reason.OUTRANKED,
                        "Memory '" + memoryId + "' WAS found in extended recall "
                        + "(score=" + String.format("%.4f", found.score()) + "). "
                        + "It may have been outside your original topK cutoff.");
            }

            float cutoffScore = results.isEmpty() ? 0f : results.get(results.size() - 1).score();
            return new WhyNotExplanation(memoryId, queryText, true, false,
                    null, cutoffScore, WhyNotExplanation.Reason.FILTERED,
                    "Memory '" + memoryId + "' was eliminated by pre-filters "
                    + "(tag gate, valence range, importance floor, or time decay). "
                    + "TopK cutoff score: " + String.format("%.4f", cutoffScore) + ". "
                    + (cutoffScore > 0 ? "Check if tags/valence/importance match your query options." : ""));
        } finally {
            releaseLease();
        }
    }

    // ==============================================================
    // INSPECT  --  Full Cognitive X-Ray
    // ==============================================================

    @Override
    public CognitiveRecord inspect(String id) {
        if (id == null) return null;

        MemoryLocation loc = index.locate(id);
        if (loc == null) return null;

        String text = index.text(id);
        MemorySource source = index.source(id);
        String[] memTags = index.tags(id);

        var body = partitionManager.routerFor(loc.colocatedPartition()).readRecordBody(loc, true);
        if (body == null) return null;

        var header = body.header();

        // Metadata and suppression state
        var metadata = java.util.Map.<String, String>of();
        boolean suppressed = suppressionSet.isSuppressed(id);

        return new CognitiveRecord(
                id, text, loc.type(), source, memTags,
                header.timestampMs(), header.synapticTags(), header.exactNorm(),
                header.importance(), header.agentRecallCount(), body.spectorRecallCount(),
                header.centroidId(), header.valence(), header.arousal(),
                header.storageStrength(), header.flags(), body.consolidationFlags(),
                body.quantizedVector(), loc.graphSlot(), loc.offset(),
                metadata, suppressed);
    }

    // ==============================================================
    // BROWSE  --  Tag-Based Iteration
    // ==============================================================

    @Override
    public List<CognitiveRecord> browse(String... tags) {
        if (tags == null || tags.length == 0) return List.of();

        // O(1) inverted tag index lookup  --  intersects tag sets for AND semantics
        var matchingIds = index.idsByAllTags(tags);
        if (matchingIds.isEmpty()) return List.of();

        var results = new java.util.ArrayList<CognitiveRecord>(matchingIds.size());

        for (String memId : matchingIds) {
            MemoryLocation loc = index.locate(memId);
            if (loc == null) continue;

            var body = partitionManager.routerFor(loc.colocatedPartition()).readRecordBody(loc, false);
            if (body != null) {
                var header = body.header();
                if (!EncodingHeaderFields.isTombstoned(header.flags())) {
                    String[] memTags = index.tags(memId);
                    results.add(new CognitiveRecord(
                            memId, index.text(memId), loc.type(),
                            index.source(memId), memTags,
                            header.timestampMs(), header.synapticTags(), header.exactNorm(),
                            header.importance(), header.agentRecallCount(), body.spectorRecallCount(),
                            header.centroidId(), header.valence(), header.arousal(),
                            header.storageStrength(), header.flags(), body.consolidationFlags(),
                            null, // no vector for browse (use inspect for full detail)
                            loc.graphSlot(), loc.offset(),
                            java.util.Map.of(), suppressionSet.isSuppressed(memId)));
                }
            }
        }

        return List.copyOf(results);
    }

    // ==============================================================
    // EXPORT  --  Bulk Memory Export
    // ==============================================================

    @Override
    public String exportJson() {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var arrayNode = mapper.createArrayNode();

        for (var entry : index.locationMap().entrySet()) {
            String memId = entry.getKey();
            CognitiveRecord record = inspect(memId);
            // Purged records are excluded unconditionally and separately from the tombstone check, even
            // though purge sets both bits. Relying on the tombstone bit alone would make the exclusion
            // incidental: if a future change ever exported tombstoned records behind a flag, purged records
            // would silently ride along, and what would be exported is a zeroed payload presented as data.
            if (record != null && !record.isPurged() && !record.isTombstoned()) {
                // Parse record's JSON into a node to avoid double-encoding
                arrayNode.add(mapper.readTree(record.toJson()));
            }
        }

        return arrayNode.toString();
    }

    // ==============================================================
    // PROSPECTIVE / SCRATCHPAD / STATS
    // ==============================================================

    @Override
    public Reminder scheduleReminder(String text, Instant triggerAt, String... tags) {
        return prospectiveScheduler.schedule(text, triggerAt, tags);
    }

    @Override
    public Reminder scheduleReminder(String text, Duration delay, String... tags) {
        return prospectiveScheduler.scheduleAfter(text, delay, tags);
    }

    @Override
    public void scratchpad(String text) {
        remember("scratchpad-" + System.nanoTime(), text, MemoryType.WORKING);
    }

    @Override
    public int totalMemories() {
        // #443: aggregate across all live partitions. Working memory is global (shared by
        // every router) so it is counted once; the other tiers are summed per partition.
        int total = partitionManager.activeRouter().countFor(MemoryType.WORKING);
        for (var handle : partitionManager.snapshot()) {
            var router = handle.router();
            total += router.countFor(MemoryType.EPISODIC)
                    + router.countFor(MemoryType.SEMANTIC)
                    + router.countFor(MemoryType.PROCEDURAL);
        }
        return total;
    }

    @Override
    public int memoryCount(MemoryType type) {
        if (type == MemoryType.WORKING) {
            return partitionManager.activeRouter().countFor(MemoryType.WORKING);
        }
        int total = 0;
        for (var handle : partitionManager.snapshot()) {
            total += handle.router().countFor(type);
        }
        return total;
    }

    @Override
    public int tombstonedCount(MemoryType type) {
        if (type == null) {
            return 0;
        }
        vacuumLock.lock();
        try {
            if (type == MemoryType.WORKING) {
                // Working memory is global; count it once through the active router, as memoryCount does.
                return countDead(partitionManager.activeRouter().get(MemoryType.WORKING));
            }
            int dead = 0;
            for (var handle : partitionManager.snapshot()) {
                dead += countDead(handle.router().get(type));
            }
            return dead;
        } finally {
            vacuumLock.unlock();
        }
    }

    private static int countDead(EngramRegion store) {
        if (store == null) {
            return 0;
        }
        int dead = 0;
        int size = store.size();
        for (int i = 0; i < size; i++) {
            long offset = store.recordOffset(i);
            if (store.isTombstoned(offset) || store.isPurged(offset)) {
                dead++;
            }
        }
        return dead;
    }

    @Override
    public int decay(Duration olderThan, float factor) {
        if (olderThan == null) { throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "olderThan"); }
        if (factor < 0f || factor > 1f) throw new SpectorValidationException(ErrorCode.ARGUMENT_OUT_OF_RANGE, "factor", 0, 1, 0);

        long nowMs = System.currentTimeMillis();
        long thresholdMs = nowMs - olderThan.toMillis();

        var episodicStore = partitionManager.cognitiveRouter().episodic();
        if (episodicStore == null) return 0;
        int affected = episodicStore.decayOldTurns(thresholdMs, factor);
        log.info("Decay: {} memories older than {} multiplied by {}", affected, olderThan, factor);
        return affected;
    }

    // ==============================================================
    // SALIENCE PROFILE  --  runtime personality & interest configuration
    // ==============================================================

    @Override
    public void setSalienceProfile(SalienceProfile profile) {
        if (rememberPathway != null) {
            rememberPathway.setSalienceProfile(profile);
        }
        log.info("Salience profile updated: interests={}, disinterests={}, persona={}, icnuOverride={}",
                profile != null ? profile.interests().size() : 0,
                profile != null ? profile.disinterests().size() : 0,
                profile != null && profile.hasPersona(),
                profile != null && profile.hasIcnuOverride());
    }

    @Override
    public void setSoulVersion(short version) {
        if (rememberPathway != null) {
            rememberPathway.setSoulVersion(version);
        }
    }

    @Override
    public void applyIdentity(
            com.spectrayan.spector.memory.model.SoulContext primarySoul,
            java.util.List<com.spectrayan.spector.memory.model.SoulContext> soulStack,
            SalienceProfile salience) {
        if (salience != null) {
            setSalienceProfile(salience);
        }
        if (primarySoul != null) {
            setSoulVersion(primarySoul.soulVersion());
        }
        if (rememberPathway != null && soulStack != null) {
            rememberPathway.setSoulContexts(soulStack);
        }
    }

    @Override
    public SalienceProfile salienceProfile() {
        if (rememberPathway != null) {
            return rememberPathway.salienceProfile();
        }
        return rememberPathway.salienceProfile();
    }

    @Override
    public float computeTopicBoost(String text) {
        if (text == null || text.isBlank()) return 1.0f;
        SalienceProfile profile = salienceProfile();
        if (profile == null || !profile.hasInterests()) return 1.0f;

        float[] embedding = embeddingProvider.embed(text).vector();
        return profile.computeTopicBoost(embedding);
    }

    @Override
    public float computeSelfRelevanceBoost(String text) {
        if (text == null || text.isBlank()) return 1.0f;
        SalienceProfile profile = salienceProfile();
        if (profile == null || !profile.hasPersona()) return 1.0f;

        float[] embedding = embeddingProvider.embed(text).vector();
        return profile.computeSelfRelevanceBoost(embedding);
    }

    // ==============================================================
    // ADMIN INTERFACE
    // ==============================================================

    @Override public SpectorMemoryAdmin admin() { return this; }

    // ==============================================================
    // SUBSYSTEM ACCESSORS (implements both SpectorMemory + SpectorMemoryAdmin)
    // ==============================================================

    @Override public CoActivationMemory coActivation() { return coActivationTracker; }
    @Override public MemoryWal wal() { return wal; }
    @Override public ProspectiveScheduler prospective() { return prospectiveScheduler; }
    @Override public com.spectrayan.spector.memory.scheduler.MemoryScheduler scheduler() { return memoryScheduler; }
    @Override public SuppressionSet suppression() { return suppressionSet; }
    @Override public HabituationPenalty habituation() { return habituationPenalty; }
    @Override public ScalarQuantizer quantizer() { return quantizer; }
    @Override public RememberPathway rememberPathway() { return rememberPathway; }
    @Override public RecallPathway recallPathway() { return recallPathway; }
    public boolean sharedPathways() { return sharedPathways; }
    @Override public CognitiveMemoryRouter cognitiveRouter() { return partitionManager.cognitiveRouter(); }
    public PartitionManager partitionManager() { return partitionManager; }
    @Override public com.spectrayan.spector.memory.cortex.PartitionRegistry partitionRegistry() { return partitionManager; }
    @Override public MemoryIndex index() { return index; }

    /** Provenance region of this namespace, or {@code null}. Package-private: for lineage tests. */
    com.spectrayan.spector.kernel.store.ProvenanceMemory provenanceMemory() { return provenanceMemory; }
    @Override public LateralEvaluator lateralEvaluator() { return lateralEvaluator; }
    @Override public CognitiveGraphFacade graph() { return graphFacade; }
    @Override public EntityDirectory entityDirectory() { return entityDirectory; }
    @Override public com.spectrayan.spector.kernel.store.InsulaMemory insularCortex() { return insularCortex; }
    @Override public com.spectrayan.spector.index.VectorIndex semanticIndex() { return semanticIndex; }
    @Override public TemporalKnowledgeGraph temporalKnowledgeGraph() { return temporalKnowledgeGraph; }
    @Override public HyperEntityGraphMemory hyperEntityGraph() { return hyperEntityGraph; }
    public DreamPathway dreamPathway() { return dreamPathway; }
    public WanderPathway wanderPathway() { return wanderPathway; }
    @Override public com.spectrayan.spector.memory.index.IndexPlaneCoordinator indexPlaneCoordinator() { return indexPlaneCoordinator; }
    @Override public com.spectrayan.spector.memory.index.IndexReconcileEngine indexReconcileEngine() { return indexReconcileEngine; }

    public void bindRecallSignalContext(com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal signal) {
        if (signal == null) return;

        if (this.namespaceId != null) {
            signal.attributes().put(com.spectrayan.spector.commons.observation.MemoryObservationHook.TAG_NAMESPACE, this.namespaceId);
            signal.attributes().put("namespace", this.namespaceId);
        }

        var ctxBuilder = signal.context() != null
                ? com.spectrayan.spector.commons.pathway.DefaultPathwayContext.from(signal.context())
                : com.spectrayan.spector.commons.pathway.DefaultPathwayContext.builder();
        if (this.partitionManager != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.cortex.PartitionRegistry.class, this.partitionManager);
        }
        if (this.bm25Index != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.cortex.MemoryBM25Index.class, this.bm25Index);
        }
        if (this.hebbianGraph != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.store.HebbianGraphBase.class, this.hebbianGraph);
        }
        if (this.temporalChain != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.store.TemporalChainMemory.class, this.temporalChain);
        }
        if (this.hyperEntityGraph != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.store.HyperEntityGraphMemory.class, this.hyperEntityGraph);
        }
        if (this.suppressionSet != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet.class, this.suppressionSet);
        }
        if (this.habituationPenalty != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty.class, this.habituationPenalty);
        }
        if (this.prospectiveScheduler != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler.class, this.prospectiveScheduler);
        }
        if (this.coActivationTracker != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.store.CoActivationMemory.class, this.coActivationTracker);
        }
        if (this.temporalKnowledgeGraph != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph.class, this.temporalKnowledgeGraph);
        }
        if (this.entityDirectory != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.graph.EntityDirectory.class, this.entityDirectory);
        }
        if (this.index != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.cortex.index.MemoryIndex.class, this.index);
        }
        if (this.rememberPathway != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.memory.pathway.SoulVersionSource.class, this.rememberPathway);
        }
        if (this.quantizer != null) {
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.core.quantization.ScalarQuantizer.class, this.quantizer);
        }
        signal.bind(ctxBuilder.build());
    }

    //  listAll implementations 

    @Override
    public List<CognitiveRecord> listAll() {
        var results = new ArrayList<CognitiveRecord>();
        for (var entry : index.locationMap().entrySet()) {
            CognitiveRecord record = inspect(entry.getKey());
            if (record != null && !record.isTombstoned()) {
                results.add(record);
            }
        }
        return List.copyOf(results);
    }

    @Override
    public List<CognitiveRecord> listAll(MemoryType tier, int offset, int limit) {
        var results = new ArrayList<CognitiveRecord>();
        int skipped = 0;
        for (var entry : index.locationMap().entrySet()) {
            if (results.size() >= limit) break;
            MemoryLocation loc = entry.getValue();
            if (loc.type() != tier) continue;
            CognitiveRecord record = inspect(entry.getKey());
            if (record != null && !record.isTombstoned()) {
                if (skipped < offset) { skipped++; continue; }
                results.add(record);
            }
        }
        return List.copyOf(results);
    }

    @Override
    public GraphEnrichmentEngine graphEnricher() {
        return graphEnrichmentEngine;
    }

    /** Returns the namespace manager (null if IN_MEMORY mode). */
    public SpectorNamespaceManager namespaceManager() { return namespaceManager; }

    @Override
    public String namespaceId() {
        return namespaceId;
    }

    @Override
    public AutoCloseable acquireLease() {
        activeLeases.incrementAndGet();
        return this::releaseLease;
    }

    public void releaseLease() {
        activeLeases.decrementAndGet();
    }

    public boolean hasActiveLeases() {
        return activeLeases.get() > 0;
    }

    // ==============================================================
    // VACUUM / COMPACTION
    // ==============================================================

    private final ReentrantLock vacuumLock = new ReentrantLock();

    @Override
    public CompactionResult vacuum(MemoryType tier) {
        return vacuum(tier, true);
    }

    @Override
    public CompactionResult vacuum(MemoryType tier, boolean force) {
        mutationPolicy.checkDeletion(
                com.spectrayan.spector.memory.policy.DeletionRequest.vacuum(namespaceId));
        vacuumLock.lock();
        try {
            long startMs = System.currentTimeMillis();
            int[] totals = new int[3]; // [before, after, tombstones]
            long[] bytesReclaimed = new long[1];
            boolean[] anyCompacted = new boolean[1];
            boolean[] anyFound = new boolean[1];

            partitionManager.withRollLock(() -> {
                List<com.spectrayan.spector.memory.cortex.PartitionHandle> handles = partitionManager.snapshot();
                MemoryType[] tiersToCompact = (tier != null)
                        ? new MemoryType[] { tier }
                        : MemoryType.values();

                float threshold = (liveMemoryPatch != null)
                        ? liveMemoryPatch.vacuumThreshold()
                        : VacuumCompactor.DEFAULT_THRESHOLD;

                for (MemoryType t : tiersToCompact) {
                    for (com.spectrayan.spector.memory.cortex.PartitionHandle h : handles) {
                        EngramRegion store = h.router().get(t);
                        if (store == null) continue;

                        CompactionResult r = VacuumCompactor.compact(
                                store, t, h.seq(), index,
                                s -> {
                                    if (graphFacade != null) {
                                        graphFacade.detachMemory(s);
                                    }
                                },
                                threshold,
                                force
                        );
                        if (r != null) {
                            anyFound[0] = true;
                            totals[0] += r.beforeCount();
                            totals[1] += r.afterCount();
                            totals[2] += r.tombstonesRemoved();
                            bytesReclaimed[0] += r.bytesReclaimed();
                            if (r.compacted()) {
                                anyCompacted[0] = true;
                                // Tombstoned payloads in this region are gone and their offsets reused.
                                index.markTombstonePayloadsCompacted(t, h.seq());
                            }
                        }
                    }
                }
            });

            if (!anyFound[0]) {
                return null;
            }

            if (anyCompacted[0]) {
                persistTombstones();
                if (graphFacade != null) {
                    for (int s : index.tombstonedGraphSlots()) {
                        graphFacade.detachMemory(s);
                    }
                }
                if (indexReconcileEngine != null) {
                    indexReconcileEngine.reconcile();
                }
                partitionManager.flushGlobalState();
            }

            long durationMs = System.currentTimeMillis() - startMs;
            MemoryType resultType = (tier != null) ? tier : MemoryType.SEMANTIC;
            return anyCompacted[0]
                    ? new CompactionResult(resultType, totals[0], totals[1], totals[2], bytesReclaimed[0], durationMs, true)
                    : CompactionResult.census(resultType, totals[0], totals[1], totals[2], durationMs);
        } finally {
            vacuumLock.unlock();
        }
    }

    @Override
    public CompactionResult survey(MemoryType tier) {
        vacuumLock.lock();
        try {
            long startMs = System.currentTimeMillis();
            List<com.spectrayan.spector.memory.cortex.PartitionHandle> handles = partitionManager.snapshot();
            int totalBefore = 0;
            int totalAfter = 0;
            int totalTombstones = 0;
            boolean anyFound = false;

            MemoryType[] tiersToSurvey = (tier != null)
                    ? new MemoryType[] { tier }
                    : MemoryType.values();

            for (MemoryType t : tiersToSurvey) {
                for (com.spectrayan.spector.memory.cortex.PartitionHandle h : handles) {
                    EngramRegion store = h.router().get(t);
                    if (store == null) continue;
                    CompactionResult r = VacuumCompactor.survey(store, t);
                    if (r != null) {
                        anyFound = true;
                        totalBefore += r.beforeCount();
                        totalAfter += r.afterCount();
                        totalTombstones += r.tombstonesRemoved();
                    }
                }
            }

            if (!anyFound) return null;
            long durationMs = System.currentTimeMillis() - startMs;
            MemoryType resultType = (tier != null) ? tier : MemoryType.SEMANTIC;
            return CompactionResult.census(resultType, totalBefore, totalAfter, totalTombstones, durationMs);
        } finally {
            vacuumLock.unlock();
        }
    }

    @Override
    public java.util.Map<MemoryType, Float> tombstoneRatios() {
        CognitiveMemoryRouter router = partitionManager.cognitiveRouter();
        java.util.Map<MemoryType, Float> ratios = new java.util.EnumMap<>(MemoryType.class);
        for (MemoryType type : MemoryType.values()) {
            EngramRegion store = router.get(type);
            if (store != null) {
                ratios.put(type, store.tombstoneRatio());
            }
        }
        return ratios;
    }

    @Override
    public int assertFact(String subject, String predicate, String object,
                          long validFrom, long validTo, float confidence) {
        // Default: auto-supersession enabled
        return assertFact(subject, predicate, object, validFrom, validTo, confidence, false);
    }

    @Override
    public int assertFact(String subject, String predicate, String object,
                          long validFrom, long validTo, float confidence,
                          boolean allowCoexisting) {
        acquireLease();
        try {
            int subjectId = entityDirectory.intern(subject, "UNKNOWN");
            int objectId = entityDirectory.intern(object, "UNKNOWN");
            return temporalKnowledgeGraph.assertFactWithSupersession(
                    subjectId, predicate, objectId, -1L, (short) 0,
                    validFrom, validTo, confidence, false, allowCoexisting);
        } finally {
            releaseLease();
        }
    }

    @Override
    public int retractFact(int factId) {
        acquireLease();
        try {
            return temporalKnowledgeGraph.retractFact(factId);
        } finally {
            releaseLease();
        }
    }

    @Override
    public boolean associateHebbian(String sourceMemoryId, String targetMemoryId, float weight) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
            acquireLease();
            try {
                if (index == null || graphFacade == null) {
                    return false;
                }
                var locA = index.locate(sourceMemoryId);
                var locB = index.locate(targetMemoryId);
                if (locA == null || locB == null) {
                    return false;
                }
                int slotA = locA.graphSlot() >= 0 ? locA.graphSlot() : (int) (locA.offset() / 164);
                int slotB = locB.graphSlot() >= 0 ? locB.graphSlot() : (int) (locB.offset() / 164);
                return graphFacade.associateHebbian(slotA, slotB, weight);
            } finally {
                releaseLease();
            }
        }
    }

    @Override
    public boolean associateTemporal(String sourceMemoryId, String targetMemoryId, int sessionId) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
            acquireLease();
            try {
                if (index == null || graphFacade == null) {
                    return false;
                }
                var locA = index.locate(sourceMemoryId);
                var locB = index.locate(targetMemoryId);
                if (locA == null || locB == null) {
                    return false;
                }
                int slotA = locA.graphSlot() >= 0 ? locA.graphSlot() : (int) (locA.offset() / 164);
                int slotB = locB.graphSlot() >= 0 ? locB.graphSlot() : (int) (locB.offset() / 164);
                return graphFacade.associateTemporal(slotA, slotB, sessionId);
            } finally {
                releaseLease();
            }
        }
    }

    @Override
    public int associateHyperedge(List<String> entityNames, String memoryId, float weight) {
        try (var permit = quiesceGuard.acquireWritePermit()) {
            acquireLease();
            try {
                if (entityNames == null || entityNames.size() < 2 || entityDirectory == null || graphFacade == null) {
                    return -1;
                }
                int memorySlot = -1;
                if (memoryId != null && !memoryId.isBlank() && index != null) {
                    var loc = index.locate(memoryId);
                    if (loc != null) {
                        memorySlot = loc.graphSlot() >= 0 ? loc.graphSlot() : (int) (loc.offset() / 164);
                    }
                }
                int[] entityIds = new int[entityNames.size()];
                for (int i = 0; i < entityNames.size(); i++) {
                    int eid = entityDirectory.intern(entityNames.get(i), "UNKNOWN");
                    entityIds[i] = eid;
                    if (memorySlot >= 0) {
                        entityDirectory.linkEntityToMemory(eid, memorySlot);
                    }
                }
                return graphFacade.associateHyperedge(entityIds, memorySlot, weight);
            } finally {
                releaseLease();
            }
        }
    }

    @Override
    public List<TemporalFact> factsAbout(String entityName, Instant asOf) {
        acquireLease();
        try {
            int entityId = entityDirectory.intern(entityName, "UNKNOWN");
            return temporalKnowledgeGraph.factsAbout(entityId)
                    .validAt(asOf)
                    .resolve();
        } finally {
            releaseLease();
        }
    }

    @Override
    public FactHistory factHistory(String subject, String predicate) {
        acquireLease();
        try {
            int subjectId = entityDirectory.intern(subject, "UNKNOWN");
            int predicateId = temporalKnowledgeGraph.predicateRegistry().getOrRegister(predicate);
            java.util.Set<Integer> retracted = temporalKnowledgeGraph.retractedFactIds();
            List<TemporalFact> allVersions = temporalKnowledgeGraph.factHistory(subjectId, predicateId);

            if (allVersions.isEmpty()) {
                return FactHistory.empty(subject, predicate);
            }

            // Separate active from superseded
            FactHistory.FactSnapshot activeFact = null;
            List<FactHistory.FactSnapshot> superseded = new java.util.ArrayList<>();

            for (TemporalFact fact : allVersions) {
                boolean isRetracted = retracted.contains(fact.factId());
                String objectName = resolveEntityName(fact.objectEntityId());

                FactHistory.FactSnapshot snapshot = new FactHistory.FactSnapshot(
                        fact.factId(), objectName,
                        fact.validFrom(), fact.validTo(),
                        fact.txTime(), fact.confidence(),
                        isRetracted ? findSupersedingFactId(allVersions, fact, retracted) : -1);

                if (!isRetracted && activeFact == null) {
                    activeFact = snapshot;  // newest non-retracted = active
                } else {
                    superseded.add(snapshot);
                }
            }

            return new FactHistory(subject, predicate, activeFact, superseded, allVersions.size());
        } finally {
            releaseLease();
        }
    }

    /**
     * Finds the fact ID that superseded (retracted) the given fact.
     */
    private int findSupersedingFactId(List<TemporalFact> allVersions, TemporalFact retractedFact,
                                       java.util.Set<Integer> retractedIds) {
        // The superseding fact is the next non-retracted fact in time order (allVersions is newest-first)
        for (int i = allVersions.indexOf(retractedFact) - 1; i >= 0; i--) {
            TemporalFact candidate = allVersions.get(i);
            if (!retractedIds.contains(candidate.factId())) {
                return candidate.factId();
            }
        }
        return -1;
    }

    /**
     * Resolves an entity ID to its name.
     */
    private String resolveEntityName(int entityId) {
        if (entityId < 0) return null;
        try {
            return entityDirectory.entityName(entityId);
        } catch (Exception e) {
            return "entity:" + entityId;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            log.debug("SpectorMemory.close() already called, skipping");
            return;
        }

        // Deregister shutdown hook to avoid double-flush
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                log.debug("Failed to remove shutdown hook: JVM is already shutting down", e);
            }
        }

        doClose();
    }

    /**
     * Internal close logic  --  called by both {@link #close()} and the JVM shutdown hook.
     * Guarded by the {@code closed} AtomicBoolean so it runs at most once.
     */
    private void doClose() {
        log.info("SpectorMemory closing ({} total memories, mode={})",
                totalMemories(), persistenceMode);

        long shutdownBudgetMs = 10_000L;
        long deadline = System.currentTimeMillis() + shutdownBudgetMs;

        // 1. Stop memory scheduler (stops accepting and drains Quartz jobs)
        if (memoryScheduler != null) {
            try {
                memoryScheduler.close();
            } catch (Exception e) {
                log.warn("Failed to shutdown memoryScheduler on close", e);
            }
        }

        // 2. Close task queues & consolidators
        if (batchConsolidator != null) {
            try {
                batchConsolidator.close();
            } catch (Exception e) {
                log.warn("Failed to close BatchConsolidator on close", e);
            }
        }
        if (eagerConsolidator != null) {
            try {
                eagerConsolidator.close();
            } catch (Exception e) {
                log.warn("Failed to close EagerConsolidator on close", e);
            }
        }

        if (!sharedPathways) {
            if (rememberPathway != null) {
                try {
                    rememberPathway.close();
                } catch (Exception e) {
                    log.warn("Failed to close RememberPathway on close", e);
                }
            }
            if (reflectPathway != null) {
                try {
                    reflectPathway.close();
                } catch (Exception e) {
                    log.warn("Failed to close ReflectPathway on close", e);
                }
            }
            if (wanderPathway != null) {
                try {
                    wanderPathway.close();
                } catch (Exception e) {
                    log.warn("Failed to close WanderPathway on close", e);
                }
            }
            if (decidePathway != null) {
                try {
                    decidePathway.close();
                } catch (Exception e) {
                    log.warn("Failed to close DecidePathway on close", e);
                }
            }
        }

        // 3. Cooperative drain of SPI executors BEFORE touching off-heap arenas (ADR-0026 Section 10)
        long remainingMs = Math.max(100L, deadline - System.currentTimeMillis());
        try {
            var drainResult = SpectorExecutors.drain(namespaceId, Duration.ofMillis(remainingMs));
            if (!drainResult.completed()) {
                log.warn("SpectorExecutors drain incomplete after {} ms (remaining tasks={})",
                        remainingMs, drainResult.remainingTasks());
            }
        } catch (Exception e) {
            log.warn("Exception while draining SpectorExecutors on close", e);
        }

        // 4. Final checkpoint flush before closing storage (while memory segments remain mapped)
        if (checkpointEngine != null) {
            try {
                // Snapshot ProfileAdaptor bandit stats to CoActivationTracker before checkpoint
                if (profileAdaptor != null && coActivationTracker != null) {
                    coActivationTracker.updateBanditStats(profileAdaptor.exportBanditStats());
                }
                checkpointEngine.checkpoint();
            } catch (Exception e) {
                log.warn("Final checkpoint on close failed", e);
            }
        }

        // Persist derived indexes (BM25, SPLADE) into bundle regions
        if (indexPlaneCoordinator != null) {
            try {
                indexPlaneCoordinator.checkpointAll();
            } catch (Exception e) {
                log.warn("Failed checkpoint on IndexPlaneCoordinator during close", e);
            }
        }

        PersistenceManager.flushAndClose(
                persistenceMode, persistencePath,
                partitionManager.activePartitionDir(),
                index, hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph,
                coActivationTracker, temporalKnowledgeGraph, partitionManager.cognitiveRouter(), wal);

        // #443: release frozen partition handles (active router + working already closed
        // by PersistenceManager). Closes the pre-#443 arena/mmap leak.
        partitionManager.close();

        if (insularCortex != null) {
            try {
                insularCortex.close();
            } catch (Exception e) {
                log.warn("Failed to close InsulaMemory on close", e);
            }
        }
        if (indexPlaneCoordinator != null) {
            try {
                indexPlaneCoordinator.close();
            } catch (Exception e) {
                log.warn("Failed to close IndexPlaneCoordinator on close", e);
            }
        }
        if (runtimeBundle != null) {
            try {
                runtimeBundle.close();
            } catch (Exception e) {
                log.warn("Failed to close RuntimeBundle on close", e);
            }
        }
        // Last: everything in the directory is flushed and closed, so the next writer may take it.
        if (persistenceMode == MemoryPersistenceMode.DISK) {
            com.spectrayan.spector.memory.persist.StoreDirectoryLock.release(persistencePath);
        }
    }

    @Override
    public com.spectrayan.spector.memory.aisme.AismeBundle aismeBundle() {
        return aismeBundle;
    }

    /** Returns the quiesce guard coordinating writer permits and checkpoint quiesce lock. */
    public com.spectrayan.spector.memory.sync.QuiesceGuard quiesceGuard() {
        return quiesceGuard;
    }

    // ==============================================================
    // BUILDER
    // ==============================================================

    /** Creates a new builder for configuring and assembling a SpectorMemory instance. */
    public static SpectorMemoryBuilder builder() { return new SpectorMemoryBuilder(); }

    /** Creates a new builder initialized with the given aggregate properties snapshot. */
    public static SpectorMemoryBuilder builder(com.spectrayan.spector.config.SpectorProperties properties) { return SpectorMemoryBuilder.create(properties); }

    /** Creates a new builder initialized with the given memory properties. */
    public static SpectorMemoryBuilder builder(com.spectrayan.spector.config.properties.MemoryProperties properties) { return SpectorMemoryBuilder.create(com.spectrayan.spector.config.SpectorProperties.of(properties)); }
}
