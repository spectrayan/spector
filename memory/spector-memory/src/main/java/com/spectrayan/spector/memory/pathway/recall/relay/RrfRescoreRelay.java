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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.graph.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.pathway.pipeline.scorer.SalienceAndHabituationScorer;

/**
 * Relay that re-applies cognitive scoring to RRF fused results.
 */
public final class RrfRescoreRelay implements SynapticRelay<RecallSignal> {
    
    private final SalienceAndHabituationScorer scorer;
    private final CoActivationRecordMemory coActivationTracker;
    private final GraphScoringPolicy graphScoringPolicy;

    public RrfRescoreRelay(final SalienceAndHabituationScorer scorer,
                           final CoActivationRecordMemory coActivationTracker,
                           final GraphScoringPolicy graphScoringPolicy) {
        this.scorer = scorer;
        this.coActivationTracker = coActivationTracker;
        this.graphScoringPolicy = graphScoringPolicy;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (signal.isRrfFused()) {
            final RecallOptions options = signal.options();
            final RecallOptions peekOptions = options.recallMode() == RecallMode.LEARN
                    ? options.toBuilder().recallMode(RecallMode.OBSERVE).build()
                    : options;
            scorer.applyCognitiveScoring(signal.candidates(), peekOptions, signal.timestampMs(), coActivationTracker, graphScoringPolicy);
        }
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.RRF_RESCORE;
    }
}
