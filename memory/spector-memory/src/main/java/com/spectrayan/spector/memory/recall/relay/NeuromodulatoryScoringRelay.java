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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pipeline.scorer.SalienceAndHabituationScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies cognitive neuromodulatory scoring such as habituation, and STDP boost.
 */
public final class NeuromodulatoryScoringRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(NeuromodulatoryScoringRelay.class);
    
    private final SalienceAndHabituationScorer salienceScorer;
    private final CoActivationRecordMemory coActivationTracker;
    private final GraphScoringPolicy graphScoringPolicy;

    public NeuromodulatoryScoringRelay(
            final SalienceAndHabituationScorer salienceScorer,
            final CoActivationRecordMemory coActivationTracker,
            final GraphScoringPolicy graphScoringPolicy) {
        this.salienceScorer = salienceScorer;
        this.coActivationTracker = coActivationTracker;
        this.graphScoringPolicy = graphScoringPolicy;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        salienceScorer.applyCognitiveScoring(
                signal.candidates(),
                signal.options(),
                signal.timestampMs(),
                coActivationTracker,
                graphScoringPolicy
        );
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SCORING;
    }
}
