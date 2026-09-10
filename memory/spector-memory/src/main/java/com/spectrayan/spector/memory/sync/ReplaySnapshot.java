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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;

import java.time.Instant;

/**
 * Ephemeral point-in-time memory state reconstructed from WAL events (R9.1, R9.3).
 *
 * @param index           reconstructed MemoryIndex with IDs, text, metadata
 * @param cognitiveRouter reconstructed CognitiveMemoryRouter with ephemeral segments (nullable)
 * @param releaseHandle   closeable handle releasing any resources upon snapshot completion
 * @param memoryCount     total memories reconstructed
 * @param eventsProcessed WAL events replayed to build this snapshot
 * @param replayTimestamp the target timestamp this snapshot represents
 */
public record ReplaySnapshot(
        MemoryIndex index,
        CognitiveMemoryRouter cognitiveRouter,
        AutoCloseable releaseHandle,
        int memoryCount,
        int eventsProcessed,
        Instant replayTimestamp
) implements AutoCloseable {

    @Override
    public void close() {
        if (releaseHandle != null) {
            try {
                releaseHandle.close();
            } catch (Exception ignored) {
            }
        }
    }
}
