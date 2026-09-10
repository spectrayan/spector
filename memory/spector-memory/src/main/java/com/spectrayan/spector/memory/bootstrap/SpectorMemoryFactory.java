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
package com.spectrayan.spector.memory.bootstrap;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.cortex.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.aisme.AismeBuilder;
import com.spectrayan.spector.memory.aisme.AismeBundle;
import com.spectrayan.spector.memory.aisme.dmn.HomeostaticDecayDaemon;
import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor;
import com.spectrayan.spector.kernel.store.ContinuityMemory;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.neuromod.dopamine.DefaultImportanceProvider;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.GraphEnrichmentEngine;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.memory.graph.TypeNormalizer;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.kernel.store.InsulaMemory;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.decide.DecidePathway;
import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.pathway.express.ExpressPathway;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReflectPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.wander.WanderPathway;
import com.spectrayan.spector.memory.persist.MemoryWalRecovery;
import com.spectrayan.spector.memory.persist.MigrationPathResolver;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.pipeline.AttachmentProcessor;
import com.spectrayan.spector.memory.pathway.pipeline.HebbianCoActivationListener;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.pathway.reflect.ReinforcementHandler;
import com.spectrayan.spector.memory.scheduler.jobs.HomeostaticDecayJob;
import com.spectrayan.spector.memory.sync.CheckpointEngine;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

