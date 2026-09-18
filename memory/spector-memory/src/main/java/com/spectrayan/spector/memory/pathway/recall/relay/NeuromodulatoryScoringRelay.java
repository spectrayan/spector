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
package com.spectrayan.spector.memory.pathway.recall.relay;
import com.spectrayan.spector.kernel.store.CoActivationMemory;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pathway.pipeline.scorer.SalienceAndHabituationScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies cognitive neuromodulatory scoring such as habituation, and STDP boost.
 */
public final class NeuromodulatoryScoringRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(NeuromodulatoryScoringRelay.class);
    
    private final SalienceAndHabituationScorer salienceScorer;
    private final CoActivationMemory coActivationTracker;
    private final GraphScoringPolicy graphScoringPolicy;

    public NeuromodulatoryScoringRelay(
            final SalienceAndHabituationScorer salienceScorer,
            final CoActivationMemory coActivationTracker,
            final GraphScoringPolicy graphScoringPolicy) {
        this.salienceScorer = salienceScorer;
        this.coActivationTracker = coActivationTracker;
        this.graphScoringPolicy = graphScoringPolicy;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        final CoActivationMemory tracker = (signal != null && signal.context() != null)
                ? signal.context().find(CoActivationMemory.class).orElse(coActivationTracker)
                : coActivationTracker;
        salienceScorer.applyCognitiveScoring(
                signal.candidates(),
                signal.options(),
                signal.timestampMs(),
                tracker,
                graphScoringPolicy
        );
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SCORING;
    }
}
