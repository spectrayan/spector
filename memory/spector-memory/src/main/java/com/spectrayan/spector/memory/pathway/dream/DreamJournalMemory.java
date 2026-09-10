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
package com.spectrayan.spector.memory.pathway.dream;

import java.nio.file.Path;
import java.time.Instant;
import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.kernel.api.TriageOutcome;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;

/**
 * Backward compatibility subclass of {@link com.spectrayan.spector.kernel.store.DreamJournalMemory}.
 */
public class DreamJournalMemory extends com.spectrayan.spector.kernel.store.DreamJournalMemory {

    public DreamJournalMemory(Path filePath, int capacity, int maxTextBytes) throws Exception {
        super(filePath, capacity, maxTextBytes);
    }

    public static DreamJournalMemory heap(int capacity, int maxTextBytes) {
        try {
            return new DreamJournalMemory(null, capacity, maxTextBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate in-memory DreamJournalMemory: " + e.getMessage(), e);
        }
    }

    public void appendScene(DreamSignal.DreamScene scene) {
        if (scene == null) return;
        append(new DreamJournalEntry(
                scene.id(),
                Instant.now(),
                DreamMode.REM,
                scene.triageOutcome() != null ? scene.triageOutcome() : TriageOutcome.NOISE,
                scene.qualityScore(),
                scene.narrative(),
                scene.insightText(),
                scene.sourceIds()
        ));
    }
}
