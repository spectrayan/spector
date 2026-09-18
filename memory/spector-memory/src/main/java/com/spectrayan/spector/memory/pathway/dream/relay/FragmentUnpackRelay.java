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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Constructive Episodic Simulation Fragment Decomposition (Schacter &amp; Addis, 2007)</h3>
 * <p>Decomposes intact episodic traces into typed constituent semantic primitives (AGENT, ACTION,
 * OBJECT, LOCATION, TEMPORAL, AFFECT) enabling flexible, combinatorial offline recombination
 * across memory traces without destroying latent vector dimensionality.</p>
 *
 * @since 1.4.0
 */
public final class FragmentUnpackRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(FragmentUnpackRelay.class);

    public static final byte DEFAULT_NEUTRAL_VALENCE = 0;
    public static final int DEFAULT_BASELINE_AROUSAL = 50;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.seedMemoryIds().isEmpty()) {
            return true;
        }

        List<String> seedIds = signal.seedMemoryIds();
        List<float[]> seedVectors = signal.seedVectors();
        EntityDirectory entityDir = signal.entityDirectory();

        for (int i = 0; i < seedIds.size(); i++) {
            String seedId = seedIds.get(i);
            float[] vector = i < seedVectors.size() ? seedVectors.get(i) : new float[0];

            byte valence = DEFAULT_NEUTRAL_VALENCE;
            int arousal = DEFAULT_BASELINE_AROUSAL;

            // Decompose the seed engram into canonical cognitive role facets,
            // preserving full vector space dimensionality for SIMD/GPU operations.
            for (FragmentRole role : FragmentRole.values()) {
                int entityId = role.ordinal();
                String label = seedId + ":" + role.name().toLowerCase();

                signal.addFragment(new SceneFragment(
                        seedId,
                        entityId,
                        label,
                        role,
                        vector,
                        valence,
                        arousal
                ));
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("FragmentUnpackRelay: unpacked {} fragments from {} salient seeds",
                    signal.fragments().size(), seedIds.size());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "fragment_unpack";
    }
}
