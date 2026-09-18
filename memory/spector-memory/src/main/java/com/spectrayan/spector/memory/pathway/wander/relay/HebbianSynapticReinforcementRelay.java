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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 5 relay in {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway} that reinforces Hebbian synaptic edges for discovered associations.
 *
 * <h3>Biological Analog: Long-Term Potentiation (LTP) during Wakeful Rest</h3>
 * <p>Reinforces synaptic connections between concepts co-activated in the Default Mode Network,
 * creating associative shortcuts across episodic memory clusters.</p>
 *
 * @since 1.2.0
 */
public final class HebbianSynapticReinforcementRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(HebbianSynapticReinforcementRelay.class);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || signal.discoveredAssociations().isEmpty()) {
            return true;
        }

        HebbianGraphBase graph = signal.hebbianGraph();
        for (WanderSignal.DiscoveredAssociation assoc : signal.discoveredAssociations()) {
            signal.addAssociationsFormed(1);
            signal.recordWeightDelta(assoc.weightDelta());

            if (graph != null) {
                int nodeA = parseSlot(assoc.sourceId());
                int nodeB = parseSlot(assoc.targetId());
                if (nodeA >= 0 && nodeB >= 0 && nodeA < graph.capacity() && nodeB < graph.capacity() && nodeA != nodeB) {
                    graph.strengthen(nodeA, nodeB, assoc.weightDelta());
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("HebbianSynapticReinforcementRelay: reinforced {} associative synaptic edges (totalDelta={})",
                    signal.associationsFormed(), signal.totalSynapticWeightDelta());
        }

        return true;
    }

    private int parseSlot(String id) {
        if (id == null) return -1;
        int lastDash = id.lastIndexOf('-');
        if (lastDash >= 0 && lastDash < id.length() - 1) {
            try {
                return Integer.parseInt(id.substring(lastDash + 1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public String relayName() {
        return "hebbian_synaptic_reinforcement";
    }
}
