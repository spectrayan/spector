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
 * Emotional Intelligence (EQ) — Goleman's five-dimension model.
 *
 * <h3>Neuroscience Basis</h3>
 * <p>Goleman's (1995) EQ framework maps to distinct neural systems that directly
 * modulate memory encoding, consolidation, and retrieval (2023-2025 studies):</p>
 *
 * <ul>
 *   <li><b>Self-Awareness</b> → better attentional control → deeper encoding of significant
 *       events; mediated by insula-prefrontal connectivity</li>
 *   <li><b>Self-Regulation</b> → PFC top-down control over limbic structures prevents
 *       encoding "blocking" from extreme stress → compresses arousal extremes</li>
 *   <li><b>Motivation</b> → goal-directed encoding strengthens future-relevant memories;
 *       2025 research shows intentional memory control can override involuntary arousal</li>
 *   <li><b>Empathy</b> → enriched social-emotional processing creates more interconnected
 *       memory traces → valence enrichment for interpersonal memories</li>
 *   <li><b>Social Skills</b> → facilitates spreading activation through social-relational
 *       networks → Hebbian co-activation boost for social content</li>
 * </ul>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/personality/EmotionalIntelligence.yaml} with identical
 * field names and ranges. Uses {@code int} (1-100) matching the YAML {@code integer} type.</p>
 *
 * <h3>Anti-Gaming</h3>
 * <p>All scores are clamped to [1, 100] in the compact constructor (guardrail L1).
 * Minimum is 1, not 0, following the consciousness schema's {@code minimum: 1}.</p>
 *
 * @param selfAwareness  Self-awareness score (1-100)
 * @param selfRegulation Self-regulation score (1-100)
 * @param motivation     Motivation score (1-100)
 * @param empathy        Empathy score (1-100)
 * @param socialSkills   Social skills score (1-100)
 * @see PersonalityModifiers#derive(BigFiveTraits, EmotionalIntelligence, StressResponse)
 */
public record EmotionalIntelligence(
        int selfAwareness,
        int selfRegulation,
        int motivation,
        int empathy,
        int socialSkills
) {

    /**
     * Compact constructor — clamps all scores to valid range [1, 100].
     * <p>Guardrail L1: minimum is 1, not 0, matching the consciousness
     * schema's {@code minimum: 1} constraint.</p>
     */
    public EmotionalIntelligence {
        selfAwareness = Math.clamp(selfAwareness, 1, 100);
        selfRegulation = Math.clamp(selfRegulation, 1, 100);
        motivation = Math.clamp(motivation, 1, 100);
        empathy = Math.clamp(empathy, 1, 100);
        socialSkills = Math.clamp(socialSkills, 1, 100);
    }

    /**
     * Neutral profile — all dimensions at midpoint. Produces no scoring effect
     * (all derived {@link PersonalityModifiers} will be 1.0).
     */
    public static final EmotionalIntelligence NEUTRAL =
            new EmotionalIntelligence(50, 50, 50, 50, 50);

    /**
     * Returns true if all dimensions are at the neutral midpoint (50).
     */
    public boolean isNeutral() {
        return selfAwareness == 50 && selfRegulation == 50
                && motivation == 50 && empathy == 50 && socialSkills == 50;
    }
}
