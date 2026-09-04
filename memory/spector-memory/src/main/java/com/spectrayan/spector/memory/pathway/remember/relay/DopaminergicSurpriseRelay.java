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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.neuromod.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.Objects;

/**
 * Evaluates dopaminergic novelty and surprise to compute intrinsic memory importance and flashbulb pinning.
 */
public final class DopaminergicSurpriseRelay implements SynapticRelay<RememberSignal> {

    private final SurpriseDetector surpriseDetector;
    private final ImportanceProvider importanceProvider;
    private final WorkingMemory workingStore;
    private final ScalarQuantizer quantizer;

    public DopaminergicSurpriseRelay(
            final SurpriseDetector surpriseDetector,
            final ImportanceProvider importanceProvider,
            final WorkingMemory workingStore,
            final ScalarQuantizer quantizer) {
        this.surpriseDetector = Objects.requireNonNull(surpriseDetector, "surpriseDetector cannot be null");
        this.importanceProvider = importanceProvider != null ? importanceProvider : ImportanceProvider.baseline();
        this.workingStore = workingStore;
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer cannot be null");
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal.header() != null) {
            signal.importance(signal.header().importance());
            signal.flashbulb((signal.header().flags() & com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.FLAG_PINNED) != 0);
            return true;
        }
        final float[] vector = signal.vector();
        final float nearestDist;
        if (workingStore != null && workingStore.count() > 0 && vector != null) {
            nearestDist = workingStore.nearestDistance(
                    vector, quantizer.mins(), quantizer.scales());
        } else if (vector != null) {
            nearestDist = VectorOps.magnitude(vector);
        } else {
            nearestDist = 0.0f;
        }
        signal.nearestDist(nearestDist);

        final ImportanceContext importanceCtx = new ImportanceContext(
                signal.text(),
                vector,
                signal.hints(),
                signal.salienceProfile(),
                signal.type(),
                nearestDist,
                surpriseDetector.stats().zScore(nearestDist),
                false,
                signal.soulContexts());

        final ImportanceResult importanceResult = importanceProvider.score(importanceCtx);
        signal.importance(importanceResult.importance());
        signal.flashbulb(importanceResult.isFlashbulb());

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.DOPAMINERGIC_SURPRISE;
    }
}
