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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.importance.CompositeImportanceScorer;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic pathway relay evaluating multi-dimensional composite importance \(I(o_t)\) and triggering flashbulb memory tags.
 *
 * <h3>Biological Analog: Multi-Limbic Salience Synthesis & Amygdalar Flashbulb Tagging</h3>
 * <p>Synthesizes predictive coding surprise, affective resonance, prospective goal relevance,
 * social context significance, and manifold novelty using SIMD vector acceleration.
 * If composite importance exceeds the threshold, flags the signal with flashbulb persistence priority.</p>
 */
public final class CompositeImportanceRelay implements SynapticRelay<RememberSignal> {

    private static final Logger log = LoggerFactory.getLogger(CompositeImportanceRelay.class);

    private final AismeProperties config;
    private final CompositeImportanceScorer scorer;
    private final CognitiveProfile profile;

    public CompositeImportanceRelay(AismeProperties config, CompositeImportanceScorer scorer) {
        this(config, scorer, CognitiveProfile.BALANCED);
    }

    public CompositeImportanceRelay(AismeProperties config, CompositeImportanceScorer scorer, CognitiveProfile profile) {
        this.config = config;
        this.scorer = scorer;
        this.profile = profile != null ? profile : CognitiveProfile.BALANCED;
    }

    @Override
    public String relayName() {
        return RelayNames.COMPOSITE_IMPORTANCE;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal == null || config == null || scorer == null) {
            return true;
        }

        if (config.enableImportance()) {
            float importance = scorer.evaluateSignal(signal, profile);
            signal.importance(importance);

            if (scorer.isFlashbulb(importance)) {
                signal.flashbulb(true);
                log.debug("CompositeImportanceRelay: signal id={} tagged as FLASHBULB with composite score={:.3f}",
                        signal.id(), importance);
            } else {
                log.debug("CompositeImportanceRelay: signal id={} computed composite importance={:.3f}",
                        signal.id(), importance);
            }
        }

        return true;
    }
}
