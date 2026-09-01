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

import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.api.ImportanceEstimator;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.neuromod.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.graph.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.pathway.reflect.daemon.ReflectDaemon;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.pathway.reflect.ReinforcementHandler;

import com.spectrayan.spector.memory.pathway.reflect.ReinforcementHandler;
import com.spectrayan.spector.memory.persist.PartitionManager;

import com.spectrayan.spector.memory.neuromod.amygdala.ValenceTracker;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.memory.neuromod.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.neuromod.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.graph.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.pathway.reflect.daemon.ReflectDaemon;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.cortex.metamemory.MemoryIntrospector;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neuromod.neurodivergent.LateralEvaluator;
import com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

/**
 * Assembles the biologically-inspired subsystem trackers: surprise/flashbulb
 * dopamine gating, valence tracking, co-activation, suppression, habituation,
 * prospective scheduling, metamemory introspection, lateral (neurodivergent)
 * evaluation, and the reflect (sleep-consolidation) daemon.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. Note that the resolved {@link IcnuWeights}
 * returned here is the one consumed by the {@code ImportanceEstimator}; the
 * ingestion target continues to receive {@code builder.icnuWeights()} directly, as
 * before.</p>
 *
 * @since 1.1.0
 */
public final class BiologicalSubsystemsBuilder {

    private BiologicalSubsystemsBuilder() {}

    /** Immutable holder for the assembled biological subsystems. */
    public record BiologicalSubsystems(
            SurpriseDetector surpriseDetector,
            IcnuWeights icnuWeights,
            FlashbulbPolicy flashbulbPolicy,
            ValenceTracker valenceTracker,
            CoActivationRecordMemory coActivationTracker,
            SuppressionSet suppressionSet,
            HabituationPenalty habituationPenalty,
            ProspectiveScheduler prospectiveScheduler,
            MemoryIntrospector introspector,
            LateralEvaluator lateralEvaluator,
            ReflectDaemon reflectDaemon
    ) {}

    public static BiologicalSubsystems build(SpectorMemoryBuilder builder,
                                      EmbeddingProvider embeddingProvider,
                                      CognitiveCortexBuilder.CortexFoundation cortex) {
        boolean isDisk = cortex.isDisk();
        var basePath = cortex.basePath();

        //  Biological Subsystems 
        SurpriseDetector surpriseDetector = new SurpriseDetector(builder.surpriseWarmup());
        IcnuWeights icnuWeights = builder.icnuWeights() != null ? builder.icnuWeights() : IcnuWeights.DEFAULT;
        FlashbulbPolicy flashbulbPolicy = new FlashbulbPolicy(builder.flashbulbThreshold());
        ValenceTracker valenceTracker = new ValenceTracker(builder.valenceLearningRate());

        CoActivationRecordMemory coActivationTracker;
        if (cortex.useBundleMode() && cortex.runtimeBundle() != null) {
            java.lang.foreign.MemorySegment regionSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.COACTIVATION);
            boolean isNew = !com.spectrayan.spector.memory.kernel.MemoryHeader.isValid(regionSlice, 0L);
            java.lang.foreign.MemorySegment ckptSlice = cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.CHECKPOINT);
            coActivationTracker = CoActivationRecordMemory.fromBundle(
                    cortex.runtimeBundle().arena(), regionSlice, 10_000, 20_000,
                    cortex.runtimeBundle().bundlePath(), isNew, ckptSlice);
        } else {
            coActivationTracker = new CoActivationRecordMemory();
        }
        SuppressionSet suppressionSet = new SuppressionSet();
        HabituationPenalty habituationPenalty = new HabituationPenalty(0.2f, builder.inhibitionTtlMs(), builder.inhibitionFloor());
        ProspectiveScheduler prospectiveScheduler = new ProspectiveScheduler();
        MemoryIntrospector introspector = new MemoryIntrospector(coActivationTracker);
        LateralEvaluator lateralEvaluator = new LateralEvaluator();

        ReflectDaemon reflectDaemon = new ReflectDaemon(
                builder.circadianPolicy(),
                builder.dimensions() > 0 ? new CentroidRouter(builder.dimensions()) : null,
                builder.LlmProvider(),
                embeddingProvider,
                5, // minClusterSize
                builder.pinSourceEpisodes(),
                builder.pinnedQuota());

        return new BiologicalSubsystems(
                surpriseDetector, icnuWeights, flashbulbPolicy, valenceTracker,
                coActivationTracker, suppressionSet, habituationPenalty,
                prospectiveScheduler, introspector, lateralEvaluator, reflectDaemon);
    }
}
