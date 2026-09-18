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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.simulation.relay.SpacetimeSeedRelay;

/**
 * Declarative recipe for composing the Wander (default-mode network) cognitive pathway.
 *
 * <p>Every stage is {@link ErrorPolicy#DEGRADE_GRACEFULLY} and carries no resilience
 * decorator, per the ADR-0036 §14 "Decide / Wander / Express" row: nothing here reaches a
 * remote provider or a nested pathway, so there is nothing to time out, retry or isolate.
 * The recipe exists for the other half of the story — a single authoring path through
 * {@link PathwayComposer}, and a shape the parity gate can assert.</p>
 *
 * <p>The idle gate stays a gate rather than {@code ABORT}: later continuity tracking may
 * still be worth running when the system is not idle, so short-circuiting the whole pathway
 * would be a behaviour change (ADR-0036 §14).</p>
 */
public class WanderRecipe implements PathwayRecipe<WanderSignal> {

    @Override
    public void compose(final PathwayComposer<WanderSignal> composer) {
        // 1. Idle Gate
        composer.gated(RelayNames.IDLE_GATE, WanderGates.IS_IDLE, idleGate(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2. Autobiographical Sampling
        composer.gated(RelayNames.AUTOBIOGRAPHICAL_SAMPLING, WanderGates.DMN_ENABLED, autobiographicalSampling(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 2b. Spacetime Shortlist Seed Selection (ADR-0031)
        composer.gated(RelayNames.SPACETIME_SEED, WanderGates.DMN_ENABLED, spacetimeSeed(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 3. Hopfield Mind Wandering
        composer.gated(RelayNames.HOPFIELD_MIND_WANDERING, WanderGates.DMN_ENABLED, hopfieldMindWandering(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 4. Manifold Synergy Evaluation
        composer.gated(RelayNames.MANIFOLD_SYNERGY, WanderGates.MANIFOLD_ENABLED, manifoldSynergy(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 5. Hebbian Synaptic Reinforcement
        composer.gated(RelayNames.HEBBIAN_REINFORCEMENT, WanderGates.DMN_ENABLED, hebbianReinforcement(), ErrorPolicy.DEGRADE_GRACEFULLY);

        // 6. Longitudinal Continuity Snapshot
        composer.gated(RelayNames.LONGITUDINAL_CONTINUITY, WanderGates.CONTINUITY_ENABLED, longitudinalContinuity(), ErrorPolicy.DEGRADE_GRACEFULLY);
    }

    /** Returns the idle-detection gate relay. */
    protected SynapticRelay<WanderSignal> idleGate() {
        return new IdleGateRelay();
    }

    /** Returns the autobiographical memory sampling relay. */
    protected SynapticRelay<WanderSignal> autobiographicalSampling() {
        return new AutobiographicalSamplingRelay();
    }

    /** Returns the spacetime shortlist seed selection relay (ADR-0031). */
    protected SynapticRelay<WanderSignal> spacetimeSeed() {
        return new SpacetimeSeedRelay.WanderSeedRelay();
    }

    /** Returns the Hopfield mind-wandering relay. */
    protected SynapticRelay<WanderSignal> hopfieldMindWandering() {
        return new HopfieldMindWanderingRelay();
    }

    /** Returns the manifold synergy evaluation relay. */
    protected SynapticRelay<WanderSignal> manifoldSynergy() {
        return new ManifoldSynergyRelay();
    }

    /** Returns the Hebbian synaptic reinforcement relay. */
    protected SynapticRelay<WanderSignal> hebbianReinforcement() {
        return new HebbianSynapticReinforcementRelay();
    }

    /** Returns the longitudinal continuity snapshot relay. */
    protected SynapticRelay<WanderSignal> longitudinalContinuity() {
        return new LongitudinalContinuityRelay();
    }
}
