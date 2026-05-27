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
        RECALL_HIT
    }
}
