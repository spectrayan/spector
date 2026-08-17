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
package com.spectrayan.spector.config;

import com.spectrayan.spector.commons.observation.MemoryObservationHook;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record ObservabilityConfig(
    boolean enabled,
    double samplingRate,
    Map<String, Boolean> observations,
    boolean highCardinalityEnabled
) {
    public static final ObservabilityConfig DEFAULT = new ObservabilityConfig(true, 1.0, Map.of(), true);

    public boolean isEnabled(String observationKey) {
        if (!enabled) {
            return false;
        }
        if (observations != null && observations.containsKey(observationKey)) {
            return Boolean.TRUE.equals(observations.get(observationKey));
        }
        return true;
    }

    public Set<String> computeEnabledObservationSet() {
        if (!enabled) {
            return Set.of();
        }

        Set<String> enabledSet = new HashSet<>(Set.of(
            MemoryObservationHook.EMBEDDING,
            MemoryObservationHook.LLM_INFERENCE,
            MemoryObservationHook.VECTOR_SEARCH,
            MemoryObservationHook.BM25_SEARCH,
            MemoryObservationHook.SPLADE_SEARCH,
            MemoryObservationHook.SCORING,
            MemoryObservationHook.GRAPH_EXPANSION,
            MemoryObservationHook.CONTRADICTION,
            MemoryObservationHook.TAG_EXTRACTION,
            MemoryObservationHook.CHUNKING,
            MemoryObservationHook.ENTITY_EXTRACTION,
            MemoryObservationHook.GRAPH_SYNC,
            MemoryObservationHook.SCORING_COGNITIVE,
            MemoryObservationHook.SCORING_HABITUATION,
            MemoryObservationHook.SCORING_STDP,
            MemoryObservationHook.SCORING_HEBBIAN,
            MemoryObservationHook.SCORING_VALENCE,
            MemoryObservationHook.SCORING_TOPK
        ));

        if (observations != null) {
            observations.forEach((k, v) -> {
                if (Boolean.FALSE.equals(v)) {
                    enabledSet.remove(k);
                } else if (Boolean.TRUE.equals(v)) {
                    enabledSet.add(k);
                }
            });
        }

        return Set.copyOf(enabledSet);
    }
}
