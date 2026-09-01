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
package com.spectrayan.spector.core.spacetime;

/**
 * Grammatical and epistemic tense context for the Express verbalization pathway (ADR-0031 Part E).
 *
 * <ul>
 *   <li>{@link #FACT}: Factual waking statements as-of-now; synthetic, dreamed, and future rows suppressed.</li>
 *   <li>{@link #SIM}: Generative simulation and prospective scenarios; passes through dreamed/wandered seeds.</li>
 *   <li>{@link #REPLAY}: Historical or counterfactual retrospection evaluated at explicit replay clock.</li>
 * </ul>
 *
 * @since 1.5.0
 */
public enum ExpressTense {

    /**
     * Waking factual tense: only verified waking episodic and semantic memories are expressed.
     */
    FACT,

    /**
     * Hypothetical / simulated tense: prospective simulations and dream scenarios labeled as simulated.
     */
    SIM,

    /**
     * Retrospective replay tense: historical reconstruction at a specific historical point in time.
     */
    REPLAY
}
