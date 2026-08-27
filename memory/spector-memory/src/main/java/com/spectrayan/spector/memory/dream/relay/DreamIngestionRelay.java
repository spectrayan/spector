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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 6 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Dream-to-Memory Consolidation Gate</h3>
 * <p>Determines which surviving dream scenes are persisted into episodic memory.</p>
 *
 * @since 1.4.0
 */
public final class DreamIngestionRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamIngestionRelay.class);

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null) return true;

        int ingested = 0;
        for (DreamSignal.DreamScene scene : signal.survivingScenes()) {
            if (scene.qualityScore() >= signal.config().persistenceThreshold()) {
                log.info("Dream insight eligible for ingestion: [{}] {}", scene.id(), scene.insightText());
                ingested++;
            }
        }

        log.info("DreamIngestionRelay: Hebbian inhibition would be applied to {} failed pairs", signal.failedPairs());

        return true;
    }

    @Override
    public String relayName() {
        return "dream_ingestion";
    }
}
