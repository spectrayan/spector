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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.neuromod.dopamine.NoveltyReference;
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
    private final CognitiveMemoryRouter router;

    public DopaminergicSurpriseRelay(
            final SurpriseDetector surpriseDetector,
            final ImportanceProvider importanceProvider,
            final WorkingMemory workingStore,
            final ScalarQuantizer quantizer) {
        this(surpriseDetector, importanceProvider, workingStore, quantizer, null);
    }

    public DopaminergicSurpriseRelay(
            final SurpriseDetector surpriseDetector,
            final ImportanceProvider importanceProvider,
            final WorkingMemory workingStore,
            final ScalarQuantizer quantizer,
            final CognitiveMemoryRouter router) {
        this.surpriseDetector = Objects.requireNonNull(surpriseDetector, "surpriseDetector cannot be null");
        this.importanceProvider = importanceProvider != null ? importanceProvider : ImportanceProvider.baseline();
        this.workingStore = workingStore;
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer cannot be null");
        this.router = router;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        if (signal.header() != null) {
            signal.importance(signal.header().importance());
            signal.flashbulb((signal.header().flags() & com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.FLAG_PINNED) != 0);
            return true;
        }
        final float[] vector = signal.vector();
        final float nearestDist = nearestDistance(vector);
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
        if (vector != null && vector.length > 0) {
            importanceProvider.observeIngested(vector);
        }

        return true;
    }

    private float nearestDistance(float[] vector) {
        if (vector == null || vector.length == 0) {
            return 0.0f;
        }
        float slab = router != null
                ? NoveltyReference.nearest(router, vector, quantizer.mins(), quantizer.scales())
                : workingOnly(vector);
        float ingested = importanceProvider.nearestIngested(vector);
        float best = slab < ingested ? slab : ingested;
        return best;
    }

    private float workingOnly(float[] vector) {
        if (workingStore == null || workingStore.visibleCount() <= 0) {
            return NoveltyReference.EMPTY;
        }
        float dist = workingStore.nearestDistance(vector, quantizer.mins(), quantizer.scales());
        if (!Float.isFinite(dist) || dist >= Float.MAX_VALUE / 2f) {
            return NoveltyReference.EMPTY;
        }
        return dist;
    }

    @Override
    public String relayName() {
        return RelayNames.DOPAMINERGIC_SURPRISE;
    }
}
