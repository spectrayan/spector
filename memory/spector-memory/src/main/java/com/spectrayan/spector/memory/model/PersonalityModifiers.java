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
 * Derived scoring modifiers — the numerical bridge between personality data
 * and the cognitive scoring pipeline.
 *
 * <h3>Purpose</h3>
 * <p>Raw personality data ({@link BigFiveTraits}, {@link EmotionalIntelligence},
 * {@link StressResponse}) is descriptive. The scoring pipeline needs numerical
 * multipliers. This record converts descriptive traits into scoring signals
 * via the {@link #derive} factory method.</p>
 *
 * <h3>Anti-Gaming: Output Clamping (Layer 2)</h3>
 * <p>Every modifier is clamped to [{@value #MIN_MOD}, {@value #MAX_MOD}] regardless
 * of input. Even if a user sets neuroticism=100 and self-regulation=0, the maximum
 * scoring effect is ±15%. This bounds the persona's influence to a <b>re-ranking
 * signal</b>, not a scoring override.</p>
 *
 * <p>Why ±15%? The SRE shows 2-3× encoding advantage, but that's for encoding
 * strategy — not importance scoring. A 1.3× multiplier on a [0.05, 10.0] importance
 * scale is a meaningful but bounded re-ranking signal. Starting conservatively
 * at ±15% allows empirical calibration before widening. Compare: {@link InterestLevel}
 * uses [0.1, 2.0]× — persona influence is deliberately weaker than explicit interest
 * declaration because it's a passive signal.</p>
 *
 * <h3>Derivation Functions</h3>
 * <p>Each modifier is derived from one or more personality dimensions using linear
 * mapping. The general pattern:</p>
 * <pre>
 *   modifier = centerValue + (trait - midpoint) / range × amplitude
 * </pre>
 * <p>Where the amplitude (0.3) and midpoint are chosen to produce output in
 * [MIN_MOD, MAX_MOD] for input in [0, 100].</p>
 *
 * <h3>Scoring Pipeline Usage</h3>
 * <ul>
 *   <li>{@code valenceAmplification} → applied in {@link SalienceProfile#modulateValence}</li>
 *   <li>{@code arousalCompression} → applied in {@link SalienceProfile#modulateArousal}</li>
 *   <li>{@code noveltyAffinity} → modulates surprise detector output</li>
 *   <li>{@code goalRelevanceBoost} → modulates importance for aspirations-matching content</li>
 *   <li>{@code selfRelevanceWeight} → master weight for all self-congruent content</li>
 * </ul>
 *
 * @param valenceAmplification    [0.7, 1.3] — f(neuroticism, extraversion); high neuroticism amplifies valence
 * @param arousalCompression      [0.7, 1.3] — f(selfRegulation); high self-regulation compresses arousal
 * @param noveltyAffinity         [0.7, 1.3] — f(openness); high openness increases novelty affinity
 * @param negativeValenceBuffer   [0.7, 1.3] — f(agreeableness); high agreeableness dampens negative valence
 * @param positiveReappraisal     [0.7, 1.3] — f(conscientiousness); "silver lining" effect
 * @param goalRelevanceBoost      [0.7, 1.3] — f(motivation); boosts importance for goal-congruent content
 * @param socialMemoryEnrichment  [0.7, 1.3] — f(empathy, socialSkills); enriches social memory valence
 * @param stressEncodingQuality   [0.5, 1.2] — f(stressResponse); encoding quality under stress
 * @param selfRelevanceWeight     [0.7, 1.3] — master weight for self-congruent content boost
 * @see BigFiveTraits
 * @see EmotionalIntelligence
 * @see StressResponse
 */
public record PersonalityModifiers(
        float valenceAmplification,
        float arousalCompression,
        float noveltyAffinity,
        float negativeValenceBuffer,
        float positiveReappraisal,
        float goalRelevanceBoost,
        float socialMemoryEnrichment,
        float stressEncodingQuality,
        float selfRelevanceWeight
) {

    /** Minimum modifier value — bounds the persona's scoring influence. */
    static final float MIN_MOD = 0.85f;

    /** Maximum modifier value — bounds the persona's scoring influence. */
    static final float MAX_MOD = 1.15f;

    /** Minimum stress encoding quality (Freeze response). */
    private static final float MIN_STRESS = 0.5f;

    /** Maximum stress encoding quality (Fight response). */
    private static final float MAX_STRESS = 1.2f;

    /**
     * Compact constructor — clamps ALL modifiers to safe ranges.
     * <p>Guardrail L2: no matter what derivation function produces (amplitude=0.3),
     * the scoring pipeline never sees a modifier outside these bounds.</p>
     */
    public PersonalityModifiers {
        valenceAmplification = Math.clamp(valenceAmplification, MIN_MOD, MAX_MOD);
        arousalCompression = Math.clamp(arousalCompression, MIN_MOD, MAX_MOD);
        noveltyAffinity = Math.clamp(noveltyAffinity, MIN_MOD, MAX_MOD);
        negativeValenceBuffer = Math.clamp(negativeValenceBuffer, MIN_MOD, MAX_MOD);
        positiveReappraisal = Math.clamp(positiveReappraisal, MIN_MOD, MAX_MOD);
        goalRelevanceBoost = Math.clamp(goalRelevanceBoost, MIN_MOD, MAX_MOD);
        socialMemoryEnrichment = Math.clamp(socialMemoryEnrichment, MIN_MOD, MAX_MOD);
        stressEncodingQuality = Math.clamp(stressEncodingQuality, MIN_STRESS, MAX_STRESS);
        selfRelevanceWeight = Math.clamp(selfRelevanceWeight, MIN_MOD, MAX_MOD);
    }

    /**
     * Neutral modifiers — all 1.0, no scoring effect.
     * <p>Used when no personality data is available (backward compatibility).</p>
     */
    public static final PersonalityModifiers NEUTRAL = new PersonalityModifiers(
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);

    /**
     * Derives scoring modifiers from personality, EQ, and stress response data.
     *
     * <h4>Derivation Strategy</h4>
     * <p>Each modifier uses a linear mapping from the input trait range to the
     * output modifier range. The general formula:</p>
     * <pre>
     *   modifier = center + (trait - midpoint) / range × amplitude
     * </pre>
     * <p>For Big Five traits (0-100 range, midpoint 50):
     * {@code modifier = 1.0 + (trait - 50) / 100 × 0.3} → output range [0.85, 1.15]</p>
     *
     * <h4>Scientific Basis for Each Derivation</h4>
     * <ul>
     *   <li><b>valenceAmplification</b>: High neuroticism amplifies negative valence,
     *       dampens positive (2025 multilevel study: neuroticism associated with more
     *       negative and less positive affect intensity in autobiographical memories)</li>
     *   <li><b>arousalCompression</b>: High self-regulation compresses arousal extremes
     *       via PFC top-down control over limbic structures (2024 fMRI connectivity studies)</li>
     *   <li><b>noveltyAffinity</b>: High openness → novel stimuli are more engaging;
     *       protective factor for episodic memory networks in aging (2024 OUP study)</li>
     *   <li><b>negativeValenceBuffer</b>: High agreeableness buffers negative affect
     *       across both positive and negative memories (2025 multilevel study)</li>
     *   <li><b>positiveReappraisal</b>: High conscientiousness → "silver lining" effect;
     *       higher positive affect intensity for negative memories (2025 study)</li>
     *   <li><b>goalRelevanceBoost</b>: High motivation → goal-directed encoding;
     *       intentional memory control overrides involuntary arousal (2025 study)</li>
     *   <li><b>socialMemoryEnrichment</b>: High empathy + social skills → enriched
     *       social-emotional memory traces (social cognition literature)</li>
     *   <li><b>stressEncodingQuality</b>: Direct from {@link StressResponse#encodingQuality()};
     *       cortisol-mediated encoding quality differences</li>
     *   <li><b>selfRelevanceWeight</b>: Average of self-awareness + motivation;
     *       higher executive function → stronger self-model → more self-referential encoding</li>
     * </ul>
     *
     * @param b5     Big Five traits (null → NEUTRAL)
     * @param eq     Emotional Intelligence (null → NEUTRAL)
     * @param stress Stress Response (null → ADAPTIVE)
     * @return derived modifiers with all values clamped to safe ranges
     */
    public static PersonalityModifiers derive(BigFiveTraits b5,
                                               EmotionalIntelligence eq,
                                               StressResponse stress) {
        if (b5 == null) b5 = BigFiveTraits.NEUTRAL;
        if (eq == null) eq = EmotionalIntelligence.NEUTRAL;
        if (stress == null) stress = StressResponse.ADAPTIVE;

        return new PersonalityModifiers(
                // Neuroticism amplifies valence extremes:
                // N=0 → 0.85 (dampened), N=50 → 1.0 (neutral), N=100 → 1.15 (amplified)
                1.0f + (b5.neuroticism() - 50f) / 100f * 0.3f,

                // Self-regulation compresses arousal (inverted: high reg = lower multiplier):
                // SR=100 → 0.85 (compressed), SR=50 → 1.0 (neutral), SR=1 → 1.15 (expanded)
                1.15f - (eq.selfRegulation() / 100f) * 0.3f,

                // Openness increases novelty affinity:
                // O=0 → 0.85, O=50 → 1.0, O=100 → 1.15
                0.85f + (b5.openness() / 100f) * 0.3f,

                // Agreeableness buffers negative valence (inverted: high = more buffering = lower mult):
                // A=100 → 0.85 (strong buffer), A=50 → 1.0 (neutral), A=0 → 1.15 (no buffer)
                1.15f - (b5.agreeableness() / 100f) * 0.3f,

                // Conscientiousness enables positive reappraisal:
                // C=0 → 0.85, C=50 → 1.0, C=100 → 1.15
                0.85f + (b5.conscientiousness() / 100f) * 0.3f,

                // Motivation boosts goal-relevant importance:
                // M=1 → ~0.85, M=50 → 1.0, M=100 → 1.15
                0.85f + (eq.motivation() / 100f) * 0.3f,

                // Empathy + social skills enrich social memory:
                // avg(E,S)=1 → ~0.85, avg=50 → 1.0, avg=100 → 1.15
                0.85f + ((eq.empathy() + eq.socialSkills()) / 200f) * 0.3f,

                // Stress response encoding quality — direct from enum:
                stress.encodingQuality(),

                // Self-relevance master weight — average of self-awareness + motivation:
                // avg(SA,M)=1 → ~0.85, avg=50 → 1.0, avg=100 → 1.15
                0.85f + ((eq.selfAwareness() + eq.motivation()) / 200f) * 0.3f
        );
    }

    /**
     * Returns true if all modifiers are at the neutral value (1.0).
     */
    public boolean isNeutral() {
        return valenceAmplification == 1.0f
                && arousalCompression == 1.0f
                && noveltyAffinity == 1.0f
                && negativeValenceBuffer == 1.0f
                && positiveReappraisal == 1.0f
                && goalRelevanceBoost == 1.0f
                && socialMemoryEnrichment == 1.0f
                && stressEncodingQuality == 1.0f
                && selfRelevanceWeight == 1.0f;
    }
}
