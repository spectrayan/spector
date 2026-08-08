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
package com.spectrayan.spector.bench.longitudinal;

/**
 * Immutable record computing agent downstream outcome metrics over longitudinal multi-session evaluation runs.
 *
 * @param condition                Experimental condition (e.g., "spector-full", "baseline-vector")
 * @param taskCompletionRate       Percentage of multi-session tasks successfully completed [0.0 - 1.0]
 * @param crossSessionConsistency  Consistency score across session boundaries [0.0 - 1.0]
 * @param preferenceStability      Percentage of stated preferences honored over time [0.0 - 1.0]
 * @param errorNonRepetition       Percentage of previously resolved bugs avoided [0.0 - 1.0]
 * @param contextCoherence         Semantic coherence score across session gaps [0.0 - 1.0]
 * @param totalSessionsEvaluated   Total session count evaluated
 */
public record LongitudinalMetrics(
        String condition,
        double taskCompletionRate,
        double crossSessionConsistency,
        double preferenceStability,
        double errorNonRepetition,
        double contextCoherence,
        int totalSessionsEvaluated
) {
    public LongitudinalMetrics {
        if (taskCompletionRate < 0.0 || taskCompletionRate > 1.0) {
            throw new IllegalArgumentException("taskCompletionRate must be between 0.0 and 1.0");
        }
        if (crossSessionConsistency < 0.0 || crossSessionConsistency > 1.0) {
            throw new IllegalArgumentException("crossSessionConsistency must be between 0.0 and 1.0");
        }
        if (preferenceStability < 0.0 || preferenceStability > 1.0) {
            throw new IllegalArgumentException("preferenceStability must be between 0.0 and 1.0");
        }
        if (errorNonRepetition < 0.0 || errorNonRepetition > 1.0) {
            throw new IllegalArgumentException("errorNonRepetition must be between 0.0 and 1.0");
        }
        if (contextCoherence < 0.0 || contextCoherence > 1.0) {
            throw new IllegalArgumentException("contextCoherence must be between 0.0 and 1.0");
        }
    }
}
