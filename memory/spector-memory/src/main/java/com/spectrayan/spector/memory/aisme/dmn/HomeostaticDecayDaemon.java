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
package com.spectrayan.spector.memory.aisme.dmn;

import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Background daemon maintaining continuous posterior belief regression and homeostatic
 * equilibrium during cognitive quiescence between user interactions.
 *
 * <h3>Biological Analog: Tonic Neuromodulatory Homeostasis</h3>
 * <p>Between conscious engagement, tonic neurotransmitter levels gradually return
 * arousal, valence, and dominance to resting baselines. Similarly, unreinforced beliefs
 * decay toward generative priors — preventing stale posterior accumulation.</p>
 */
public final class HomeostaticDecayDaemon implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(HomeostaticDecayDaemon.class);
    
    private final MentalStateTracker mentalStateTracker;
    private final HomeostaticCore homeostaticCore;
    private final float decayFactor;
    
    public HomeostaticDecayDaemon(
            MentalStateTracker mentalStateTracker,
            HomeostaticCore homeostaticCore,
            float decayFactor) {
        this.mentalStateTracker = Objects.requireNonNull(mentalStateTracker);
        this.homeostaticCore = Objects.requireNonNull(homeostaticCore);
        this.decayFactor = decayFactor;
    }
    
    @Override
    public void run() {
        try {
            // 1. Decay posterior beliefs toward generative prior baseline
            mentalStateTracker.decay(System.currentTimeMillis(), decayFactor);
            
            // 2. Step homeostatic state toward neutral equilibrium
            //    Use zero external input and small dt to gradually relax
            float[] neutralInput = new float[]{0.0f, 0.0f, 0.0f};
            homeostaticCore.step(neutralInput, 1.0f);
            
            var state = homeostaticCore.currentState();
            log.debug("HomeostaticDecay: posterior decayed (factor={}), homeostasis stepped to arousal={:.3f} valence={:.3f} dominance={:.3f}",
                    decayFactor, state.arousal(), state.valence(), state.dominance());
        } catch (Exception e) {
            log.warn("HomeostaticDecay cycle encountered non-fatal error: {}", e.getMessage());
        }
    }
}