import com.spectrayan.spector.memory.api.ImportanceProvider;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.adaptor.ProfileAdaptor;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.neuromod.dopamine.DefaultImportanceProvider;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pathway.pipeline.AttachmentProcessor;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.store.InsulaMemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Single-responsibility assembly factory for the Spector Cognitive Memory subsystem graph.
 *
 * <p>Extracted from {@link DefaultSpectorMemory} constructor (Phase 6 decomposition) to
 * isolate subsystem construction, dependency wiring, and configuration validation.
 * All subsystem instantiation delegates to dedicated sub-builders (e.g. {@link CognitiveCortexBuilder},
 * {@link BiologicalSubsystemsBuilder}, {@link CognitiveGraphBuilder},
 * {@link RetrievalIndexBuilder},
 * {@link MigrationPathResolver} and
 * {@link MemoryWalRecovery}). {@link #assemble} is the orchestrator that invokes them in
 * the correct dependency order and collects the results into a {@link SubsystemBundle}.</p>
 *
 * @since 1.1.0
 */
public final class SpectorMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryFactory.class);

    public record SubsystemBundle(
            RememberPathway rememberPathway,
            EmbeddingProvider embeddingProvider,
            RecallPathway recallPathway,
            ReflectPathway reflectPathway,
            ExpressPathway expressPathway,
            MemoryIndex index,
            ScalarQuantizer quantizer,
            PartitionManager partitionManager,
            ImportanceProvider importanceProvider,
            ReinforcementHandler reinforcementHandler,
            ValenceTracker valenceTracker,
            CoActivationMemory coActivationTracker,
            SuppressionSet suppressionSet,
            HabituationPenalty habituationPenalty,
            ProspectiveScheduler prospectiveScheduler,
            MemoryIntrospector introspector,
            LateralEvaluator lateralEvaluator,
            MemoryWal wal,
            HebbianGraphBase hebbianGraph,
            TemporalChainMemory temporalChain,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            CognitiveGraphFacade graphFacade,
            MemoryIdGenerator idGenerator,
            CheckpointEngine checkpointEngine,
            GraphEnrichmentEngine graphEnrichmentEngine,
            MemoryBM25Index bm25Index,
            AttachmentProcessor attachmentProcessor,
            ParallelEmbeddingPipeline parallelPipeline,
            EmbedConfig embedConfig,
            Path resolvedPartitionDir,
            Path basePath,
            SpectorNamespaceManager namespaceManager,
            ProfileAdaptor profileAdaptor,
            RuntimeBundle runtimeBundle,
            InsulaMemory insularCortex,
            WanderPathway wanderPathway,
            com.spectrayan.spector.kernel.store.ContinuityMemory continuityMemory,
            DecidePathway decidePathway,
            DreamPathway dreamPathway,
            com.spectrayan.spector.memory.aisme.AismeBundle aismeBundle,
            com.spectrayan.spector.kernel.store.ProvenanceMemory provenanceMemory
    ) {}

    private SpectorMemoryFactory() {}

    public static SubsystemBundle assemble(SpectorMemoryBuilder builder) {
        if (builder.embeddingProvider() == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL,
                    "embeddingProvider is required");
        }
        var memProps = builder.properties() != null && builder.properties().memory() != null
                ? builder.properties().memory()
                : new com.spectrayan.spector.config.properties.MemoryProperties();
        var graphProps = memProps.getGraph() != null
                ? memProps.getGraph()
                : new com.spectrayan.spector.config.properties.GraphProperties();
        var entityProps = graphProps.getEntity() != null
                ? graphProps.getEntity()
                : new com.spectrayan.spector.config.properties.GraphProperties.EntityGraphProperties();
        var remProps = memProps.getRemember() != null
                ? memProps.getRemember()
                : new com.spectrayan.spector.config.properties.RememberProperties();
        var aismeConfig = com.spectrayan.spector.config.properties.AismeProperties.fromProperties(
                memProps.getAisme());
        var twoFactorConfig = com.spectrayan.spector.config.properties.TwoFactorProperties.from(
                memProps.getTwofactor());

        // ── AISME Predictive Coding Fail-Fast (R12.3) ──
        if (aismeConfig != null && aismeConfig.enabled() && aismeConfig.enablePredictiveCoding()
                && memProps.getMaxNamespaces() > 8) {
            int dims = memProps.getDimensions();
            long perNamespaceBytes = (long) (4 - 1) * dims * dims * Float.BYTES;
            double perNamespaceMiB = perNamespaceBytes / (1024.0 * 1024.0);
            throw new SpectorValidationException(ErrorCode.CONFIG_VALUE_INVALID,
                    String.format("PredictiveCodingNetwork allocates %.2f MiB per namespace for tier weights at %d dimensions. "
                            + "AISME predictive coding cannot be enabled when maxNamespaces=%d (> 8). "
                            + "Reduce maxNamespaces <= 8 or set spector.memory.aisme.enable-predictive-coding=false to prevent OOM.",
                            perNamespaceMiB, dims, memProps.getMaxNamespaces()));
        }

        com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager = builder.cacheManager() != null
                ? builder.cacheManager()
                : com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager.defaultManager();

        EmbeddingProvider embeddingProvider = com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider.wrap(
                builder.embeddingProvider(),
                cacheManager
        );
        boolean sequential = builder.properties() != null
                && builder.properties().provider() != null
                && builder.properties().provider().getEmbedding() != null
                && builder.properties().provider().getEmbedding().isSequential();
        ParallelEmbeddingPipeline parallelPipeline = builder.parallelEmbeddingPipeline() != null
                ? builder.parallelEmbeddingPipeline()
                : new ParallelEmbeddingPipeline(embeddingProvider, sequential);
        int batchSize = (builder.properties() != null && builder.properties().provider() != null
                && builder.properties().provider().getEmbedding() != null)
                ? builder.properties().provider().getEmbedding().getBatchSize() : 32;
        if (batchSize <= 0) batchSize = 32;
        EmbedConfig embedConfig = new EmbedConfig(batchSize, 3, sequential);

        //  Storage + cortex foundation (path, quantizer, namespace, partitions, tier stores) 
        CognitiveCortexBuilder.CortexFoundation cortex = CognitiveCortexBuilder.build(builder);

        //  Memory Index 
        MemoryIndex index = MemoryIndexBuilder.build(cortex);

        //  WAL 
        MemoryWal wal;
        if (cortex.isDisk() && cortex.basePath() != null) {
            wal = new MemoryWal(StoragePaths.walDir(cortex.basePath()));
        } else {
            wal = new MemoryWal();
        }

        //  Biological subsystem trackers 
        BiologicalSubsystemsBuilder.BiologicalSubsystems bio =
                BiologicalSubsystemsBuilder.build(builder, embeddingProvider, cortex);

        //  3-Layer cognitive graph (+ facade) 
        CognitiveGraphBuilder.CognitiveGraphs graphs =
                CognitiveGraphBuilder.build(builder, cortex, index);
                
        com.spectrayan.spector.memory.graph.OntologyConfig ontConfig = builder.ontologyConfig() != null
                ? builder.ontologyConfig()
                : com.spectrayan.spector.memory.graph.OntologyConfig.defaultInstance();
        com.spectrayan.spector.memory.graph.TypeNormalizer typeNormalizer = null;
        if (ontConfig != null) {
            typeNormalizer = new com.spectrayan.spector.memory.graph.TypeNormalizer(ontConfig);
            if (graphs.entityExtractor() instanceof com.spectrayan.spector.memory.graph.LlmEntityExtractor llmExtractor) {
                llmExtractor.setTypeNormalizer(typeNormalizer);
            }
        }

        //  Retrieval indices (BM25 + SPLADE + ColBERT) 
        RetrievalIndexBuilder.RetrievalIndices retrieval =
                RetrievalIndexBuilder.build(builder, cortex, index);

        //  Importance Provider (#481 SPI) 
        ImportanceProvider importanceProvider = builder.importanceProvider() != null
                ? builder.importanceProvider()
                : new DefaultImportanceProvider(
                        bio.surpriseDetector(), bio.flashbulbPolicy(), bio.icnuWeights());

        //  Ingestion target (RememberPathway) 
        int activePartitionIndex = 0;
        RememberPathway rememberPathway = new RememberPathway.Builder()
                .namespaceId(builder.namespaceId())
                .cortex(cortex)
                .bio(bio)
                .graphs(graphs)
                .retrieval(retrieval)
                .index(index)
                .wal(wal)
                .activePartitionIndex(activePartitionIndex)
                .importanceProvider(importanceProvider)
                .tagExtractor(builder.tagExtractor())
                .semanticIndex(builder.semanticIndex())
                .sparseEmbeddingProvider(builder.sparseEmbeddingProvider())
                .dataEncryptor(builder.dataEncryptor())
                .entityExtractionParallelism(memProps.getEntityExtractionParallelism())
                .entityExtractionQueueCapacity(memProps.getEntityExtractionQueueCapacity())
                .normalizeAtIngest(true)
                .build();

        if (builder.salienceProfileProvider() != null) {
            SalienceProfile effective = builder.salienceProfileProvider().effectiveProfile();
            if (effective != null && !effective.isNeutral()) {
                rememberPathway.setSalienceProfile(effective);
            }
        }

        //  Partition manager (+ #443 frozen-partition registry, roll callback, text resolver) 
        PartitionManager partitionManager = PartitionManagerBuilder.build(
                builder, cortex, retrieval, index, graphs, rememberPathway);

        partitionManager.setRememberPathway(rememberPathway);
        rememberPathway.setPartitionRollCallback(partitionManager::rollPartition);

        //  WAL Recovery 
        com.spectrayan.spector.kernel.bundle.RegionRef ckptRef = cortex.useBundleMode() && cortex.runtimeBundle() != null
                ? cortex.runtimeBundle().checkpointRef()
                : null;
        MemoryWalRecovery.recover(wal, cortex.cognitiveRouter(), index, graphs.hebbianGraph(),
                graphs.temporalChain(), graphs.temporalKnowledgeGraph(),
                graphs.entityDirectory(), graphs.hyperEntityGraph(),
                bio.coActivationTracker(), rememberPathway, cortex.basePath(), cortex.initialPartitionSeq(),
                ckptRef);
        // ADR-0003 #456 (P2): the EntityDirectory is now the authoritative identity store, WAL-bound
        // and recovered directly (WalRecoveryDispatcher GRAPH_ADD_NODE/LINK repointed to it).
        if (wal != null) {
            if (graphs.entityDirectory() != null) {
                graphs.entityDirectory().bindWal(wal);
            }
            // ADR-0003 #460 / #417: bind the hypergraph so hyperedges are durable between checkpoints.
            if (graphs.hyperEntityGraph() != null) {
                graphs.hyperEntityGraph().bindWal(wal);
            }
            if (graphs.hebbianGraph() instanceof HebbianGraphMemory hgm) {
                hgm.bindWal(wal);
            }
        }

        //  ProfileAdaptor (Contextual Bandit) 
        CognitiveProfile salienceDefault = null;
        if (builder.salienceProfileProvider() != null) {
            SalienceProfile effective = builder.salienceProfileProvider().effectiveProfile();
            if (effective != null) {
                salienceDefault = effective.defaultProfile();
            }
        }
        ProfileAdaptor profileAdaptor = new ProfileAdaptor(salienceDefault);
        if (!bio.coActivationTracker().banditStats().isEmpty()) {
            profileAdaptor.loadBanditStats(bio.coActivationTracker().banditStats());
        }

        // Active Inference Self-Model Engine (AISME) (#597, #623)
        com.spectrayan.spector.memory.aisme.AismeBundle aismeBundle = null;
        if (aismeConfig != null && aismeConfig.enabled()) {
            com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor vectorAccessor =
                    new com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor(
                            index, partitionManager, cortex.quantizer());
            com.spectrayan.spector.memory.model.SoulContext primarySoul =
                    builder.soul() != null ? builder.soul() : builder.agentSoul();
            java.util.List<com.spectrayan.spector.memory.model.SoulContext> activeSouls;
            if (builder.soulContexts() != null && !builder.soulContexts().isEmpty()) {
                activeSouls = builder.soulContexts();
            } else if (primarySoul != null) {
                activeSouls = java.util.List.of(primarySoul);
            } else {
                activeSouls = java.util.List.of();
            }
            aismeBundle = com.spectrayan.spector.memory.aisme.AismeBuilder.build(
                    aismeConfig,
                    primarySoul,
                    memProps.getDimensions(),
                    rememberPathway,
                    vectorAccessor,
                    activeSouls
            );
        }

        //  Recall Pathway (#561 — relay-based engine) 
        RecallPathway recallPathway = new RecallPathway.Builder()
                .embeddingProvider(embeddingProvider)
                .cortex(cortex)
                .bio(bio)
                .graphs(graphs)
                .retrieval(retrieval)
                .index(index)
                .partitionManager(partitionManager)
                .wal(wal)
                .graphScoringPolicy(builder.graphScoringPolicy())
                .sparseEmbeddingProvider(builder.sparseEmbeddingProvider())
                .hook(builder.hook())
                .semanticIndex(builder.semanticIndex())
                .aismeBundle(aismeBundle)
                .salienceProfile(builder.salienceProfile())
                .salienceProfileProvider(builder.salienceProfileProvider())
                .build();

        if (bio.coActivationTracker() != null) {
            recallPathway.addListener(new com.spectrayan.spector.memory.pathway.pipeline.HebbianCoActivationListener(bio.coActivationTracker()));
        }

        if (builder.semanticIndex() != null && !builder.semanticIndex().isReadOnly() && builder.semanticIndex().size() == 0) {
            rebuildHnswIfNeeded(builder, partitionManager, index, cortex.quantizer());
        }

        //  ID Generator (moved up so ReflectPathway can use it)
        MemoryIdGenerator idGenerator = builder.idGenerator() != null
                ? builder.idGenerator()
                : (memProps.getIdStrategy() != null && !memProps.getIdStrategy().isBlank()
                ? com.spectrayan.spector.kernel.id.IdStrategy.valueOf(memProps.getIdStrategy().toUpperCase(java.util.Locale.ROOT)).createGenerator()
                : com.spectrayan.spector.kernel.id.IdStrategy.TSID.createGenerator());

        //  Reflect Pathway (#503 / ADR-0007)
        ReflectPathway reflectPathway = ReflectPathway.builder()
                .embeddingProvider(embeddingProvider)
                .textGenerator(builder.llmProvider())
                .importanceProvider(importanceProvider)
                .policy(memProps.getCircadian())
                .centroidRouter(memProps.getDimensions() > 0 ? new com.spectrayan.spector.memory.cortex.CentroidRouter(memProps.getDimensions()) : null)
                .hebbianGraph(graphs.hebbianGraph())
                .temporalChain(graphs.temporalChain())
                .entityDirectory(graphs.entityDirectory())
                .hyperEntityGraph(graphs.hyperEntityGraph())
                .wal(wal)
                .typeNormalizer(typeNormalizer)
                .minClusterSize(5)
                .pinSourceEpisodes(remProps.isPinSourceEpisodes())
                .pinnedQuota(remProps.getPinnedQuota())
                .soulDriftRefusionEnabled(true)
                .soulDriftRefusionBatchSize(100)
                .temporalRetentionDays(entityProps.getRetentionDays())
                .entityResolutionEnabled(entityProps.isResolutionEnabled())
                .entityShadowMode(entityProps.isShadowMode())
                .entityCosineThreshold(entityProps.getCosineThreshold())
                .cognitiveManifold(aismeBundle != null ? aismeBundle.cognitiveManifold() : null)
                .manifoldConsolidationRelay(aismeBundle != null ? aismeBundle.manifoldConsolidationRelay() : null)
                .mentalStateTracker(aismeBundle != null ? aismeBundle.mentalStateTracker() : null)
                .provenanceMemory(cortex.provenanceMemory())
                .idGenerator(idGenerator)
                .build();

        // Express Pathway (#602)
        ExpressPathway expressPathway = ExpressPathway.builder().build();

        ReinforcementHandler reinforcementHandler = new ReinforcementHandler(
                bio.valenceTracker(), graphs.hebbianGraph(), bio.lateralEvaluator(), recallPathway,
                wal, twoFactorConfig, profileAdaptor);

        //  Wander Pathway (#609 / AISME Phase 10 — DMN & Longitudinal Continuity)
        WanderPathway wanderPathway = WanderPathway.builder()
                .quantizer(cortex.quantizer())
                .embeddingProvider(embeddingProvider)
                .mentalStateTracker(aismeBundle != null ? aismeBundle.mentalStateTracker() : null)
                .cognitiveManifold(aismeBundle != null ? aismeBundle.cognitiveManifold() : null)
                .hopfieldNetwork(aismeBundle != null ? aismeBundle.hopfieldNetwork() : null)
                .hebbianGraph(graphs.hebbianGraph())
                .homeostaticCore(aismeBundle != null ? aismeBundle.homeostaticCore() : null)
                .continuityMemory(cortex.continuityMemory())
                .aismeConfig(aismeConfig)
                .build();

        //  Decide Pathway (#611 / AISME Phase 11 — Expected Free Energy G(π) Policy Engine)
        DecidePathway decidePathway = (aismeBundle != null && aismeBundle.policyInferenceEngine() != null)
                ? DecidePathway.builder()
                        .policyInferenceEngine(aismeBundle.policyInferenceEngine())
                        .build()
                : null;

        //  Dream Pathway (#679, #681 / Soul-Conditioned Generative Dreaming)
        com.spectrayan.spector.memory.model.SoulContext dreamPrimarySoul =
                builder.soul() != null ? builder.soul() : builder.agentSoul();
        java.util.List<com.spectrayan.spector.memory.model.SoulContext> dreamActiveSouls;
        if (builder.soulContexts() != null && !builder.soulContexts().isEmpty()) {
            dreamActiveSouls = builder.soulContexts();
        } else if (dreamPrimarySoul != null) {
            dreamActiveSouls = java.util.List.of(dreamPrimarySoul);
        } else {
            dreamActiveSouls = java.util.List.of();
        }

        DreamPathway dreamPathway = DreamPathway.builder()
                .dreamProperties(memProps.getDream())
                .partitionManager(partitionManager)
                .aismeConfig(aismeConfig)
                .primarySoul(dreamPrimarySoul)
                .soulContexts(dreamActiveSouls)
                .salienceProfile(builder.salienceProfile())
                .hebbianGraph(graphs.hebbianGraph())
                .entityDirectory(graphs.entityDirectory())
                .hyperEntityGraph(graphs.hyperEntityGraph())
                .embeddingProvider(embeddingProvider)
                .hopfieldNetwork(aismeBundle != null ? aismeBundle.hopfieldNetwork() : null)
                .idGenerator(idGenerator)
                .build();

        // ── Storage Checkpoint Engine (DISK mode only) ──
        CheckpointEngine checkpointEngine;
        if (cortex.isDisk() && cortex.basePath() != null) {
            if (memProps.getCheckpointIntervalSeconds() > 0) {
                Path bundlePath = cortex.runtimeBundle() != null ? cortex.runtimeBundle().bundlePath() : null;
                checkpointEngine = new CheckpointEngine(
                        cortex.cognitiveRouter(), wal,
                        bundlePath,
                        index, null,
                        graphs.hebbianGraph(), graphs.temporalChain(),
                        graphs.entityDirectory(), graphs.hyperEntityGraph(), bio.coActivationTracker(),
                        graphs.temporalKnowledgeGraph(),
                        cortex.resolvedPartitionDir(), cortex.basePath(), ckptRef);
                if (builder.spectorProperties() != null && builder.spectorProperties().events() != null) {
                    checkpointEngine.setEventBus(com.spectrayan.spector.events.EventBus.broadcast(
                            builder.spectorProperties().events().isAsync()));
                }
            } else {
                checkpointEngine = null;
            }
        } else {
            checkpointEngine = null;
        }

        // ── Graph Enrichment Engine ──
        GraphEnrichmentEngine graphEnrichmentEngine;
        if (graphs.entityExtractor() != null
                && !(graphs.entityExtractor() instanceof com.spectrayan.spector.memory.graph.NoOpEntityExtractor)
                && graphs.entityDirectory() != null) {
            graphEnrichmentEngine = new GraphEnrichmentEngine(
                    builder.namespaceId(),
                    index,
                    graphs.entityExtractor(),
                    graphs.entityDirectory(),
                    graphs.hyperEntityGraph(),
                    graphs.temporalKnowledgeGraph());
            if (graphs.graphFacade() != null) {
                graphEnrichmentEngine.setGraphFacade(graphs.graphFacade());
            }
        } else {
            graphEnrichmentEngine = null;
        }

        // ── Multimodal Attachment Processor ──
        AttachmentProcessor attachmentProcessor;
        if (!builder.sensoryExtractors().isEmpty()) {
            attachmentProcessor = new AttachmentProcessor(builder.sensoryExtractors(), builder.assetStore());
            log.info("AttachmentProcessor initialized with {} extractors", builder.sensoryExtractors().size());
        } else {
            attachmentProcessor = null;
        }

        return new SubsystemBundle(
                rememberPathway, embeddingProvider, recallPathway, reflectPathway, expressPathway, index, cortex.quantizer(),
                partitionManager, importanceProvider,
                reinforcementHandler, bio.valenceTracker(), bio.coActivationTracker(),
                bio.suppressionSet(), bio.habituationPenalty(), bio.prospectiveScheduler(),
                bio.introspector(), bio.lateralEvaluator(), wal, graphs.hebbianGraph(), graphs.temporalChain(),
                graphs.temporalKnowledgeGraph(),
                graphs.entityDirectory(), graphs.hyperEntityGraph(), graphs.graphFacade(), idGenerator,
                checkpointEngine, graphEnrichmentEngine, retrieval.bm25Index(), attachmentProcessor,
                parallelPipeline, embedConfig, cortex.resolvedPartitionDir(), cortex.basePath(),
                cortex.namespaceManager(), profileAdaptor, cortex.runtimeBundle(), cortex.insularCortex(),
                wanderPathway, cortex.continuityMemory(), decidePathway, dreamPathway, aismeBundle,
                cortex.provenanceMemory()
        );
    }
    private static void rebuildHnswIfNeeded(SpectorMemoryBuilder builder, PartitionManager partitionManager, MemoryIndex index, ScalarQuantizer quantizer) {
        if (builder.semanticIndex() == null || builder.semanticIndex().isReadOnly() || builder.semanticIndex().size() > 0) {
            return;
        }
        var partitions = partitionManager.snapshot();
        int totalRebuilt = 0;
        long startMs = System.currentTimeMillis();

        for (var handle : partitions) {
            int partitionSeq = handle.seq();
            var semStore = handle.router() != null ? handle.router().semantic() : null;
            if (semStore == null || semStore.size() == 0) continue;

            int storeSize = semStore.size();
            long baseOffset = semStore.dataOffset();
            int stride = semStore.layout().stride();

            for (int i = 0; i < storeSize; i++) {
                byte[] quantized = semStore.readQuantizedVector(i);
                if (quantized == null) {
                    continue;
                }

                long recordOff = baseOffset + (long) i * stride;
                String id = index.findIdByOffset(partitionSeq, com.spectrayan.spector.kernel.api.MemoryType.SEMANTIC, recordOff);
                if (id != null) {
                    float[] vector = quantizer.decode(quantized);
                    var loc = index.location(id);
                    int graphSlot = (loc != null) ? loc.graphSlot() : i;
                    builder.semanticIndex().add(id, graphSlot, vector);
                    totalRebuilt++;
                }
            }
        }
        if (totalRebuilt > 0) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("HNSW multi-partition rebuild complete: {} vectors indexed across {} partitions in {}ms",
                    totalRebuilt, partitions.size(), elapsed);
        }
    }
}
