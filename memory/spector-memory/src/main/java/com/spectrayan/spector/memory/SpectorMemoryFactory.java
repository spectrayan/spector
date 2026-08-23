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

import com.spectrayan.spector.commons.concurrent.DaemonSupervisor;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.adaptor.ProfileAdaptor;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.dopamine.DefaultImportanceProvider;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pipeline.AttachmentProcessor;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.memory.insula.InsularCortex;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for assembling the core subsystems of a {@code DefaultSpectorMemory} instance.
 *
 * <p>Constructs and wires the ingestion pipeline, recall pipeline, persistence managers,
 * biological subsystem trackers, cognitive graphs, and search indices from configuration
 * properties in {@link SpectorMemoryBuilder}.</p>
 *
 * <p>The heavy lifting is delegated to a set of single-responsibility subsystem builders
 * (see {@link CognitiveCortexBuilder}, {@link MemoryIndexBuilder},
 * {@link BiologicalSubsystemsBuilder}, {@link CognitiveGraphBuilder},
 * {@link RetrievalIndexBuilder}, {@link CognitiveIngestionTargetBuilder},
 * {@link PartitionManagerBuilder}, {@link RecallPipelineBuilder},
 * {@link DaemonSupervisorBuilder}, {@link MigrationPathResolver} and
 * {@link MemoryWalRecovery}). {@link #assemble} is the orchestrator that invokes them in
 * the correct dependency order and collects the results into a {@link SubsystemBundle}.</p>
 *
 * @since 1.1.0
 */
public final class SpectorMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryFactory.class);

    public record SubsystemBundle(
            CognitiveIngestionTarget cognitiveTarget,
            RememberPathway rememberPathway,
            EmbeddingProvider embeddingProvider,
            RecallPipeline recallPipeline,
            RecallPathway recallPathway,
            ReflectPathway reflectPathway,
            ExpressPathway expressPathway,
            MemoryIndex index,
            ScalarQuantizer quantizer,
            PartitionManager partitionManager,
            ImportanceProvider importanceProvider,
            ReflectionOrchestrator reflectionOrchestrator,
            ReinforcementHandler reinforcementHandler,
            ValenceTracker valenceTracker,
            CoActivationRecordMemory coActivationTracker,
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
            CheckpointDaemon checkpointDaemon,
            com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon graphEnrichmentDaemon,
            DaemonSupervisor daemonSupervisor,
            MemoryBM25Index bm25Index,
            AttachmentProcessor attachmentProcessor,
            ParallelEmbeddingPipeline parallelPipeline,
            EmbedConfig embedConfig,
            Path resolvedPartitionDir,
            Path basePath,
            SpectorNamespaceManager namespaceManager,
            ProfileAdaptor profileAdaptor,
            RuntimeBundle runtimeBundle,
            InsularCortex insularCortex,
            WanderPathway wanderPathway,
            com.spectrayan.spector.memory.cortex.ContinuityRecordMemory continuityMemory,
            DecidePathway decidePathway
    ) {}

    private SpectorMemoryFactory() {}

    public static SubsystemBundle assemble(SpectorMemoryBuilder builder) {
        if (builder.embeddingProvider == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL,
                    "embeddingProvider is required");
        }
        com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager = builder.cacheManager != null
                ? builder.cacheManager
                : com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager.defaultManager();

        EmbeddingProvider embeddingProvider = com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider.wrap(
                builder.embeddingProvider,
                cacheManager
        );
        ParallelEmbeddingPipeline parallelPipeline = new ParallelEmbeddingPipeline(embeddingProvider);
        EmbedConfig embedConfig = new EmbedConfig(builder.embedBatchSize, 3);

        //  Storage + cortex foundation (path, quantizer, namespace, partitions, tier stores) 
        CognitiveCortexBuilder.CortexFoundation cortex = CognitiveCortexBuilder.build(builder);

        //  Memory Index 
        MemoryIndex index = MemoryIndexBuilder.build(cortex);

        //  WAL 
        MemoryWal wal;
        if (cortex.isDisk() && cortex.basePath() != null) {
            wal = new MemoryWal(StorageLayout.walDir(cortex.basePath()));
        } else {
            wal = new MemoryWal();
        }

        //  Biological subsystem trackers 
        BiologicalSubsystemsBuilder.BiologicalSubsystems bio =
                BiologicalSubsystemsBuilder.build(builder, embeddingProvider, cortex);

        //  3-Layer cognitive graph (+ facade) 
        CognitiveGraphBuilder.CognitiveGraphs graphs =
                CognitiveGraphBuilder.build(builder, cortex, index);
                
        com.spectrayan.spector.memory.graph.OntologyConfig ontConfig = builder.ontologyConfig != null
                ? builder.ontologyConfig
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
        ImportanceProvider importanceProvider = builder.importanceProvider != null
                ? builder.importanceProvider
                : new DefaultImportanceProvider(
                        bio.surpriseDetector(), bio.flashbulbPolicy(), bio.icnuWeights());

        //  Ingestion target 
        int activePartitionIndex = 0;
        CognitiveIngestionTarget cognitiveTarget = CognitiveIngestionTargetBuilder.build(
                builder, cortex, bio, graphs, retrieval, index, wal, activePartitionIndex,
                importanceProvider);

        //  Partition manager (+ #443 frozen-partition registry, roll callback, text resolver) 
        PartitionManager partitionManager = PartitionManagerBuilder.build(
                builder, cortex, retrieval, index, graphs, cognitiveTarget);

        //  WAL Recovery 
        MemoryWalRecovery.recover(wal, cortex.cognitiveRouter(), index, graphs.hebbianGraph(),
                graphs.temporalChain(), graphs.temporalKnowledgeGraph(),
                graphs.entityDirectory(), graphs.hyperEntityGraph(),
                bio.coActivationTracker(), cognitiveTarget, cortex.basePath(), cortex.initialPartitionSeq());
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
        if (builder.salienceProfileProvider != null) {
            SalienceProfile effective = builder.salienceProfileProvider.effectiveProfile();
            if (effective != null) {
                salienceDefault = effective.defaultProfile();
            }
        }
        ProfileAdaptor profileAdaptor = new ProfileAdaptor(salienceDefault);
        if (!bio.coActivationTracker().banditStats().isEmpty()) {
            profileAdaptor.loadStats(bio.coActivationTracker().banditStats());
        }

        //  Recall Pipeline (semantic strategy + HNSW rebuild + history + pipeline + listeners) 
        RecallPipeline recallPipeline = RecallPipelineBuilder.build(
                builder, embeddingProvider, cortex, bio, graphs, retrieval, index, partitionManager, wal);

        // Active Inference Self-Model Engine (AISME) (#597, #623)
        com.spectrayan.spector.memory.aisme.AismeBundle aismeBundle = null;
        if (builder.aismeConfig != null && builder.aismeConfig.enabled()) {
            com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor vectorAccessor =
                    new com.spectrayan.spector.memory.cortex.CognitiveVectorAccessor(
                            index, partitionManager, cortex.quantizer());
            com.spectrayan.spector.memory.model.SoulContext primarySoul =
                    builder.soul != null ? builder.soul : builder.agentSoul;
            java.util.List<com.spectrayan.spector.memory.model.SoulContext> activeSouls;
            if (builder.soulContexts != null && !builder.soulContexts.isEmpty()) {
                activeSouls = builder.soulContexts;
            } else if (primarySoul != null) {
                activeSouls = java.util.List.of(primarySoul);
            } else {
                activeSouls = java.util.List.of();
            }
            aismeBundle = com.spectrayan.spector.memory.aisme.AismeBuilder.build(
                    builder.aismeConfig,
                    primarySoul,
                    builder.dimensions,
                    cognitiveTarget,
                    vectorAccessor,
                    activeSouls
            );
        }

        //  Recall Pathway (#561 — relay-based engine, opt-in via usePathwayEngine or AISME) 
        RecallPathway recallPathway = null;
        RememberPathway rememberPathway = null;
        if (builder.usePathwayEngine || (aismeBundle != null)) {
            recallPathway = new RecallPathway.Builder()
                    .embeddingProvider(embeddingProvider)
                    .cortex(cortex)
                    .bio(bio)
                    .graphs(graphs)
                    .retrieval(retrieval)
                    .index(index)
                    .partitionManager(partitionManager)
                    .wal(wal)
                    .graphScoringPolicy(builder.graphScoringPolicy)
                    .sparseEmbeddingProvider(builder.SparseEmbeddingProvider)
                    .hook(builder.hook)
                    .semanticIndex(builder.semanticIndex)
                    .aismeBundle(aismeBundle)
                    .build();

            rememberPathway = new RememberPathway.Builder()
                    .cortex(cortex)
                    .bio(bio)
                    .graphs(graphs)
                    .retrieval(retrieval)
                    .index(index)
                    .wal(wal)
                    .activePartitionIndex(activePartitionIndex)
                    .importanceProvider(importanceProvider)
                    .tagExtractor(builder.tagExtractor)
                    .semanticIndex(builder.semanticIndex)
                    .sparseEmbeddingProvider(builder.SparseEmbeddingProvider)
                    .dataEncryptor(builder.dataEncryptor)
                    .entityExtractionParallelism(builder.entityExtractionParallelism)
                    .entityExtractionQueueCapacity(builder.entityExtractionQueueCapacity)
                    .normalizeAtIngest(true)
                    .build();

            if (builder.salienceProfileProvider != null) {
                SalienceProfile effective = builder.salienceProfileProvider.effectiveProfile();
                if (effective != null && !effective.isNeutral()) {
                    rememberPathway.setSalienceProfile(effective);
                }
            }

            partitionManager.setRememberPathway(rememberPathway);
            rememberPathway.setPartitionRollCallback(partitionManager::rollPartition);

            log.info("Cognitive Pathway Engine enabled — recall and remember use relay-based pathways");
        }

        //  Reflect Pathway (#503 / ADR-0007)
        ReflectPathway reflectPathway = ReflectPathway.builder()
                .embeddingProvider(embeddingProvider)
                .textGenerator(builder.LlmProvider)
                .importanceProvider(importanceProvider)
                .policy(builder.circadianPolicy)
                .centroidRouter(builder.dimensions > 0 ? new com.spectrayan.spector.memory.cortex.CentroidRouter(builder.dimensions) : null)
                .hebbianGraph(graphs.hebbianGraph())
                .temporalChain(graphs.temporalChain())
                .entityDirectory(graphs.entityDirectory())
                .hyperEntityGraph(graphs.hyperEntityGraph())
                .wal(wal)
                .typeNormalizer(typeNormalizer)
                .minClusterSize(5)
                .pinSourceEpisodes(builder.pinSourceEpisodes)
                .pinnedQuota(builder.pinnedQuota)
                .soulDriftRefusionEnabled(true)
                .soulDriftRefusionBatchSize(100)
                .temporalRetentionDays(builder.temporalRetentionDays)
                .entityResolutionEnabled(builder.entityResolutionEnabled)
                .entityShadowMode(builder.entityShadowMode)
                .entityCosineThreshold(builder.entityCosineThreshold)
                .cognitiveManifold(aismeBundle != null ? aismeBundle.cognitiveManifold() : null)
                .manifoldConsolidationRelay(aismeBundle != null ? aismeBundle.manifoldConsolidationRelay() : null)
                .mentalStateTracker(aismeBundle != null ? aismeBundle.mentalStateTracker() : null)
                .build();

        // Express Pathway (#602)
        ExpressPathway expressPathway = ExpressPathway.builder().build();

        //  Extracted Components (Deprecated, retained for backward compatibility)
        ReflectionOrchestrator reflectionOrchestrator = new ReflectionOrchestrator(
                bio.reflectDaemon(), graphs.hebbianGraph(), graphs.temporalChain(), graphs.entityDirectory(),
                graphs.hyperEntityGraph(), wal, builder.temporalRetentionDays,
                embeddingProvider, builder.LlmProvider,
                builder.entityResolutionEnabled, builder.entityShadowMode, builder.entityCosineThreshold, typeNormalizer);

        ReinforcementHandler reinforcementHandler = new ReinforcementHandler(
                bio.valenceTracker(), graphs.hebbianGraph(), bio.lateralEvaluator(), recallPipeline,
                wal, builder.twoFactorConfig, profileAdaptor);

        //  ID Generator 
        MemoryIdGenerator idGenerator = builder.idGenerator != null
                ? builder.idGenerator
                : builder.idStrategy.createGenerator();

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
                .aismeConfig(builder.aismeConfig)
                .build();

        //  Decide Pathway (#611 / AISME Phase 11 — Expected Free Energy G(π) Policy Engine)
        DecidePathway decidePathway = (aismeBundle != null && aismeBundle.policyInferenceEngine() != null)
                ? DecidePathway.builder()
                        .policyInferenceEngine(aismeBundle.policyInferenceEngine())
                        .build()
                : null;

        //  Daemon Supervisor + Checkpoint Daemon  (DISK mode only)
        DaemonSupervisorBuilder.DaemonBundle daemons = DaemonSupervisorBuilder.build(
                builder, cortex, bio, graphs, index, wal, wanderPathway, partitionManager);

        //  Homeostatic Decay Daemon (#613 / AISME Phase 12 — Continuous Self-Dynamics)
        if (daemons.daemonSupervisor() != null && aismeBundle != null
                && builder.aismeConfig != null && builder.aismeConfig.backgroundDecayEnabled()) {
            var decayDaemon = new com.spectrayan.spector.memory.aisme.dmn.HomeostaticDecayDaemon(
                    aismeBundle.mentalStateTracker(),
                    aismeBundle.homeostaticCore(),
                    builder.aismeConfig.backgroundDecayFactor());
            daemons.daemonSupervisor().schedule(
                    "homeostatic-decay",
                    decayDaemon,
                    java.time.Duration.ofSeconds(Math.max(10, builder.aismeConfig.backgroundDecayIntervalSeconds())),
                    com.spectrayan.spector.commons.concurrent.DaemonPolicy.DEFAULT);
        }

        // Wire the graph facade into the enrichment daemon for cache invalidation
        if (daemons.graphEnrichmentDaemon() != null && graphs.graphFacade() != null) {
            daemons.graphEnrichmentDaemon().setGraphFacade(graphs.graphFacade());
        }

        //  Multimodal Attachment Processor 
        AttachmentProcessor attachmentProcessor;
        if (!builder.sensoryExtractors.isEmpty()) {
            attachmentProcessor = new AttachmentProcessor(builder.sensoryExtractors, builder.assetStore);
            log.info("AttachmentProcessor initialized with {} extractors", builder.sensoryExtractors.size());
        } else {
            attachmentProcessor = null;
        }

        return new SubsystemBundle(
                cognitiveTarget, rememberPathway, embeddingProvider, recallPipeline, recallPathway, reflectPathway, expressPathway, index, cortex.quantizer(),
                partitionManager, importanceProvider, reflectionOrchestrator,
                reinforcementHandler, bio.valenceTracker(), bio.coActivationTracker(),
                bio.suppressionSet(), bio.habituationPenalty(), bio.prospectiveScheduler(),
                bio.introspector(), bio.lateralEvaluator(), wal, graphs.hebbianGraph(), graphs.temporalChain(),
                graphs.temporalKnowledgeGraph(),
                graphs.entityDirectory(), graphs.hyperEntityGraph(), graphs.graphFacade(), idGenerator,
                daemons.checkpointDaemon(), daemons.graphEnrichmentDaemon(), daemons.daemonSupervisor(), retrieval.bm25Index(), attachmentProcessor,
                parallelPipeline, embedConfig, cortex.resolvedPartitionDir(), cortex.basePath(),
                cortex.namespaceManager(), profileAdaptor, cortex.runtimeBundle(), cortex.insularCortex(),
                wanderPathway, cortex.continuityMemory(), decidePathway
        );
    }
}
