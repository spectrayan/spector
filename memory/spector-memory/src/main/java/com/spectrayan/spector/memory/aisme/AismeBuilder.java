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
package com.spectrayan.spector.memory.aisme;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.FreeEnergyCalculator;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.AffectiveResonanceScorer;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.narrative.NarrativeSelfEngine;
import com.spectrayan.spector.memory.aisme.pcmn.PredictiveCodingNetwork;
import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityEvaluator;
import com.spectrayan.spector.memory.aisme.policy.ExpectedFreeEnergyCalculator;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.aisme.relay.ConsciousAccessRelay;
import com.spectrayan.spector.memory.aisme.relay.ConsciousnessContinuityRelay;
import com.spectrayan.spector.memory.aisme.relay.ConstructiveMemoryPersistenceRelay;
import com.spectrayan.spector.memory.aisme.relay.ConstructiveSimulationRelay;
import com.spectrayan.spector.memory.aisme.relay.EpistemicLearningRelay;
import com.spectrayan.spector.memory.aisme.relay.FreeEnergyGuidedRelay;
import com.spectrayan.spector.memory.aisme.relay.HomeostaticBiasRelay;
import com.spectrayan.spector.memory.aisme.relay.HopfieldAssociativeRelay;
import com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay;
import com.spectrayan.spector.memory.aisme.relay.ManifoldRerankRelay;
import com.spectrayan.spector.memory.aisme.workspace.GlobalWorkspace;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;

import java.util.List;
import java.util.function.Function;

/**
 * Factory for instantiating and wiring the Active Inference Self-Model Engine (AISME) subsystems and synaptic relays.
 */
public final class AismeBuilder {

    private AismeBuilder() {}

    /**
     * Builds an {@link AismeBundle} initialized with the provided configuration and vector lookup.
     *
     * @param config the AISME configuration (or disabled if null)
     * @param soul optional polymorphic SoulContext identity definition
     * @param dimensions vector embedding dimensionality
     * @param vectorLookup function mapping memory IDs to vector representations
     * @return the constructed AismeBundle, or null if disabled
     */
    public static AismeBundle build(
            final AismeConfig config,
            final SoulContext soul,
            final int dimensions,
            final Function<String, float[]> vectorLookup
    ) {
        return build(config, soul, dimensions, null, vectorLookup, soul != null ? List.of(soul) : List.of());
    }

    /**
     * Builds an {@link AismeBundle} initialized with the provided configuration, multi-soul context, and vector lookup.
     *
     * @param config the AISME configuration (or disabled if null)
     * @param soul optional polymorphic SoulContext identity definition
     * @param dimensions vector embedding dimensionality
     * @param vectorLookup function mapping memory IDs to vector representations
     * @param soulContexts list of all active soul contexts for multi-soul EFE evaluation
     * @return the constructed AismeBundle, or null if disabled
     */
    public static AismeBundle build(
            final AismeConfig config,
            final SoulContext soul,
            final int dimensions,
            final Function<String, float[]> vectorLookup,
            final List<SoulContext> soulContexts
    ) {
        return build(config, soul, dimensions, null, vectorLookup, soulContexts);
    }

    /**
     * Builds an {@link AismeBundle} with full cognitive target wiring, multi-soul context, and vector lookup.
     *
     * @param config the AISME configuration (or disabled if null)
     * @param soul optional polymorphic SoulContext identity definition
     * @param dimensions vector embedding dimensionality
     * @param ingestionTarget target for persisting high-alignment constructive simulations
     * @param vectorLookup function mapping memory IDs to vector representations
     * @param soulContexts list of all active soul contexts for multi-soul EFE evaluation
     * @return the constructed AismeBundle, or null if disabled
     */
    public static AismeBundle build(
            final AismeConfig config,
            final SoulContext soul,
            final int dimensions,
            final CognitiveIngestionTarget ingestionTarget,
            final Function<String, float[]> vectorLookup,
            final List<SoulContext> soulContexts
    ) {
        final AismeConfig cfg = config != null ? config : AismeConfig.disabled();
        if (!cfg.enabled() || dimensions <= 0) {
            return null;
        }

        final List<SoulContext> activeSouls;
        if (soulContexts != null && !soulContexts.isEmpty()) {
            activeSouls = soulContexts;
        } else if (soul != null) {
            activeSouls = List.of(soul);
        } else {
            activeSouls = List.of();
        }

        final HomeostaticCore homeostaticCore = new HomeostaticCore();
        final AffectiveResonanceScorer affectiveScorer = new AffectiveResonanceScorer();
        final GenerativeSelfModel generativeSelfModel = activeSouls.size() > 1
                ? GenerativeSelfModel.fromSoulsAndProfile(activeSouls, CognitiveProfile.BALANCED, dimensions)
                : GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, dimensions);
        final MentalStateTracker mentalStateTracker = new MentalStateTracker(generativeSelfModel);
        final FreeEnergyCalculator freeEnergyCalculator = new FreeEnergyCalculator();
        final ContinuousHopfieldNetwork hopfieldNetwork = new ContinuousHopfieldNetwork();
        final CognitiveManifold cognitiveManifold = new CognitiveManifold(dimensions);
        final PredictiveCodingNetwork predictiveCodingNetwork = new PredictiveCodingNetwork(dimensions, 4);
        final NarrativeSelfEngine narrativeSelfEngine = new NarrativeSelfEngine(soul, dimensions);
        final GlobalWorkspace globalWorkspace = new GlobalWorkspace(cfg.globalWorkspaceCapacity());
        final ConsciousnessContinuityEvaluator continuityEvaluator = new ConsciousnessContinuityEvaluator(
                soul, 0.01f, cfg.manifoldSigma());

