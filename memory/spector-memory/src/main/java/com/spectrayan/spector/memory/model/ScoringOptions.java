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

import com.spectrayan.spector.memory.synapse.TwoFactorConfig;

/**
 * Scoring parameters for recall queries.
 *
 * <p>Controls how cognitive scores are computed — similarity weight, importance
 * weight, tag relevance boost, strictness, valence alignment, two-factor memory,
 * and scoring mode.</p>
 *
 * @param alpha                     similarity weight in fused score (default: 0.6)
 * @param beta                      importance × decay weight (default: 0.4)
 * @param tagRelevanceBoost         weighted tag overlap boost (default: 0.3)
 * @param semanticCandidateMultiplier HNSW over-fetch multiplier (default: 3)
 * @param strictnessCoefficient     similarity cliff steepness (default: 1.0)
 * @param queryValence              emotional valence for state-dependent recall
 * @param enableValenceAlignment    enable valence proximity scoring
 * @param twoFactorConfig           Bjork & Bjork retrieval/storage strength config
 * @param scoringMode               COGNITIVE or SIMILARITY scoring
 */
public record ScoringOptions(
        float alpha,
        float beta,
        float tagRelevanceBoost,
        int semanticCandidateMultiplier,
        float strictnessCoefficient,
        byte queryValence,
        boolean enableValenceAlignment,
        TwoFactorConfig twoFactorConfig,
        ScoringMode scoringMode
) {
    /** Default balanced cognitive scoring. */
    public static final ScoringOptions DEFAULT = new ScoringOptions(
            0.6f, 0.4f, 0.3f, 3, 1.0f,
            (byte) 0, false, TwoFactorConfig.DEFAULT, ScoringMode.COGNITIVE);
}
