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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stage 5 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Dream Journal / Audit Trail</h3>
 * <p>Logs constructed scenes.</p>
 *
 * @since 1.4.0
 */
public final class DreamJournalRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(DreamJournalRelay.class);
    public static final int MAX_LOG_PREVIEW_LENGTH = 200;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) return true;

        int written = 0;
        boolean journalEnabled = signal.config() != null && signal.config().journalEnabled();
        
        for (DreamSignal.DreamScene scene : signal.constructedScenes()) {
            String narrative = scene.narrative() != null ? scene.narrative() : "";
            if (narrative.length() > MAX_LOG_PREVIEW_LENGTH) {
                narrative = narrative.substring(0, MAX_LOG_PREVIEW_LENGTH) + "...";
            }

            log.info("Dream Journal: Mode={}, Outcome={}, Quality={}, SourceIDs={}, Narrative='{}'",
                signal.mode(), scene.triageOutcome(), scene.qualityScore(), scene.sourceIds(), narrative);
                
            if (journalEnabled && signal.dreamJournalMemory() != null) {
                signal.dreamJournalMemory().appendScene(scene);
            }
            written++;
        }

        if (signal.dreamsGenerated() != null) {
            signal.dreamsGenerated().addAndGet(written);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "dream_journal";
    }
}
