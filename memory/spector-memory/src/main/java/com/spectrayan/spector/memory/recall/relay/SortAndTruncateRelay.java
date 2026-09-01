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
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Relay that filters suppressed memories, sorts by score, and truncates to top K.
 */
public final class SortAndTruncateRelay implements SynapticRelay<RecallSignal> {

    private final SuppressionSet suppressionSet;

    public SortAndTruncateRelay(final SuppressionSet suppressionSet) {
        this.suppressionSet = suppressionSet;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        List<CognitiveResult> allResults = signal.candidates();
        
        if (suppressionSet != null) {
            allResults.removeIf(r -> suppressionSet.isSuppressed(r.id()));
        }
        
        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed().thenComparing(CognitiveResult::id));
        final int topK = signal.options().topK();
        if (allResults.size() > topK) {
            allResults = new ArrayList<>(allResults.subList(0, topK));
            signal.setCandidates(allResults);
        }
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SORT_TRUNCATE;
    }
}
