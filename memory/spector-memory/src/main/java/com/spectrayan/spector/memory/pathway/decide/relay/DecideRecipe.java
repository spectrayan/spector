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
package com.spectrayan.spector.memory.pathway.decide.relay;

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.relay.PolicyInferenceRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * Declarative recipe for composing the Decide (expected free energy) cognitive pathway.
 *
 * <p>Both stages are {@link ErrorPolicy#DEGRADE_GRACEFULLY} with no resilience decorator, per
 * the ADR-0036 §14 "Decide / Wander / Express" row. Note that "Decide with no candidates" is
 * expressed as a gate ({@code HAS_CANDIDATES}) rather than a fault — having nothing to choose
 * between is a normal state, not a failure.</p>
 */
public class DecideRecipe implements PathwayRecipe<DecideSignal> {

    @Override
    public void compose(final PathwayComposer<DecideSignal> composer) {
        // Stage 1: policy inference over candidate actions
        composer.gated(RelayNames.POLICY_INFERENCE,
                DecideGates.EFE_ENABLED.and(DecideGates.HAS_CANDIDATES),
                policyInference(),
                ErrorPolicy.DEGRADE_GRACEFULLY);

        // Stage 2: waking thought experiments
        composer.gated(RelayNames.EXPERIMENT_THOUGHT,
                DecideGates.EFE_ENABLED,
                experiment(),
                ErrorPolicy.DEGRADE_GRACEFULLY);
    }

    /** Returns the expected-free-energy policy inference relay. */
    protected SynapticRelay<DecideSignal> policyInference() {
        return new PolicyInferenceRelay();
    }

    /** Returns the waking thought-experiment relay. */
    protected SynapticRelay<DecideSignal> experiment() {
        return new ExperimentRelay();
    }
}
