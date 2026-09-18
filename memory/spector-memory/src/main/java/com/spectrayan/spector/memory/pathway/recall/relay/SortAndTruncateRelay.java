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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.neuromod.inhibition.SuppressionSet;
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
        
        final SuppressionSet ss = (signal != null && signal.context() != null)
                ? signal.context().find(SuppressionSet.class).orElse(suppressionSet) : suppressionSet;
        if (ss != null) {
            allResults.removeIf(r -> ss.isSuppressed(r.id()));
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
