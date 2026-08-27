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
import com.spectrayan.spector.core.spi.AcceleratorRegistry;
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

                // 1. Semantic Distance (Anti-centroid: prefer distant concepts) via SPI CosineSimilarity
                float cosSim = 0.0f;
                if (fA.embedding() != null && fB.embedding() != null && fA.embedding().length == fB.embedding().length && fA.embedding().length > 0) {
                    cosSim = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(fA.embedding(), fB.embedding(), 1, fA.embedding().length)[0];
                }
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
