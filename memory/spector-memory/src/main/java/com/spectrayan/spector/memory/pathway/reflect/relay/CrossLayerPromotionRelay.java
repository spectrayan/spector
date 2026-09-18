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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-Layer Schema Integration & STC Cross-Capture Relay.
 *
 * <p>Promotes strong statistical co-activation edges (Hebbian) into structured entity-level
 * relations, and propagates STC plasticity boosts to existing entity hyperedges.</p>
 */
public final class CrossLayerPromotionRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(CrossLayerPromotionRelay.class);

    private static final float HEBBIAN_PROMOTION_MIN_WEIGHT = SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_PROMOTION_MIN_WEIGHT;
    private static final float CROSS_CAPTURE_MIN_WEIGHT = SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_MIN_WEIGHT;
    private static final float CROSS_CAPTURE_SCALE_FACTOR = SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_SCALE_FACTOR;
    private static final float CROSS_CAPTURE_MAX_BOOST = 0.3f;

    @Override
    public boolean transmit(final ReflectSignal signal) {
        final EntityDirectory entityDirectory = signal.entityDirectory();
        final HyperEntityGraphMemory hyperEntityGraph = signal.hyperEntityGraph();
        final HebbianGraphBase hebbianGraph = signal.hebbianGraph();

        if (entityDirectory == null || entityDirectory.entityCount() == 0
                || hyperEntityGraph == null || hebbianGraph == null) {
            return true;
        }

        try {
            // Build reverse index: memoryIdx -> List<entityId>
            int ecnt = entityDirectory.entityCount();
            Map<Integer, List<Integer>> memToEntities = new HashMap<>();
            for (int e = 0; e < ecnt; e++) {
                int refCount = entityDirectory.memoryRefCount(e);
                for (int r = 0; r < refCount; r++) {
                    int memIdx = entityDirectory.memoryRefAt(e, r);
                    if (memIdx >= 0) {
                        memToEntities.computeIfAbsent(memIdx, k -> new ArrayList<>(2)).add(e);
                    }
                }
            }

            int promoted = 0;
            int captured = 0;
            int capacity = hebbianGraph.capacity();

            for (int nodeA = 0; nodeA < capacity; nodeA++) {
                var edges = hebbianGraph.neighbors(nodeA);
                for (var edge : edges) {
                    if (edge.weight() < Math.min(HEBBIAN_PROMOTION_MIN_WEIGHT, CROSS_CAPTURE_MIN_WEIGHT)) {
                        break;
                    }
                    int nodeB = edge.neighborIndex();
                    if (nodeB <= nodeA) continue;

                    var entitiesA = memToEntities.get(nodeA);
                    var entitiesB = memToEntities.get(nodeB);
                    if (entitiesA == null || entitiesB == null) continue;

                    // 1. Cross-layer promotion
                    if (edge.weight() >= HEBBIAN_PROMOTION_MIN_WEIGHT) {
                        for (int eA : entitiesA) {
                            for (int eB : entitiesB) {
                                if (eA != eB) {
                                    hyperEntityGraph.addHyperedge(
                                            new int[]{eA, eB},
                                            new int[]{HyperEntityGraphMemory.ROLE_SUBJECT, HyperEntityGraphMemory.ROLE_OBJECT},
                                            0,
                                            1.0f, -1, System.currentTimeMillis());
                                    promoted++;
                                }
                            }
                        }
                    }

                    // 2. STC Cross-capture boost
                    if (edge.weight() >= CROSS_CAPTURE_MIN_WEIGHT) {
                        float boost = Math.min(edge.weight() * CROSS_CAPTURE_SCALE_FACTOR, CROSS_CAPTURE_MAX_BOOST);
                        for (int eA : entitiesA) {
                            for (int eB : entitiesB) {
                                if (eA != eB) {
                                    if (hyperEntityGraph.boostHyperedgeWeight(eA, eB, boost)) {
                                        captured++;
                                        signal.graphMetrics().recordCrossCapture();
                                    }
                                    if (hyperEntityGraph.boostHyperedgeWeight(eB, eA, boost)) {
                                        captured++;
                                        signal.graphMetrics().recordCrossCapture();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (promoted > 0 || captured > 0) {
                log.debug("Cross-Layer Integration: promoted {} relations, boosted {} edges via STC",
                        promoted, captured);
            }
        } catch (Exception e) {
            log.warn("Cross-layer promotion failed: {}", e.getMessage(), e);
        }
        return true;
    }
}
