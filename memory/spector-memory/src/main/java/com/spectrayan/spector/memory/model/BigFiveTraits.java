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
package com.spectrayan.spector.memory.model;

/**
 * Big Five (OCEAN) personality traits — continuous 0-100 scores.
 *
 * <h3>Neuroscience Basis</h3>
 * <p>The Big Five model (Costa &amp; McCrae, 1992) is the most empirically
 * validated personality framework. A 2025 large-scale multilevel study
 * directly mapped these traits to autobiographical memory affect intensity:</p>
 *
 * <ul>
 *   <li><b>Neuroticism</b> → amplifies negative valence, dampens positive (amygdala-hippocampus
 *       connectivity dysregulation, not just amygdala hyperreactivity)</li>
 *   <li><b>Extraversion</b> → amplifies positive valence, especially social content</li>
 *   <li><b>Openness</b> → enhances encoding of novel stimuli; protective for episodic memory</li>
 *   <li><b>Conscientiousness</b> → "silver lining" effect — increases positive affect
 *       of negative memories (positive reappraisal)</li>
 *   <li><b>Agreeableness</b> → buffers negative valence across both positive and negative memories</li>
 * </ul>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/personality/BigFiveTraits.yaml} with identical
 * field names and ranges. Scores use {@code float} (0-100) for consistency
 * with the consciousness repo's {@code number} type.</p>
 *
 * <h3>Anti-Gaming</h3>
 * <p>All scores are clamped to [0, 100] in the compact constructor. This is
 * guardrail Layer 1 (input clamping). Layer 2 (output clamping) is applied
 * in {@link PersonalityModifiers}, which caps all derived modifiers to [0.7, 1.3].</p>
 *
 * @param openness          Openness to experience score (0-100)
 * @param conscientiousness Conscientiousness score (0-100)
 * @param extraversion      Extraversion score (0-100)
 * @param agreeableness     Agreeableness score (0-100)
 * @param neuroticism       Neuroticism score (0-100)
 * @see PersonalityModifiers#derive(BigFiveTraits, EmotionalIntelligence, StressResponse)
 */
public record BigFiveTraits(
        float openness,
        float conscientiousness,
        float extraversion,
        float agreeableness,
        float neuroticism
) {

    /**
     * Compact constructor — clamps all scores to valid range.
     * <p>Guardrail L1: even if the user provides neuroticism=999,
     * it's silently clamped to 100.</p>
     */
    public BigFiveTraits {
        openness = Math.clamp(openness, 0f, 100f);
        conscientiousness = Math.clamp(conscientiousness, 0f, 100f);
        extraversion = Math.clamp(extraversion, 0f, 100f);
        agreeableness = Math.clamp(agreeableness, 0f, 100f);
        neuroticism = Math.clamp(neuroticism, 0f, 100f);
    }

    /**
     * Neutral profile — all traits at midpoint. Produces no scoring effect
     * (all derived {@link PersonalityModifiers} will be 1.0).
     */
    public static final BigFiveTraits NEUTRAL = new BigFiveTraits(50f, 50f, 50f, 50f, 50f);

    /**
     * Returns true if all traits are at the neutral midpoint (50).
     * A neutral profile has no effect on scoring.
     */
    public boolean isNeutral() {
        return openness == 50f && conscientiousness == 50f
                && extraversion == 50f && agreeableness == 50f
                && neuroticism == 50f;
    }
}
