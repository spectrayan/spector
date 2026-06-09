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

import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.index.MemoryIndex;

import java.lang.foreign.Arena;
import java.time.Instant;

/**
 * Ephemeral point-in-time memory state reconstructed from WAL events.
 *
 * <h3>Biological Analog: Hippocampal Replay</h3>
 * <p>During sleep, the hippocampus replays past experiences to consolidate them.
 * {@code ReplaySnapshot} is the programmatic equivalent — it reconstructs the
 * memory landscape as it existed at a specific moment in time by replaying
 * the WAL event log.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>The snapshot holds an ephemeral {@link Arena} with off-heap segments
 * for the reconstructed tier stores. It must be closed after use to release
 * off-heap memory:</p>
 * <pre>{@code
 *   try (var snapshot = WalReplayer.replay(wal, targetTimestamp, maxEvents, ...)) {
 *       // Use snapshot.index() and snapshot.tierRouter() for recall
 *   }
 * }</pre>
 *
 * @param index           reconstructed MemoryIndex with IDs, text, metadata
 * @param tierRouter      reconstructed TierRouter with ephemeral off-heap segments
 * @param arena           the off-heap Arena owning all replay segments (close to release)
 * @param memoryCount     total memories reconstructed
 * @param eventsProcessed WAL events replayed to build this snapshot
 * @param replayTimestamp the target timestamp this snapshot represents
 */
public record ReplaySnapshot(
        MemoryIndex index,
        TierRouter tierRouter,
        Arena arena,
        int memoryCount,
        int eventsProcessed,
        Instant replayTimestamp
) implements AutoCloseable {

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }
}
