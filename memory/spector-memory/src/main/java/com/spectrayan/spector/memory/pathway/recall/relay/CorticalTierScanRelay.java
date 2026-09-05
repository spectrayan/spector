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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.pruning.PartitionPruner;
import com.spectrayan.spector.memory.pathway.pipeline.scan.EpisodicScoreFunction;
import com.spectrayan.spector.memory.pathway.pipeline.scan.ParallelScanEmitter;
import com.spectrayan.spector.memory.pathway.pipeline.scan.ScanContext;
import com.spectrayan.spector.memory.pathway.pipeline.scan.ScanEmitter;
import com.spectrayan.spector.memory.pathway.pipeline.scan.SequentialScanEmitter;
import com.spectrayan.spector.memory.pathway.pipeline.scan.SlabScoreFunction;
import com.spectrayan.spector.memory.pathway.pipeline.scan.TierScanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Evaluates candidates via a vector similarity tier scan.
 */
public final class CorticalTierScanRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(CorticalTierScanRelay.class);

    private static final TierScanStrategy WORKING_SCAN = new TierScanStrategy.WorkingTierScanStrategy();
    private static final List<TierScanStrategy> PER_PARTITION_SCANS = List.of(
            new TierScanStrategy.EpisodicTierScanStrategy(),
            new TierScanStrategy.SemanticTierScanStrategy(),
            new TierScanStrategy.ProceduralTierScanStrategy()
    );

    private final PartitionRegistry partitionRegistry;
    private final PartitionPruner partitionPruner;
    private final SemanticRecallStrategy semanticRecallStrategy;
    private final SlabScoreFunction scoreFunc;
    private final EpisodicScoreFunction episodicScoreFunc;

    /**
     * Constructs a new CorticalTierScanRelay.
     *
     * @param partitionRegistry      the partition registry
     * @param partitionPruner        the partition pruner
     * @param semanticRecallStrategy the semantic recall strategy (nullable)
     * @param scoreFunc              the scoring function
     * @param episodicScoreFunc      the episodic scoring function
     */
    public CorticalTierScanRelay(
            final PartitionRegistry partitionRegistry,
            final PartitionPruner partitionPruner,
            final SemanticRecallStrategy semanticRecallStrategy,
            final SlabScoreFunction scoreFunc,
            final EpisodicScoreFunction episodicScoreFunc) {
        this.partitionRegistry = partitionRegistry;
        this.partitionPruner = partitionPruner;
        this.semanticRecallStrategy = semanticRecallStrategy;
        this.scoreFunc = scoreFunc;
        this.episodicScoreFunc = episodicScoreFunc;
    }

    public CorticalTierScanRelay(
            final PartitionRegistry partitionRegistry,
            final PartitionPruner partitionPruner,
            final SemanticRecallStrategy semanticRecallStrategy,
            final SlabScoreFunction scoreFunc) {
        this(partitionRegistry, partitionPruner, semanticRecallStrategy, scoreFunc, null);
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (!signal.options().textSearchMode().usesVector()) {
            return true;
        }

        final float[] queryVector = signal.queryVector();
        final String rawQuery = signal.rawQuery();
        final RecallOptions options = signal.options();
        final long nowMs = signal.queryTimeMs() > 0 ? signal.queryTimeMs() : signal.timestampMs();
        final MemoryType[] targetTypes = options.memoryTypes();
        final List<CognitiveResult> allResults = signal.candidates();

        final List<Callable<List<CognitiveResult>>> scanTasks = new ArrayList<>();
        scan(new ParallelScanEmitter(scanTasks, queryVector, rawQuery, options, nowMs, scoreFunc, episodicScoreFunc, semanticRecallStrategy),
                targetTypes, options, nowMs);

        if (!scanTasks.isEmpty()) {
            try {
                final List<List<CognitiveResult>> tierResults = ConcurrentTasks.forkJoinAll(scanTasks);
                for (final List<CognitiveResult> tier : tierResults) {
                    allResults.addAll(tier);
                }
            } catch (final ConcurrentExecutionException e) {
                log.error("Parallel tier scan failed: {}", e.getMessage(), e);
                allResults.addAll(sequentialScan(queryVector, rawQuery, options, nowMs, targetTypes));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Recall interrupted during parallel scan");
                return false;
            }
        }
        return true;
    }

    private void scan(final ScanEmitter emitter, final MemoryType[] targetTypes, final RecallOptions options, final long nowMs) {
        final List<PartitionHandle> snapshot = partitionRegistry.snapshot();
        final CognitiveMemoryRouter active = partitionRegistry.activeRouter();
        final boolean singlePartition = snapshot.size() == 1;
        final int activeSeq = snapshot.get(snapshot.size() - 1).seq();
        final boolean semanticHnswAvailable = semanticRecallStrategy != null && semanticRecallStrategy.isAvailable();
        final ScanContext ctx = new ScanContext(targetTypes, active, singlePartition, activeSeq, semanticHnswAvailable);

        final PartitionHandle activeHandle = snapshot.get(snapshot.size() - 1);

        WORKING_SCAN.contribute(ctx, activeHandle, emitter);

        if (ctx.semanticHnswAvailable() && CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, ctx.targetTypes())) {
            emitter.emitSemanticHnsw();
        }

        final List<PartitionHandle> candidatePartitions = partitionPruner.prune(snapshot, options, targetTypes, nowMs);

        for (final PartitionHandle handle : candidatePartitions) {
            for (final TierScanStrategy strategy : PER_PARTITION_SCANS) {
                strategy.contribute(ctx, handle, emitter);
            }
        }
    }

    private List<CognitiveResult> sequentialScan(final float[] queryVector, final String rawQuery,
                                                 final RecallOptions options, final long nowMs, final MemoryType[] targetTypes) {
        final List<CognitiveResult> results = new ArrayList<>();
        scan(new SequentialScanEmitter(results, queryVector, rawQuery, options, nowMs, scoreFunc, episodicScoreFunc, semanticRecallStrategy),
                targetTypes, options, nowMs);
        return results;
    }

    @Override
    public String relayName() {
        return RelayNames.VECTOR_SEARCH;
    }
}
