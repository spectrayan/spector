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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.graph.hebbian.SynapticDecayModulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synaptic Homeostasis Relay.
 *
 * <p>Applies arousal-modulated homeostatic decay to Hebbian co-activation graph edges,
 * preventing synaptic saturation while preserving frequently co-activated associations.</p>
 */
public final class HebbianHomeostasisRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(HebbianHomeostasisRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.hebbianGraph() == null) {
            return true;
        }

        try {
            if (signal.partitionManager() != null && signal.index() != null) {
                signal.hebbianGraph().setDecayModulator(
                        new SynapticDecayModulator(signal.partitionManager(), signal.index(), signal.hebbianGraph().capacity()));
            }

            int decayed = signal.hebbianGraph().decayEdges(
                    SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_DECAY_FACTOR,
                    signal.graphMetrics()
            );
            signal.hebbianGraph().setDecayModulator(null);
            log.debug("Hebbian Homeostasis: decayed {} edges", decayed);
        } catch (Exception e) {
            log.warn("Hebbian homeostasis decay failed: {}", e.getMessage(), e);
        }
        return true;
    }
}
