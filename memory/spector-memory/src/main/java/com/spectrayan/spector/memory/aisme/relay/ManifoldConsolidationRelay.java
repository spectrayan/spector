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
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.manifold.ManifoldConsolidator;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Synaptic relay executing Riemannian cognitive manifold consolidation during sleep reflection.
 *
 * <h3>Biological Analog: Sleep Consolidation of Cognitive Map Geometry</h3>
 * <p>During sleep replay (reflect pathway), associative Hebbian co-activations and temporal links
 * warp the Riemannian metric tensor, updating coordinate precision and low-rank cross-coupling.</p>
 */
public final class ManifoldConsolidationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ManifoldConsolidationRelay.class);

    private final CognitiveManifold manifold;
    private final ManifoldConsolidator consolidator;
    private final Supplier<List<float[]>> coActivationSupplier;

    /**
     * Constructs a new ManifoldConsolidationRelay with no explicit supplier.
     *
     * @param manifold the cognitive manifold to consolidate (nullable)
     * @param consolidator the consolidator engine (nullable)
     */
    public ManifoldConsolidationRelay(CognitiveManifold manifold, ManifoldConsolidator consolidator) {
        this(manifold, consolidator, null);
    }

    /**
     * Constructs a new ManifoldConsolidationRelay with a co-activation pair supplier.
     *
     * @param manifold the cognitive manifold to consolidate (nullable)
     * @param consolidator the consolidator engine (nullable)
     * @param coActivationSupplier supplier for co-activated memory difference vectors
     */
    public ManifoldConsolidationRelay(CognitiveManifold manifold, ManifoldConsolidator consolidator, Supplier<List<float[]>> coActivationSupplier) {
        this.manifold = manifold;
        this.consolidator = consolidator != null ? consolidator : new ManifoldConsolidator();
        this.coActivationSupplier = coActivationSupplier;
    }

    @Override
    public String relayName() {
        return RelayNames.MANIFOLD_CONSOLIDATION;
    }

    @Override
    public boolean transmit(ReflectSignal signal) {
        if (manifold == null) {
            return true;
        }

        try {
            PersonalMetricTensor current = manifold.currentTensor();
            if (current != null) {
                List<float[]> pairs = (coActivationSupplier != null) ? coActivationSupplier.get() : List.of();
                if (pairs != null && !pairs.isEmpty()) {
                    PersonalMetricTensor updated = consolidator.consolidate(current, pairs, consolidator.defaultLearningRate());
                    manifold.updateTensor(updated);
                    log.debug("Manifold metric tensor consolidated to version {}", updated.version());
                }
            }
        } catch (final RuntimeException e) {
            log.warn("Failed to consolidate cognitive manifold metric tensor during reflection: {}", e.getMessage());
        }

        return true;
    }
}