        final HomeostaticBiasRelay homeostaticBiasRelay = new HomeostaticBiasRelay(homeostaticCore, affectiveScorer);
        final FreeEnergyGuidedRelay freeEnergyGuidedRelay = new FreeEnergyGuidedRelay(
                mentalStateTracker, freeEnergyCalculator, homeostaticCore, affectiveScorer, vectorLookup);
        final HopfieldAssociativeRelay hopfieldAssociativeRelay = new HopfieldAssociativeRelay(
                hopfieldNetwork, vectorLookup, CognitiveProfile.BALANCED, homeostaticCore);
        final ManifoldRerankRelay manifoldRerankRelay = new ManifoldRerankRelay(cognitiveManifold, vectorLookup);
        final ConstructiveSimulationRelay constructiveSimulationRelay = new ConstructiveSimulationRelay(
                narrativeSelfEngine, predictiveCodingNetwork, vectorLookup);
        final ConsciousnessContinuityRelay consciousnessContinuityRelay = new ConsciousnessContinuityRelay(
                continuityEvaluator, vectorLookup);
        final ConsciousAccessRelay consciousAccessRelay = new ConsciousAccessRelay(globalWorkspace);
        final ManifoldConsolidationRelay manifoldConsolidationRelay = new ManifoldConsolidationRelay(cognitiveManifold, null);
        final EpistemicLearningRelay epistemicLearningRelay = new EpistemicLearningRelay(
                mentalStateTracker, homeostaticCore, vectorLookup);

        // Expected Free Energy (G) Policy Engine
        final ExpectedFreeEnergyCalculator efeCalculator = cfg.enableExpectedFreeEnergy()
                ? new ExpectedFreeEnergyCalculator(
                        activeSouls,
                        cfg.efeSoulWeightAgent(), cfg.efeSoulWeightUser(),
                        cfg.efeSoulWeightTenant(), cfg.efeSoulWeightOrgUnit(),
                        cfg.efeEpistemicWeight(), cfg.efePragmaticWeight())
                : null;
        final PolicyInferenceEngine policyInferenceEngine = efeCalculator != null
                ? new PolicyInferenceEngine(efeCalculator, homeostaticCore, mentalStateTracker, cfg.efePolicyPrecision())
                : null;

        // Constructive Memory Persistence (#613 / AISME Phase 12)
        final ConstructiveMemoryPersistenceRelay constructiveMemoryPersistenceRelay =
                cfg.constructivePersistenceEnabled()
                        ? new ConstructiveMemoryPersistenceRelay(ingestionTarget, vectorLookup, cfg.constructivePersistenceThreshold())
                        : null;

        return new AismeBundle(
                cfg,
                soul,
                activeSouls,
                homeostaticCore,
                affectiveScorer,
                generativeSelfModel,
                mentalStateTracker,
                hopfieldNetwork,
                cognitiveManifold,
                predictiveCodingNetwork,
                narrativeSelfEngine,
                globalWorkspace,
                continuityEvaluator,
                homeostaticBiasRelay,
                freeEnergyGuidedRelay,
                hopfieldAssociativeRelay,
                manifoldRerankRelay,
                constructiveSimulationRelay,
                constructiveMemoryPersistenceRelay,
                consciousnessContinuityRelay,
                consciousAccessRelay,
                manifoldConsolidationRelay,
                epistemicLearningRelay,
                efeCalculator,
                policyInferenceEngine
        );
    }
}
