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
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default Mode Network (DMN) relay that evaluates global associative resonance across the
 * off-heap {@link DistributedMemoryTensor} during idle mind-wandering.
 *
 * <h3>Biological Analog: Wide-Field Neocortical Holographic Resonance</h3>
 * <p>Probes the global holographic memory energy landscape to identify high-density associative
 * clusters and compute prior epistemic grounding across the agent's lifetime memory pool.</p>
 *
 * @since 1.3.0
 */
public final class RffMindWanderingRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(RffMindWanderingRelay.class);

    private final DistributedMemoryTensor memoryTensor;

    /**
     * Constructs an RffMindWanderingRelay with the specified distributed holographic tensor.
     *
     * @param memoryTensor the off-heap holographic accumulator (nullable)
     */
    public RffMindWanderingRelay(DistributedMemoryTensor memoryTensor) {
        this.memoryTensor = memoryTensor;
    }

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || memoryTensor == null || signal.sampledVectors().isEmpty()) {
            return true;
        }

        List<float[]> sampled = signal.sampledVectors();
        List<String> ids = signal.sampledMemoryIds();
        float beta = signal.hopfieldTemperature();

        int evaluated = 0;
        for (int i = 0; i < sampled.size(); i++) {
            float[] vec = sampled.get(i);
            if (vec == null || vec.length != memoryTensor.inputDimension()) {
                continue;
            }

            float energy = memoryTensor.evaluateEnergy(vec, beta);
            evaluated++;

            if (log.isTraceEnabled()) {
                log.trace("RFF Global Resonance for memory {}: energy={}", ids.get(i), energy);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("RffMindWanderingRelay evaluated global holographic energy for {} memories across {} total patterns",
                    evaluated, memoryTensor.patternCount());
        }

        return true;
    }

    @Override
    public String relayName() {
        return "rff_mind_wandering";
    }
}
