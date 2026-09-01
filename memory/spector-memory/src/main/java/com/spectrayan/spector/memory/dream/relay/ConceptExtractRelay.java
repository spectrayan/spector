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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;

/**
 * Stage 10 relay in {@link com.spectrayan.spector.memory.pathway.DreamPathway}.
 *
 * <h3>Biological Analog: Persist the Residue, Discard the Scaffold (Insight Extraction)</h3>
 * <p>Distills clean ExtractedInsight records from surviving scenes.</p>
 *
 * @since 1.4.0
 */
public final class ConceptExtractRelay implements SynapticRelay<DreamSignal> {

    public static final float DEFAULT_FREE_ENERGY_SCORE = 0.50f;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.survivingScenes().isEmpty()) {
            return true;
        }

        for (DreamSignal.DreamScene scene : signal.survivingScenes()) {
            ExtractedInsight insight = new ExtractedInsight(
                    signal.nextId(),
                    scene.insightText(),
                    scene.embedding(),
                    ExtractedInsight.InsightType.SEMANTIC,
                    scene.sourceIds(),
                    scene.qualityScore(),
                    DEFAULT_FREE_ENERGY_SCORE
            );
            signal.addExtractedInsight(insight);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "concept_extract";
    }
}
