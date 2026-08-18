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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.synapse.TemperatureSoftmax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Relay that applies Softmax temperature modulation to candidate scores.
 */
public final class TemperatureSoftmaxRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(TemperatureSoftmaxRelay.class);

    private final SurpriseDetector surpriseDetector;
    private final PartitionRegistry partitionRegistry;
    private final float[] calibrationMins;
    private final float[] calibrationScales;

    public TemperatureSoftmaxRelay(final SurpriseDetector surpriseDetector,
                                   final PartitionRegistry partitionRegistry,
                                   final float[] calibrationMins,
                                   final float[] calibrationScales) {
        this.surpriseDetector = surpriseDetector;
        this.partitionRegistry = partitionRegistry;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        final RecallOptions options = signal.options();
        List<CognitiveResult> allResults = signal.candidates();

        final float effectiveTemp = computeEffectiveTemperature(signal.queryVector(), options);
        signal.setEffectiveTemperature(effectiveTemp);
        if (Math.abs(effectiveTemp - 1.0f) >= 1e-4f) {
            TemperatureSoftmax.applySoftmaxTemperature(allResults, effectiveTemp);
            allResults.sort(Comparator.comparing(CognitiveResult::score).reversed());
            if (allResults.size() > options.topK()) {
                allResults = new ArrayList<>(allResults.subList(0, options.topK()));
                signal.setCandidates(allResults);
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.TEMPERATURE;
    }

    private float computeEffectiveTemperature(final float[] queryVector, final RecallOptions options) {
        if (!options.adaptiveTemperature()) {
            return options.computeEffectiveTemperature(0.0);
        }
        double zSurprise = 0.0;
        if (surpriseDetector != null && queryVector != null && partitionRegistry != null) {
            try {
                final var activeRouter = partitionRegistry.activeRouter();
                if (activeRouter != null && activeRouter.working() != null) {
                    final float nearestDist = activeRouter.working().nearestDistance(
                            queryVector, calibrationMins, calibrationScales);
                    if (nearestDist != Float.MAX_VALUE) {
                        zSurprise = surpriseDetector.querySurpriseZScore(nearestDist);
                    }
                }
            } catch (final RuntimeException e) {
                log.debug("Failed to compute query surprise z-score: {}", e.getMessage());
            }
        }
        return options.computeEffectiveTemperature(zSurprise);
    }
}
