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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.hopfield.AttractorState;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 relay in {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway} that performs continuous Hopfield energy relaxation.
 *
 * <h3>Biological Analog: CA3 Autoassociative Pattern Completion & Attractor Hopping</h3>
 * <p>Relaxes sampled episodic memory vectors into collective energy basins across the memory pool,
 * discovering non-obvious associative links between disparate concepts.</p>
 *
 * @since 1.2.0
 */
public final class HopfieldMindWanderingRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(HopfieldMindWanderingRelay.class);

    private final ContinuousHopfieldNetwork defaultNetwork = new ContinuousHopfieldNetwork(5, 1e-4f);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || signal.sampledVectors().size() < 2) {
            return true;
        }

        List<float[]> sampled = signal.sampledVectors();
        List<String> ids = signal.sampledMemoryIds();
        ContinuousHopfieldNetwork chn = signal.hopfieldNetwork() != null ? signal.hopfieldNetwork() : defaultNetwork;

        float[][] patterns = sampled.toArray(new float[0][]);
        float beta = signal.hopfieldTemperature();

        int seedCount = Math.min(sampled.size(), 8);
        for (int i = 0; i < seedCount; i++) {
            float[] seed = sampled.get(i);
            String seedId = ids.get(i);

            AttractorState state = chn.retrieveAttractor(seed, patterns, beta);
            float[] weights = state.attentionWeights();

            // Find best non-identical pattern match
            float bestWeight = 0.0f;
            int bestIdx = -1;
            for (int j = 0; j < weights.length; j++) {
                if (j != i && weights[j] > bestWeight) {
                    bestWeight = weights[j];
                    bestIdx = j;
                }
            }

            if (bestIdx >= 0 && bestWeight >= signal.synergyThreshold()) {
                String targetId = ids.get(bestIdx);
                float weightDelta = bestWeight * 0.1f;
                signal.discoveredAssociations().add(
                        new WanderSignal.DiscoveredAssociation(seedId, targetId, bestWeight, weightDelta)
                );
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("HopfieldMindWanderingRelay: discovered {} candidate associative attractors",
                    signal.discoveredAssociations().size());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "hopfield_mind_wandering";
    }
}
