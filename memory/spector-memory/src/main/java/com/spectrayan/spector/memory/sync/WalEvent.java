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

import java.time.Instant;

/**
 * Immutable event entry for the memory Write-Ahead Log.
 *
 * <p>Every mutation (remember, forget, reinforce) produces a WAL event.
 * These events are the source-of-truth for memory state and enable
 * replay-based replication.</p>
 *
 * @param sequence   monotonically increasing event sequence number
 * @param type       the event type (REMEMBER, FORGET, REINFORCE, REFLECT)
 * @param memoryId   the memory ID this event applies to
 * @param timestamp  when the event occurred
 * @param payload    serialized event data (format depends on type)
 */
public record WalEvent(
        long sequence,
        EventType type,
        String memoryId,
        Instant timestamp,
        byte[] payload
) {

    /**
     * Event types for the write-ahead log.
     */
    public enum EventType {
        /** New memory was stored. */
        REMEMBER,
        /** Memory was tombstoned. */
        FORGET,
        /** Memory valence was reinforced. */
        REINFORCE,
        /** Sleep consolidation promoted/pruned memories. */
        REFLECT,
        /** Synaptic tags were merged. */
        TAG_MERGE,
        /** Memory recall count was incremented. */
        RECALL_HIT,
        
        // Unified Shape Opcodes
        /** RecordMemory write. */
        RECORD_WRITE,
        /** GraphMemory add edge. */
        ADJ_ADD_EDGE,
        /** GraphMemory delete edge. */
        ADJ_DEL_EDGE,
        /** RegistryMemory intern. */
        REGISTRY_INTERN,
        /** AppendMemory append. */
        APPEND,
        /** Checkpoint snapshot mark. */
        SNAPSHOT_MARK,
        /** EntityGraph add node. */
        GRAPH_ADD_NODE,
        /** EntityGraph link entity to memory. */
        GRAPH_LINK_MEMORY,
        /** TemporalChain link. */
        CHAIN_LINK
    }
}
