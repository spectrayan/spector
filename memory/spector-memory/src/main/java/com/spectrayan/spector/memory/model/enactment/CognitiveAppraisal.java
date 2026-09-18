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
package com.spectrayan.spector.memory.model.enactment;
import com.spectrayan.spector.kernel.score.Valence;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Continuous multi-dimensional cognitive appraisal vector mapped to AISME InteroceptiveState (ADR-0032).
 *
 * <p>Grounds subjective evaluation in Lazarus &amp; Scherer Cognitive Appraisal Theory:
 * <ul>
 *   <li><b>Goal Congruence</b> maps to Valence [-1.0, 1.0]</li>
 *   <li><b>Urgency &amp; Stakes</b> maps to Arousal [-1.0, 1.0]</li>
 *   <li><b>Coping Potential</b> maps to Dominance [-1.0, 1.0]</li>
 * </ul>
 * </p>
 */
public record CognitiveAppraisal(
        float goalCongruence,
        float urgencyAndStakes,
        float copingPotential,
        AgencyAttribution agency,
        boolean normativeViolation,
        String primaryConcern
) {
    public CognitiveAppraisal {
        if (Float.isNaN(goalCongruence) || Float.isNaN(urgencyAndStakes) || Float.isNaN(copingPotential)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Appraisal dimensions cannot be NaN");
        }
        agency = (agency != null) ? agency : AgencyAttribution.CIRCUMSTANTIAL;
        primaryConcern = (primaryConcern != null) ? primaryConcern : "";
    }

    public static CognitiveAppraisal neutral() {
        return new CognitiveAppraisal(0.0f, 0.0f, 0.0f, AgencyAttribution.CIRCUMSTANTIAL, false, "neutral");
    }

    public CognitiveAppraisal clamp() {
        float g = Math.max(-1.0f, Math.min(1.0f, goalCongruence));
        float u = Math.max(-1.0f, Math.min(1.0f, urgencyAndStakes));
        float c = Math.max(-1.0f, Math.min(1.0f, copingPotential));
        return new CognitiveAppraisal(g, u, c, agency, normativeViolation, primaryConcern);
    }
}
