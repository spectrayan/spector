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
package com.spectrayan.spector.memory.aisme;

import com.spectrayan.spector.config.properties.AismeProperties;
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
import com.spectrayan.spector.memory.model.SoulContext;

import java.util.Collections;
import java.util.List;

/**
 * Immutable bundle holding all initialized Active Inference Self-Model Engine (AISME) subsystems and relays.
 */
public record AismeBundle(
        AismeProperties config,
        SoulContext primarySoul,
        List<SoulContext> soulContexts,
        HomeostaticCore homeostaticCore,
        AffectiveResonanceScorer affectiveScorer,
        GenerativeSelfModel generativeSelfModel,
        MentalStateTracker mentalStateTracker,
        ContinuousHopfieldNetwork hopfieldNetwork,
        CognitiveManifold cognitiveManifold,
        PredictiveCodingNetwork predictiveCodingNetwork,
        NarrativeSelfEngine narrativeSelfEngine,
        GlobalWorkspace globalWorkspace,
        ConsciousnessContinuityEvaluator continuityEvaluator,
        HomeostaticBiasRelay homeostaticBiasRelay,
        FreeEnergyGuidedRelay freeEnergyGuidedRelay,
        HopfieldAssociativeRelay hopfieldAssociativeRelay,
        ManifoldRerankRelay manifoldRerankRelay,
        ConstructiveSimulationRelay constructiveSimulationRelay,
        ConstructiveMemoryPersistenceRelay constructiveMemoryPersistenceRelay,
        ConsciousnessContinuityRelay consciousnessContinuityRelay,
        ConsciousAccessRelay consciousAccessRelay,
        ManifoldConsolidationRelay manifoldConsolidationRelay,
        EpistemicLearningRelay epistemicLearningRelay,
        ExpectedFreeEnergyCalculator expectedFreeEnergyCalculator,
        PolicyInferenceEngine policyInferenceEngine
) {

    public AismeBundle {
        soulContexts = soulContexts != null ? List.copyOf(soulContexts) : Collections.emptyList();
    }

    /**
     * Backward-compatible 24-arg constructor for code passing single AgentSoul.
     */
    public AismeBundle(
            AismeProperties config,
            AgentSoul agentSoul,
            HomeostaticCore homeostaticCore,
            AffectiveResonanceScorer affectiveScorer,
            GenerativeSelfModel generativeSelfModel,
            MentalStateTracker mentalStateTracker,
            ContinuousHopfieldNetwork hopfieldNetwork,
            CognitiveManifold cognitiveManifold,
            PredictiveCodingNetwork predictiveCodingNetwork,
            NarrativeSelfEngine narrativeSelfEngine,
            GlobalWorkspace globalWorkspace,
            ConsciousnessContinuityEvaluator continuityEvaluator,
            HomeostaticBiasRelay homeostaticBiasRelay,
            FreeEnergyGuidedRelay freeEnergyGuidedRelay,
            HopfieldAssociativeRelay hopfieldAssociativeRelay,
            ManifoldRerankRelay manifoldRerankRelay,
            ConstructiveSimulationRelay constructiveSimulationRelay,
            ConstructiveMemoryPersistenceRelay constructiveMemoryPersistenceRelay,
            ConsciousnessContinuityRelay consciousnessContinuityRelay,
            ConsciousAccessRelay consciousAccessRelay,
            ManifoldConsolidationRelay manifoldConsolidationRelay,
            EpistemicLearningRelay epistemicLearningRelay,
            ExpectedFreeEnergyCalculator expectedFreeEnergyCalculator,
            PolicyInferenceEngine policyInferenceEngine
    ) {
        this(config, agentSoul, agentSoul != null ? List.of(agentSoul) : List.of(),
                homeostaticCore, affectiveScorer, generativeSelfModel, mentalStateTracker,
                hopfieldNetwork, cognitiveManifold, predictiveCodingNetwork, narrativeSelfEngine,
                globalWorkspace, continuityEvaluator, homeostaticBiasRelay, freeEnergyGuidedRelay,
                hopfieldAssociativeRelay, manifoldRerankRelay, constructiveSimulationRelay,
                constructiveMemoryPersistenceRelay, consciousnessContinuityRelay,
                consciousAccessRelay, manifoldConsolidationRelay, epistemicLearningRelay,
                expectedFreeEnergyCalculator, policyInferenceEngine);
    }

    /**
     * Backward-compatible accessor returning the primary soul as an {@link AgentSoul} if applicable.
     *
     * @return AgentSoul or null if the primary soul is not an AgentSoul
     */
    public AgentSoul agentSoul() {
        return primarySoul instanceof AgentSoul agent ? agent : null;
    }
}
