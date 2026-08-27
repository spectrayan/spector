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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 4 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Lewis &amp; Bendor REM Anti-Centroid Pairing</h3>
 * <p>Discovers hyper-associations by intentionally binding semantic fragments that exhibit
 * high geometric distance in embedding space while maintaining relational structural overlap
 * and affective resonance (rhyme).</p>
 *
 * @since 1.4.0
 */
public final class HyperAssociateRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(HyperAssociateRelay.class);

    public static final float WEIGHT_SEMANTIC_DISTANCE = 0.50f;
    public static final float WEIGHT_RELATIONAL_OVERLAP = 0.30f;
    public static final float WEIGHT_AFFECTIVE_RHYME = 0.20f;
    public static final float PAIRING_ACCEPTANCE_THRESHOLD = 0.35f;

    public record FragmentPair(SceneFragment fragmentA, SceneFragment fragmentB, float antiCentroidScore) {}

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.fragments().isEmpty()) {
            return true;
        }

        List<SceneFragment> fragments = signal.fragments();
        List<FragmentPair> pairs = new ArrayList<>();

        for (int i = 0; i < fragments.size(); i++) {
            SceneFragment fA = fragments.get(i);
            for (int j = i + 1; j < fragments.size(); j++) {
                SceneFragment fB = fragments.get(j);

                // Skip pairing fragments from the same original memory trace
                if (fA.sourceMemoryId().equals(fB.sourceMemoryId())) {
                    continue;
                }

                // 1. Semantic Distance (Anti-centroid: prefer distant concepts)
                float cosSim = computeSimilarity(fA.embedding(), fB.embedding());
                float semDistance = Math.max(0.0f, 1.0f - cosSim);

                // 2. Relational Role Complementarity
                float relOverlap = computeRoleComplementarity(fA.role(), fB.role());

                // 3. Affective Rhyme (Valence alignment / emotional resonance)
                float affRhyme = 1.0f - (Math.abs((float) fA.valence() - (float) fB.valence()) / 100.0f);

                float antiCentroidScore = semDistance * WEIGHT_SEMANTIC_DISTANCE + relOverlap * WEIGHT_RELATIONAL_OVERLAP + affRhyme * WEIGHT_AFFECTIVE_RHYME;

                if (antiCentroidScore > PAIRING_ACCEPTANCE_THRESHOLD) {
                    pairs.add(new FragmentPair(fA, fB, antiCentroidScore));
                }
            }
        }

        pairs.sort(Comparator.comparingDouble(FragmentPair::antiCentroidScore).reversed());

        if (log.isDebugEnabled()) {
            log.debug("HyperAssociateRelay: discovered {} anti-centroid hyper-associative candidate pairs", pairs.size());
        }

        return true;
    }

    private static float computeSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0f;
        int minLen = Math.min(a.length, b.length);
        float dot = 0.0f, normA = 0.0f, normB = 0.0f;
        for (int k = 0; k < minLen; k++) {
            dot += a[k] * b[k];
            normA += a[k] * a[k];
            normB += b[k] * b[k];
        }
        if (normA == 0.0f || normB == 0.0f) return 0.0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static float computeRoleComplementarity(FragmentRole rA, FragmentRole rB) {
        if (rA == null || rB == null) return 0.5f;
        if (rA != rB) {
            // Complementary roles (e.g. AGENT + ACTION, ACTION + OBJECT) form coherent scenes
            return 1.0f;
        }
        return 0.4f;
    }

    @Override
    public String relayName() {
        return "hyper_associate";
    }
}
