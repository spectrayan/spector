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
package com.spectrayan.spector.memory.pipeline.scorer;

import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;

/**
 * Computes novelty scores, habituation decay penalties, emotional valence/arousal
 * modulation, and prospective reminder triggers.
 */
public class SalienceAndHabituationScorer {

    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;

    public SalienceAndHabituationScorer(SuppressionSet suppressionSet, HabituationPenalty habituationPenalty) {
        this.suppressionSet = suppressionSet;
        this.habituationPenalty = habituationPenalty;
    }

    public SuppressionSet suppressionSet() {
        return suppressionSet;
    }

    public HabituationPenalty habituationPenalty() {
        return habituationPenalty;
    }
}
