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
import com.spectrayan.spector.commons.concurrent.DaemonPolicy;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.adaptor.ProfileAdaptor;
import com.spectrayan.spector.memory.pipeline.RecallHistory;
import com.spectrayan.spector.provider.embedding.EmbedConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.index.BM25Index;
import com.spectrayan.spector.index.ColBERTReranker;
import com.spectrayan.spector.index.ColBERTTokenCache;
import com.spectrayan.spector.memory.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemoryStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.ProceduralMemoryStore;
import com.spectrayan.spector.memory.cortex.SemanticMemoryStore;

import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.cortex.WorkingMemoryStore;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.graph.TypeRegistry;
import com.spectrayan.spector.memory.graph.HyperEntityGraph;
import com.spectrayan.spector.memory.graph.LlmEntityExtractor;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCsr;
import com.spectrayan.spector.memory.hippocampus.ReflectDaemon;
import com.spectrayan.spector.memory.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.pipeline.HebbianCoActivationListener;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.pipeline.LtpReconsolidationListener;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.namespace.SpectorNamespaceManager;
import com.spectrayan.spector.memory.temporal.TemporalChain;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.pipeline.AttachmentProcessor;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import java.nio.file.Files;
import java.util.List;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * Factory class for assembling the core subsystems of a {@code DefaultSpectorMemory} instance.
 *
 * <p>Constructs and wires the ingestion pipeline, recall pipeline, persistence managers,
 * biological subsystem trackers, cognitive graphs, and search indices from configuration
 * properties in {@link SpectorMemoryBuilder}.</p>
 *
 * @since 1.1.0
 */
