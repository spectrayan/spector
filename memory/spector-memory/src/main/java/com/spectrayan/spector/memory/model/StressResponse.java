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
package com.spectrayan.spector.memory.model;

/**
 * Stress response archetype — how the individual typically responds to stress.
 *
 * <h3>Neuroscience Basis</h3>
 * <p>Stress response type directly affects memory encoding quality via the
 * HPA axis (hypothalamic-pituitary-adrenal) and cortisol release:</p>
 *
 * <ul>
 *   <li><b>Fight</b> — enhanced encoding of threat-related content;
 *       high cortisol → amygdala-enhanced but narrowly focused encoding</li>
 *   <li><b>Flight</b> — rapid but shallow encoding;
 *       moderate cortisol → fast but less-detailed memory traces</li>
 *   <li><b>Freeze</b> — fragmented encoding (can produce dissociative gaps);
 *       extreme cortisol → impaired hippocampal function</li>
 *   <li><b>Fawn</b> — enhanced social/relational encoding;
 *       moderate cortisol → heightened social processing</li>
 *   <li><b>Adaptive</b> — context-appropriate encoding;
 *       regulated cortisol → optimal encoding across contexts</li>
 * </ul>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/personality/StressResponse.yaml} with identical
 * enum values.</p>
 *
 * <h3>Scoring Impact</h3>
 * <p>Each archetype carries an {@link #encodingQuality()} multiplier used in
 * {@link PersonalityModifiers#derive} to modulate the
 * {@code stressEncodingQuality} modifier. This affects importance scoring
 * only under high-arousal conditions.</p>
 *
 * @see PersonalityModifiers
 */
public enum StressResponse {

    /**
     * Fight response — enhanced threat encoding.
     * Encoding quality: 1.2× (high cortisol enhances encoding, but narrows focus).
     */
    FIGHT(1.2f),

    /**
     * Flight response — rapid but shallow encoding.
     * Encoding quality: 0.8× (speed over depth).
     */
    FLIGHT(0.8f),

    /**
     * Freeze response — fragmented encoding.
     * Encoding quality: 0.5× (extreme cortisol impairs hippocampal function).
     */
    FREEZE(0.5f),

    /**
     * Fawn response — enhanced social encoding.
     * Encoding quality: 1.0× (neutral overall, but enriches social content).
     */
    FAWN(1.0f),

    /**
     * Adaptive response — context-appropriate encoding.
     * Encoding quality: 1.0× (optimal, no distortion).
     */
    ADAPTIVE(1.0f);

    private final float encodingQuality;

    StressResponse(float encodingQuality) {
        this.encodingQuality = encodingQuality;
    }

    /**
     * Returns the encoding quality multiplier for this stress response archetype.
     * Applied to the {@code stressEncodingQuality} modifier in
     * {@link PersonalityModifiers}.
     */
    public float encodingQuality() {
        return encodingQuality;
    }
}
