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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contradiction and preference stability detector for longitudinal agent evaluation trajectories.
 */
public class ConsistencyDetector {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyDetector.class);

    /**
     * Evaluates cross-session preference stability.
     *
     * @param groundTruthPreferences Ground-truth key-value preferences expected to be maintained
     * @param recalledContent        Content recalled / generated during session
     * @return Score between 0.0 (all contradicted) and 1.0 (all respected)
     */
    public double evaluatePreferenceStability(Map<String, String> groundTruthPreferences, List<String> recalledContent) {
        Objects.requireNonNull(groundTruthPreferences, "groundTruthPreferences cannot be null");
        Objects.requireNonNull(recalledContent, "recalledContent cannot be null");

        if (groundTruthPreferences.isEmpty()) {
            return 1.0;
        }

        int matchedCount = 0;
        String combinedRecalled = String.join(" ", recalledContent).toLowerCase();

        for (Map.Entry<String, String> entry : groundTruthPreferences.entrySet()) {
            String expectedVal = entry.getValue().toLowerCase();
            if (combinedRecalled.contains(expectedVal)) {
                matchedCount++;
            }
        }

        double score = (double) matchedCount / groundTruthPreferences.size();
        log.debug("Evaluated preference stability: {}/{} matched -> score {}", matchedCount, groundTruthPreferences.size(), score);
        return score;
    }

    /**
     * Evaluates error non-repetition rate (checking if negative constraints / resolved bugs were avoided).
     *
     * @param negativeConstraints List of negative constraints / previously fixed bug markers
     * @param recalledContent     Content recalled / generated during session
     * @return Score between 0.0 (all bugs re-introduced) and 1.0 (all bugs avoided)
     */
    public double evaluateErrorNonRepetition(List<String> negativeConstraints, List<String> recalledContent) {
        Objects.requireNonNull(negativeConstraints, "negativeConstraints cannot be null");
        Objects.requireNonNull(recalledContent, "recalledContent cannot be null");

        if (negativeConstraints.isEmpty()) {
            return 1.0;
        }

        int violations = 0;
        String combinedRecalled = String.join(" ", recalledContent).toLowerCase();

        for (String constraint : negativeConstraints) {
            if (combinedRecalled.contains(constraint.toLowerCase())) {
                violations++;
                log.warn("Detected constraint violation / bug re-introduction: {}", constraint);
            }
        }

        double score = 1.0 - ((double) violations / negativeConstraints.size());
        return Math.max(0.0, score);
    }
}
