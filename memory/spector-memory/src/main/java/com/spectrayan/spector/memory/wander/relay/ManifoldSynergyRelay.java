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
package com.spectrayan.spector.memory.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 4 relay in {@link com.spectrayan.spector.memory.WanderPathway} that validates associative attractors against the Riemannian cognitive manifold.
 *
 * <h3>Biological Analog: Entorhinal Grid Cell & Medial Prefrontal Schema Mapping</h3>
 * <p>Ensures that spontaneous associative hops conform to the agent's internalized Riemannian
 * conceptual manifold \(G(s)\), filtering out false topological bridges.</p>
 *
 * @since 1.2.0
 */
public final class ManifoldSynergyRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(ManifoldSynergyRelay.class);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || signal.discoveredAssociations().isEmpty() || signal.cognitiveManifold() == null) {
            return true;
        }

        CognitiveManifold manifold = signal.cognitiveManifold();
        List<WanderSignal.DiscoveredAssociation> original = signal.discoveredAssociations();
        List<WanderSignal.DiscoveredAssociation> filtered = new ArrayList<>(original.size());

        List<float[]> vectors = signal.sampledVectors();
        List<String> ids = signal.sampledMemoryIds();

        for (WanderSignal.DiscoveredAssociation assoc : original) {
            int idx1 = ids.indexOf(assoc.sourceId());
            int idx2 = ids.indexOf(assoc.targetId());

            if (idx1 >= 0 && idx2 >= 0 && idx1 < vectors.size() && idx2 < vectors.size()) {
                float[] v1 = vectors.get(idx1);
                float[] v2 = vectors.get(idx2);

                if (v1.length == manifold.dimensions() && v2.length == manifold.dimensions()) {
                    float sqDist = manifold.squaredDistance(v1, v2);
                    float manifoldSynergy = (float) Math.exp(-0.5f * sqDist);

                    if (manifoldSynergy >= 0.2f) {
                        float combinedSynergy = (assoc.synergy() + manifoldSynergy) * 0.5f;
                        filtered.add(new WanderSignal.DiscoveredAssociation(
                                assoc.sourceId(), assoc.targetId(), combinedSynergy, assoc.weightDelta() * (1.0f + combinedSynergy)
                        ));
                    }
                    continue;
                }
            }
            filtered.add(assoc);
        }

        signal.discoveredAssociations().clear();
        signal.discoveredAssociations().addAll(filtered);

        if (log.isDebugEnabled()) {
            log.debug("ManifoldSynergyRelay: validated {} associations on Riemannian manifold", filtered.size());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "manifold_synergy";
    }
}
