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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.lifespan.LifespanRetentionController;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sleep Reflection Relay executing lifespan-adaptive forgetting and capacity-driven synaptic pruning.
 *
 * <p>Dynamically computes the retention threshold \(\tau(t)\) as a function of the agent's
 * cumulative operational lifespan epochs \(t\) and current episodic volume pressure \(V(t) / V_{\text{target}}\).</p>
 */
public final class LifespanAdaptivePruningRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(LifespanAdaptivePruningRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal == null) {
            return true;
        }

        LifespanRetentionController controller = signal.lifespanController();
        if (controller == null) {
            log.debug("LifespanRetentionController not present on ReflectSignal; skipping lifespan pruning.");
            return true;
        }

        long epoch = controller.advanceEpoch();

        if (signal.partitionManager() == null) {
            float tau = controller.currentTau();
            signal.setEffectiveLifespanTau(tau);
            log.info("Advanced lifespan epoch to {} (tau={}) with null partition manager", epoch, tau);
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        long totalVolume = 0L;

        for (var handle : handles) {
            if (handle.router() != null) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    totalVolume += episodicStore.writePosition();
                }
            }
        }

        controller.updateVolume(totalVolume);
        float tau = controller.computeCurrentTau(totalVolume);
        signal.setEffectiveLifespanTau(tau);

        log.info("Lifespan adaptive pruning epoch {}: activeVolume={}, tau={}", epoch, totalVolume, tau);
        return true;
    }
}