public final class SpectorMemoryFactory {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryFactory.class);

    public record SubsystemBundle(
            CognitiveIngestionTarget cognitiveTarget,
            EmbeddingProvider embeddingProvider,
            RecallPipeline recallPipeline,
            MemoryIndex index,
            ScalarQuantizer quantizer,
            PartitionManager partitionManager,
            ImportanceEstimator importanceEstimator,
            ReflectionOrchestrator reflectionOrchestrator,
            ReinforcementHandler reinforcementHandler,
            ValenceTracker valenceTracker,
            CoActivationTracker coActivationTracker,
            SuppressionSet suppressionSet,
            HabituationPenalty habituationPenalty,
            ProspectiveScheduler prospectiveScheduler,
            MemoryIntrospector introspector,
            LateralEvaluator lateralEvaluator,
            MemoryWal wal,
            HebbianGraphBase hebbianGraph,
            TemporalChain temporalChain,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            EntityGraph entityGraph,
            HyperEntityGraph hyperEntityGraph,
            CognitiveGraphFacade graphFacade,
            MemoryIdGenerator idGenerator,
            CheckpointDaemon checkpointDaemon,
            DaemonSupervisor daemonSupervisor,
            MemoryBM25Index bm25Index,
            AttachmentProcessor attachmentProcessor,
            ParallelEmbeddingPipeline parallelPipeline,
            EmbedConfig embedConfig,
            Path resolvedPartitionDir,
            Path basePath,
            SpectorNamespaceManager namespaceManager,
            ProfileAdaptor profileAdaptor
    ) {}

    private SpectorMemoryFactory() {}

    public static SubsystemBundle assemble(SpectorMemoryBuilder builder) {
        if (builder.embeddingProvider == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL,
                    "embeddingProvider is required");
        }
        EmbeddingProvider embeddingProvider = builder.embeddingProvider;
        ParallelEmbeddingPipeline parallelPipeline = new ParallelEmbeddingPipeline(embeddingProvider);
        EmbedConfig embedConfig = new EmbedConfig(builder.embedBatchSize, 3);

        boolean isDisk = builder.persistenceMode == MemoryPersistenceMode.DISK;

        //  Resolve persistence path 
        Path basePath;
        if (isDisk && builder.persistencePath != null) {
            basePath = builder.persistencePath;
        } else if (isDisk) {
            try {
                basePath = createSecureTempDirectory("spector-memory-");
                log.warn("DISK persistence mode with no explicit path  --  using secure temp directory: {}", basePath);
            } catch (java.io.IOException e) {
                throw new SpectorValidationException(ErrorCode.INTERNAL_ERROR,
                        "Failed to create secure temp directory", e);
            }
        } else {
            basePath = null;
        }

        //  Quantizer 
        ScalarQuantizer quantizer;
        if (builder.quantizer != null) {
            quantizer = builder.quantizer;
        } else {
            float[] defaultMins = new float[builder.dimensions];
            float[] defaultMaxs = new float[builder.dimensions];
            java.util.Arrays.fill(defaultMins, -1.0f);
            java.util.Arrays.fill(defaultMaxs, 1.0f);
            quantizer = ScalarQuantizer.fromBounds(builder.dimensions, defaultMins, defaultMaxs);
        }


        //  Namespace Manager 
        SpectorNamespaceManager namespaceManager;
        if (isDisk && basePath != null) {
            namespaceManager = new SpectorNamespaceManager(basePath);
            log.info("NamespaceManager initialized: {} namespaces discovered", namespaceManager.count());
        } else {
            namespaceManager = null;
        }

        //  Partition layout 
        int quantizedVecBytes = builder.dimensions;

        Path resolvedPartitionDir = null;
        if (isDisk && basePath != null) {
            try {
                createDirectoriesSecure(StorageLayout.runtimeDir(basePath));
                createDirectoriesSecure(StorageLayout.partitionsDir(basePath));
                resolvedPartitionDir = PartitionManager.discoverOrCreatePartition(basePath);
                log.info("Active partition: {}", resolvedPartitionDir.getFileName());
            } catch (java.io.IOException e) {
                log.error("Failed to initialize partition layout: {}", e.getMessage(), e);
            }
        }

        //  Tier stores 
        TierRouter tierRouter;
        WorkingMemoryStore workingStore;
        if (isDisk && builder.persistWorkingMemory && basePath != null) {
            workingStore = new WorkingMemoryStore(quantizedVecBytes, builder.workingCapacity,
                    StorageLayout.workingMem(basePath));
        } else {
            workingStore = new WorkingMemoryStore(quantizedVecBytes, builder.workingCapacity);
        }

        if (isDisk && basePath != null && resolvedPartitionDir != null) {
            EpisodicMemoryStore episodicStore = new EpisodicMemoryStore(
                    StorageLayout.episodicMem(resolvedPartitionDir),
                    quantizedVecBytes, builder.episodicPartitionCapacity);
            ProceduralMemoryStore proceduralStore = new ProceduralMemoryStore(
                    quantizedVecBytes, builder.proceduralCapacity,
                    StorageLayout.proceduralMem(resolvedPartitionDir));
            SemanticMemoryStore semanticStore = new SemanticMemoryStore(
                    quantizedVecBytes, builder.semanticCapacity,
                    StorageLayout.semanticMem(resolvedPartitionDir));
            tierRouter = new TierRouter(workingStore, episodicStore, semanticStore, proceduralStore);
        } else {
            EpisodicMemoryStore episodicStore = new EpisodicMemoryStore(
                    quantizedVecBytes, builder.episodicPartitionCapacity);
            ProceduralMemoryStore proceduralStore = new ProceduralMemoryStore(
                    quantizedVecBytes, builder.proceduralCapacity);
            SemanticMemoryStore semanticStore = new SemanticMemoryStore(
                    quantizedVecBytes, builder.semanticCapacity);
            tierRouter = new TierRouter(workingStore, episodicStore, semanticStore, proceduralStore);
        }

        //  Memory Index 
        MemoryIndex index;
        if (isDisk && basePath != null) {
            Path runtimeIndex = StorageLayout.indexMidxRuntime(basePath);
            Path partitionIndex = resolvedPartitionDir != null ? resolvedPartitionDir.resolve(StorageLayout.FILE_INDEX) : null;

            Path loadFrom = getNewerPath(runtimeIndex, partitionIndex, null);
            if (loadFrom != null) {
                index = MemoryIndex.load(loadFrom);
                if (loadFrom.equals(partitionIndex)) {
                    log.info("Loaded index from partition (newer than runtime index): {}", loadFrom);
                } else {
                    log.info("Loaded index from runtime: {}", loadFrom);
                }
            } else {
                index = new MemoryIndex();
            }
        } else {
            index = new MemoryIndex();
        }

        //  WAL 
        MemoryWal wal;
        if (isDisk && basePath != null) {
            wal = new MemoryWal(StorageLayout.walDir(basePath));
        } else {
            wal = new MemoryWal();
        }

        //  Biological Subsystems 
        SurpriseDetector surpriseDetector = new SurpriseDetector(builder.surpriseWarmup);
        IcnuWeights icnuWeights = builder.icnuWeights != null ? builder.icnuWeights : IcnuWeights.DEFAULT;
        FlashbulbPolicy flashbulbPolicy = new FlashbulbPolicy(builder.flashbulbThreshold);
        ValenceTracker valenceTracker = new ValenceTracker(builder.valenceLearningRate);

        CoActivationTracker coActivationTracker;
        if (isDisk && basePath != null) {
            coActivationTracker = CoActivationTracker.load(
                    StorageLayout.coactivationTracker(basePath), 10_000, 20_000);
        } else {
            coActivationTracker = new CoActivationTracker();
        }
        SuppressionSet suppressionSet = new SuppressionSet();
        HabituationPenalty habituationPenalty = new HabituationPenalty(0.2f, builder.inhibitionTtlMs, builder.inhibitionFloor);
        ProspectiveScheduler prospectiveScheduler = new ProspectiveScheduler();
        MemoryIntrospector introspector = new MemoryIntrospector(coActivationTracker);
        LateralEvaluator lateralEvaluator = new LateralEvaluator();

        ReflectDaemon reflectDaemon = new ReflectDaemon(
                builder.circadianPolicy,
                builder.dimensions > 0 ? new CentroidRouter(builder.dimensions) : null,
                builder.LlmProvider,
                embeddingProvider,
                5, // minClusterSize
                builder.pinSourceEpisodes,
                builder.pinnedQuota);

        //  3-Layer Cognitive Graph 
        int graphCapacity = builder.hebbianGraphCapacity > 0
                ? builder.hebbianGraphCapacity : builder.episodicPartitionCapacity;

        HebbianGraphBase hebbianGraph;
        if (isDisk && basePath != null) {
            Path runtimeGraph = StorageLayout.hebbianGraphRuntime(basePath);
            Path legacyGraph = basePath.resolve(StorageLayout.FILE_HEBBIAN);
            Path v2Graph = resolvedPartitionDir != null
                    ? StorageLayout.hebbianGraph(resolvedPartitionDir) : null;
            Path loadFrom = getNewerPath(runtimeGraph, v2Graph, legacyGraph);
            if (loadFrom == null) {
                loadFrom = legacyGraph;
            }
            hebbianGraph = HebbianGraphCsr.load(loadFrom, graphCapacity,
                    builder.hebbianMaxDegree, builder.edgeImportance);
        } else {
            hebbianGraph = new HebbianGraphCsr(graphCapacity);
        }

        int temporalCapacity = builder.temporalChainCapacity > 0
                ? builder.temporalChainCapacity : graphCapacity;
        TemporalChain temporalChain;
        if (isDisk && basePath != null) {
            Path runtimeChain = StorageLayout.temporalChainRuntime(basePath);
            Path legacyChain = basePath.resolve(StorageLayout.FILE_TEMPORAL);
            Path v2Chain = resolvedPartitionDir != null
                    ? StorageLayout.temporalChain(resolvedPartitionDir) : null;
            Path loadFrom = getNewerPath(runtimeChain, v2Chain, legacyChain);
            if (loadFrom == null) {
                loadFrom = legacyChain;
            }
            temporalChain = TemporalChain.load(loadFrom, temporalCapacity);
        } else {
            temporalChain = new TemporalChain(temporalCapacity);
        }

        EntityExtractor entityExtractor;
        if (builder.entityExtractionMode == EntityExtractionMode.LLM
                && builder.LlmProvider != null) {
            entityExtractor = new LlmEntityExtractor(
                    builder.LlmProvider,
                    builder.maxEntitiesPerMemory, builder.maxRelationsPerMemory,
                    builder.llmGenerationOptions);
        } else if (builder.entityExtractionMode == EntityExtractionMode.CUSTOM
                && builder.entityExtractor != null) {
            entityExtractor = builder.entityExtractor;
        } else {
            entityExtractor = NoOpEntityExtractor.INSTANCE;
        }

        boolean entityEnabled = builder.entityExtractionMode != EntityExtractionMode.NONE;
        EntityGraph entityGraph;
        if (entityEnabled) {
            int entityCap = builder.entityGraphCapacity;
            int edgeCap = entityCap * builder.entityMaxDegree;
            if (isDisk && basePath != null) {
                Path runtimeEntity = StorageLayout.entityGraphRuntime(basePath);
                Path legacyEntity = basePath.resolve(StorageLayout.FILE_ENTITY);
                Path v2Entity = resolvedPartitionDir != null
                        ? StorageLayout.entityGraph(resolvedPartitionDir) : null;

                Path loadFrom = getNewerPath(runtimeEntity, v2Entity, legacyEntity);
                if (loadFrom != null) {
                    entityGraph = EntityGraph.load(loadFrom, entityCap, edgeCap);
                } else {
                    entityGraph = new EntityGraph(runtimeEntity, entityCap, edgeCap,
                            builder.entityMaxDegree, builder.edgeImportance);
                }
            } else {
                entityGraph = new EntityGraph(entityCap, edgeCap,
                        builder.entityMaxDegree, builder.edgeImportance);
            }
        } else {
            entityGraph = null;
        }

        HyperEntityGraph hyperEntityGraph;
        if (entityEnabled && builder.hyperEntityGraphEnabled) {
            int hyperCap = builder.entityGraphCapacity;
            int hyperEdgeCap = hyperCap * 2;
            if (isDisk && basePath != null) {
                Path hyperPath = StorageLayout.hyperEntityGraphRuntime(basePath);
                if (java.nio.file.Files.exists(hyperPath)) {
                    hyperEntityGraph = HyperEntityGraph.load(hyperPath, hyperCap, hyperEdgeCap);
                } else {
                    hyperEntityGraph = new HyperEntityGraph(hyperCap, hyperEdgeCap);
                }
            } else {
                hyperEntityGraph = new HyperEntityGraph(hyperCap, hyperEdgeCap);
            }
        } else {
            hyperEntityGraph = null;
        }

        TemporalKnowledgeGraph temporalKnowledgeGraph;
        TypeRegistry predRegistry = (entityGraph != null) ? entityGraph.relationTypeRegistry() : new TypeRegistry("relation-type");
        if (isDisk && basePath != null) {
            Path runtimeTkg = StorageLayout.temporalFactsRuntime(basePath);
            long initialSize = 16L * 1024 * 1024; // 16MB
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(runtimeTkg, initialSize, predRegistry);
        } else {
            temporalKnowledgeGraph = new TemporalKnowledgeGraph(predRegistry);
        }

        //  BM25 Text Search 
        MemoryBM25Index bm25Index;
        TextAppendMemory textDataStore;
        int activePartitionIndex = 0;
        if (isDisk && basePath != null && resolvedPartitionDir != null) {
            textDataStore = new TextAppendMemory(StorageLayout.textDat(resolvedPartitionDir), builder.dataEncryptor);
            textDataStore.readAll();
            index.setTextDataStore(textDataStore);

            java.nio.file.Path bm25Path = StorageLayout.bm25BidxRuntime(basePath);
            java.nio.file.Path v2Bm25 = resolvedPartitionDir != null ? StorageLayout.bm25Bidx(resolvedPartitionDir) : null;
            java.nio.file.Path loadFrom = getNewerPath(bm25Path, v2Bm25, null);
            if (loadFrom != null) {
                bm25Path = loadFrom;
            }
            BM25Index loadedBm25 = BM25Index.load(bm25Path);
            bm25Index = new MemoryBM25Index(1);
            if (loadedBm25 != null) {
                bm25Index.setPartition(0, loadedBm25);
                log.info("BM25 loaded from binary index: {} docs", loadedBm25.size());
            } else {
                Map<String, String> allTexts = new java.util.HashMap<>();
                for (var entry : index.locationMap().entrySet()) {
                    String text = index.text(entry.getKey());
                    if (text != null && !text.isEmpty()) {
                        allTexts.put(entry.getKey(), text);
                    }
                }
                if (!allTexts.isEmpty()) {
                    bm25Index.rebuildPartition(0, allTexts);
                    log.info("Rebuilt BM25 index with {} documents from memory index", allTexts.size());
                    bm25Index.partition(0).save(bm25Path);
                }
            }
        } else {
            bm25Index = new MemoryBM25Index(1);
            textDataStore = null;
        }

        //  SPLADE Index 
        com.spectrayan.spector.memory.cortex.MemorySpladeIndex memorySpladeIndex = null;
        if (builder.SparseEmbeddingProvider != null) {
            memorySpladeIndex = new com.spectrayan.spector.memory.cortex.MemorySpladeIndex(1);
            log.info("SPLADE index enabled: provider={}", builder.SparseEmbeddingProvider.modelName());
        }

        //  ColBERT Reranker 
        ColBERTReranker colbertReranker = null;
        if (builder.tokenEmbeddingProvider != null) {
            ColBERTTokenCache tokenCache = new ColBERTTokenCache(
                    builder.tokenEmbeddingProvider.tokenDimensions(), 10_000);
            colbertReranker = new ColBERTReranker(builder.tokenEmbeddingProvider, tokenCache);
            log.info("ColBERT reranker enabled: provider={}, tokenDims={}",
                    builder.tokenEmbeddingProvider.modelName(),
                    builder.tokenEmbeddingProvider.tokenDimensions());
        }

        //  Ingestion Target 
        CognitiveIngestionTarget cognitiveTarget = new CognitiveIngestionTarget(
                quantizer, surpriseDetector, flashbulbPolicy,
                tierRouter, index, wal, workingStore, builder.icnuWeights,
                builder.semanticIndex, builder.tagExtractor, true,
                hebbianGraph, temporalChain, entityExtractor, entityGraph,
                hyperEntityGraph,
                bm25Index, textDataStore, activePartitionIndex,
                memorySpladeIndex, builder.SparseEmbeddingProvider,
                builder.dataEncryptor);

        //  Wire Salience Profile Provider 
        if (builder.salienceProfileProvider != null) {
            SalienceProfile effective = builder.salienceProfileProvider.effectiveProfile();
            if (effective != null && !effective.isNeutral()) {
                cognitiveTarget.setSalienceProfile(effective);
                log.info("Salience profile applied: interests={}, disinterests={}, icnuOverride={}",
                        effective.interests().size(), effective.disinterests().size(),
                        effective.hasIcnuOverride());
            }
        }

        //  Partition Manager 
        PartitionManager partitionManager;
        if (isDisk) {
            partitionManager = new PartitionManager(
                    basePath, quantizedVecBytes, builder.semanticCapacity,
                    builder.episodicPartitionCapacity, builder.proceduralCapacity,
                    tierRouter, resolvedPartitionDir,
                    index, hebbianGraph, temporalChain, cognitiveTarget);
            cognitiveTarget.setPartitionRollCallback(partitionManager::rollPartition);
        } else {
            partitionManager = new PartitionManager(
                    null, quantizedVecBytes, builder.semanticCapacity,
                    builder.episodicPartitionCapacity, builder.proceduralCapacity,
                    tierRouter, null,
                    index, hebbianGraph, temporalChain, cognitiveTarget);
        }

        //  WAL Recovery 
        performWalRecovery(wal, tierRouter, index, hebbianGraph, temporalChain, temporalKnowledgeGraph, entityGraph, coActivationTracker, cognitiveTarget, basePath);

        //  Semantic Recall Strategy + HNSW Rebuild 
        SemanticRecallStrategy semanticStrategy = null;
        if (builder.semanticIndex != null && tierRouter.semantic() != null) {
            semanticStrategy = new SemanticRecallStrategy(builder.semanticIndex, tierRouter.semantic(), index);
            rebuildHnswIfNeeded(builder, tierRouter, index, quantizer);
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
        if (!coActivationTracker.banditStats().isEmpty()) {
            profileAdaptor.loadStats(coActivationTracker.banditStats());
        }

        //  RecallHistory (Executive Dysfunction context buffer) 
        RecallHistory recallHistory = new RecallHistory();

        //  Recall Pipeline 
        RecallPipeline recallPipeline = new RecallPipeline(
                embeddingProvider, tierRouter, index,
                suppressionSet, habituationPenalty, prospectiveScheduler, wal,
                quantizer.mins(), quantizer.scales(), semanticStrategy,
                null, hebbianGraph, temporalChain, entityGraph, entityExtractor,
                builder.graphScoringPolicy, bm25Index,
                memorySpladeIndex, builder.SparseEmbeddingProvider, colbertReranker,
                recallHistory);

        recallPipeline.addListener(new LtpReconsolidationListener(index, tierRouter, wal));
        recallPipeline.addListener(new HebbianCoActivationListener(coActivationTracker));

        //  Extracted Components 
        ImportanceEstimator importanceEstimator = new ImportanceEstimator(
                surpriseDetector, flashbulbPolicy, icnuWeights, quantizer);

        ReflectionOrchestrator reflectionOrchestrator = new ReflectionOrchestrator(
                reflectDaemon, hebbianGraph, temporalChain, entityGraph,
                hyperEntityGraph, wal, builder.temporalRetentionDays);

        ReinforcementHandler reinforcementHandler = new ReinforcementHandler(
                valenceTracker, hebbianGraph, lateralEvaluator, recallPipeline,
                wal, builder.twoFactorConfig, profileAdaptor);

        //  Cognitive Graph Facade 
        CognitiveGraphFacade graphFacade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityGraph, hyperEntityGraph, index);

        //  ID Generator 
        MemoryIdGenerator idGenerator = builder.idGenerator != null
                ? builder.idGenerator
                : builder.idStrategy.createGenerator();

        //  Daemon Supervisor + Checkpoint Daemon  (DISK mode only)
        CheckpointDaemon checkpointDaemon;
        DaemonSupervisor daemonSupervisor;
        if (isDisk && basePath != null && builder.checkpointIntervalSeconds > 0) {
            Path indexSavePath = resolvedPartitionDir != null
                    ? resolvedPartitionDir.resolve(StorageLayout.FILE_INDEX)
                    : StorageLayout.indexMidxRuntime(basePath);
            checkpointDaemon = new CheckpointDaemon(
                    tierRouter, wal,
                    StorageLayout.checkpointMeta(basePath),
                    index, indexSavePath,
                    hebbianGraph, temporalChain, entityGraph,
                    hyperEntityGraph, coActivationTracker,
                    resolvedPartitionDir, basePath);
            daemonSupervisor = new DaemonSupervisor("memory");
            daemonSupervisor.schedule(
                    "checkpoint",
                    checkpointDaemon::checkpoint,
                    java.time.Duration.ofSeconds(builder.checkpointIntervalSeconds),
                    DaemonPolicy.CRITICAL);
        } else {
            checkpointDaemon = null;
            daemonSupervisor = null;
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
                cognitiveTarget, embeddingProvider, recallPipeline, index, quantizer,
                partitionManager, importanceEstimator, reflectionOrchestrator,
                reinforcementHandler, valenceTracker, coActivationTracker,
                suppressionSet, habituationPenalty, prospectiveScheduler,
                introspector, lateralEvaluator, wal, hebbianGraph, temporalChain,
                temporalKnowledgeGraph,
                entityGraph, hyperEntityGraph, graphFacade, idGenerator,
                checkpointDaemon, daemonSupervisor, bm25Index, attachmentProcessor,
                parallelPipeline, embedConfig, resolvedPartitionDir, basePath,
                namespaceManager, profileAdaptor
        );
    }

    private static void rebuildHnswIfNeeded(SpectorMemoryBuilder builder, TierRouter tierRouter, MemoryIndex index, ScalarQuantizer quantizer) {
        var semStore = tierRouter.semantic();
        int storeSize = semStore.size();
        if (storeSize > 0 && builder.semanticIndex.size() == 0) {
            log.info("Rebuilding HNSW index from {} persisted semantic records...", storeSize);
            long startMs = System.currentTimeMillis();
            var seg = semStore.primarySegment();
            var recLayout = semStore.layout();
            int stride = recLayout.stride();
            int vecBytes = recLayout.quantizedVecBytes();
            long baseOffset = semStore.filePath() != null
                    ? com.spectrayan.spector.memory.cortex.AbstractTierStore.METADATA_HEADER_BYTES : 0;

            int rebuilt = 0;
            for (int i = 0; i < storeSize; i++) {
                long recordOff = baseOffset + (long) i * stride;
                byte[] quantized = new byte[vecBytes];
                java.lang.foreign.MemorySegment.copy(
                        seg, java.lang.foreign.ValueLayout.JAVA_BYTE,
                        recLayout.vectorOffset(recordOff),
                        java.lang.foreign.MemorySegment.ofArray(quantized),
                        java.lang.foreign.ValueLayout.JAVA_BYTE, 0, vecBytes);

                float[] vector = quantizer.decode(quantized);
                String id = index.findIdByOffset(MemoryType.SEMANTIC, recordOff);
                if (id != null && !builder.semanticIndex.isReadOnly()) {
                    builder.semanticIndex.add(id, i, vector);
                    rebuilt++;
                }
            }
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("HNSW rebuild complete: {}/{} vectors added in {}ms", rebuilt, storeSize, elapsed);
        }
    }

    private static Path getNewerPath(Path runtimePath, Path partitionPath, Path legacyPath) {
        Path target = null;
        long latestTime = Long.MIN_VALUE;

        if (runtimePath != null && java.nio.file.Files.exists(runtimePath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(runtimePath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = runtimePath;
                }
            } catch (java.io.IOException ignored) {}
        }

        if (partitionPath != null && java.nio.file.Files.exists(partitionPath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(partitionPath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = partitionPath;
                }
            } catch (java.io.IOException ignored) {}
        }

        if (legacyPath != null && java.nio.file.Files.exists(legacyPath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(legacyPath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = legacyPath;
                }
            } catch (java.io.IOException ignored) {}
        }

        return target;
    }

    private static void createDirectoriesSecure(Path path) throws java.io.IOException {
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            java.nio.file.attribute.FileAttribute<java.util.Set<java.nio.file.attribute.PosixFilePermission>> attrs =
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            java.nio.file.Files.createDirectories(path, attrs);
        } else {
            java.nio.file.Files.createDirectories(path);
        }
    }

    private static Path createSecureTempDirectory(String prefix) throws java.io.IOException {
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            java.nio.file.attribute.FileAttribute<java.util.Set<java.nio.file.attribute.PosixFilePermission>> attrs =
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            return java.nio.file.Files.createTempDirectory(prefix, attrs);
        } else {
            Path tempDir = java.nio.file.Files.createTempDirectory(prefix);
            java.io.File file = tempDir.toFile();
            boolean readable = file.setReadable(true, true);
            boolean writable = file.setWritable(true, true);
            boolean executable = file.setExecutable(true, true);
            if (!readable || !writable || !executable) {
                log.warn("Could not set strict file permissions on temporary directory: {}", tempDir);
            }
            return tempDir;
        }
    }

    private static void performWalRecovery(
            MemoryWal wal,
            TierRouter tierRouter,
            MemoryIndex index,
            HebbianGraphBase hebbianGraph,
            TemporalChain temporalChain,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            EntityGraph entityGraph,
            CoActivationTracker coActivationTracker,
            CognitiveIngestionTarget cognitiveTarget,
            Path basePath) {
        
        if (wal == null || !wal.isPersistent()) {
            return;
        }
        
        long checkpointHwm = 0;
        if (basePath != null) {
            Path metaPath = StorageLayout.checkpointMeta(basePath);
            if (Files.exists(metaPath)) {
                checkpointHwm = CheckpointDaemon.readCheckpointHwm(metaPath);
                log.info("WAL recovery: loaded checkpoint HWM {}", checkpointHwm);
            }
        }
        
        List<WalEvent> events = wal.replay(checkpointHwm);
        if (events.isEmpty()) {
            log.info("WAL recovery: no events to replay after HWM {}", checkpointHwm);
            return;
        }
        
        log.info("WAL recovery: replaying {} events after HWM {}", events.size(), checkpointHwm);
        
        // 1. Gather all shape-specific Memory instances
        java.util.Map<MemoryId, Memory<?>> memories = new java.util.HashMap<>();
        
        if (tierRouter != null) {
            if (tierRouter.working() != null) {
                Memory<?> backing = tierRouter.working().backing();
                memories.put(backing.id(), backing);
            }
            if (tierRouter.semantic() != null) {
                Memory<?> backing = tierRouter.semantic().backing();
                memories.put(backing.id(), backing);
            }
            if (tierRouter.procedural() != null) {
                Memory<?> backing = tierRouter.procedural().backing();
                memories.put(backing.id(), backing);
            }
            if (tierRouter.episodic() != null) {
                Memory<?> backing = tierRouter.episodic().backing();
                memories.put(backing.id(), backing);
            }
        }
        
        if (entityGraph != null) {
            memories.put(entityGraph.id(), entityGraph);
        }
        if (hebbianGraph instanceof HebbianGraphCsr hg) {
            memories.put(hg.id(), hg);
        }
        if (temporalChain != null) {
            memories.put(MemoryId.of("temporal", "chain"), temporalChain);
        }
        if (temporalKnowledgeGraph != null) {
            memories.put(temporalKnowledgeGraph.id(), temporalKnowledgeGraph.backing());
        }

        // Add textDataStore memory if available
        MemoryId textId = MemoryId.of("cortex", "text");
        if (index.textDataStore() != null) {
            memories.put(textId, index.textDataStore());
        }

        long countBeforeRecovery = 0;
        Memory<?> textMem = memories.get(textId);
        if (textMem instanceof com.spectrayan.spector.memory.kernel.shape.AppendMemory<?> am) {
            countBeforeRecovery = am.appendCursor();
        }
        
        // 2. Dispatch shape mutations directly to target memory segments
        WalRecoveryDispatcher.recover(wal, memories);
        
        // 3. Rebuild MemoryIndex on-heap maps from high-level REMEMBER/FORGET/REINFORCE events
        long lastRecordOffset = -1;
        MemoryType lastRecordType = null;
        long lastTextOffset = -1;
        int lastTextLength = -1;
        long currentTextCursor = countBeforeRecovery;
        
        for (WalEvent event : events) {
            if (event.sequence() <= checkpointHwm) {
                continue;
            }
            
            try {
                switch (event.type()) {
                    case RECORD_WRITE -> {
                        ByteBuffer buf = java.nio.ByteBuffer.wrap(event.payload());
                        long recordId = buf.getLong();
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        Memory<?> target = memories.get(targetId);
                        if (target instanceof com.spectrayan.spector.memory.kernel.shape.RecordMemory<?> rm) {
                            lastRecordOffset = rm.recordOffset(recordId);
                            String pathName = targetId.memoryName();
                            if ("working".equals(pathName)) lastRecordType = MemoryType.WORKING;
                            else if ("semantic".equals(pathName)) lastRecordType = MemoryType.SEMANTIC;
                            else if ("procedural".equals(pathName)) lastRecordType = MemoryType.PROCEDURAL;
                            else if ("episodic".equals(pathName)) lastRecordType = MemoryType.EPISODIC;
                        }
                    }
                    case APPEND -> {
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        if ("cortex".equals(targetId.namespace()) && "text".equals(targetId.memoryName())) {
                            lastTextOffset = currentTextCursor;
                            lastTextLength = event.payload().length;
                            currentTextCursor += 4 + lastTextLength;
                        }
                    }
                    case REMEMBER -> {
                        if (lastRecordOffset != -1 && lastRecordType != null) {
                            // CognitiveRecordLayout has dynamic stride
                            int stride = 164; // default
                            MemoryId targetId = MemoryId.of("tier", lastRecordType.name().toLowerCase());
                            Memory<?> target = memories.get(targetId);
                            if (target != null) {
                                stride = target.layout().recordStride();
                            }
                            int storeIndex = (int) (lastRecordOffset / stride);
                            com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation loc =
                                    new com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation(
                                            lastRecordType, lastRecordOffset, -1, lastTextOffset, lastTextLength);
                            
                            String text = "";
                            if (index.textDataStore() != null && lastTextOffset != -1) {
                                try {
                                    text = index.textDataStore().readTextDirect(lastTextOffset, lastTextLength);
                                } catch (Exception ignored) {}
                            }
                            
                            index.register(event.memoryId(), loc, text, MemorySource.OBSERVED, new String[0]);
                        }
                    }
                    case FORGET -> {
                        index.remove(event.memoryId());
                    }
                    default -> {}
                }
            } catch (Exception e) {
                log.warn("WAL recovery index sync failed for event seq={}, type={}: {}", 
                        event.sequence(), event.type(), e.getMessage());
            }
        }
    }
}
