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

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs point-in-time memory state from WAL events.
 *
 * <h3>Biological Analog: Hippocampal Time Travel</h3>
 * <p>Neurons in the hippocampus can "replay" past experiences during sleep,
 * reconstructing the neural landscape as it existed at a specific moment.
 * {@code WalReplayer} does the same for Spector memories — replaying WAL
 * events to build an ephemeral snapshot of historical state.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Events are replayed in chronological order up to the target timestamp</li>
 *   <li>REMEMBER events create entries (text + quantized vector in payload)</li>
 *   <li>FORGET events remove entries (tombstone semantics)</li>
 *   <li>REINFORCE events update valence on the header</li>
 *   <li>The result is an ephemeral {@link ReplaySnapshot} with its own Arena</li>
 *   <li>A safety cap ({@code maxEvents}) prevents OOM on very large WALs</li>
 * </ul>
 *
 * <h3>Memory Layout</h3>
 * <p>The replayer allocates a contiguous off-heap segment sized for
 * {@code maxEvents * stride} bytes. Each REMEMBER event writes its header
 * and quantized vector into the next slot. FORGET events mark slots as
 * tombstoned (flag bit 0). The resulting segment can be scanned by the
 * standard {@link com.spectrayan.spector.memory.synapse.CognitiveScorer}.</p>
 */
public final class WalReplayer {

    private static final Logger log = LoggerFactory.getLogger(WalReplayer.class);

    private WalReplayer() {} // utility class

    /**
     * Replays WAL events to reconstruct memory state at a point in time.
     *
     * @param wal             the WAL to replay from
     * @param targetTimestamp reconstruct state as of this instant
     * @param maxEvents       safety cap on events to process
     * @param quantizedVecBytes quantized vector size in bytes (must match original)
     * @return an ephemeral {@link ReplaySnapshot} — caller must close it
     */
    public static ReplaySnapshot replay(MemoryWal wal, Instant targetTimestamp,
                                         int maxEvents, int quantizedVecBytes) {

        log.info("WAL replay starting: target={}, maxEvents={}, vecBytes={}",
                targetTimestamp, maxEvents, quantizedVecBytes);

        // Step 1: Collect relevant events up to the target timestamp
        List<WalEvent> allEvents = wal.replay(0);
        List<WalEvent> filtered = allEvents.stream()
                .filter(e -> !e.timestamp().isAfter(targetTimestamp))
                .limit(maxEvents)
                .toList();

        log.info("WAL replay: {} events match target (of {} total)",
                filtered.size(), allEvents.size());

        // Step 2: Build ephemeral index by processing events in order
        MemoryIndex index = new MemoryIndex();

        // Track which memories have been ingested (for FORGET processing)
        // Maps memoryId → (text, payload-bytes, source, tags, type, offset-in-segment)
        Map<String, ReplayEntry> liveMemories = new HashMap<>();
        int rememberCount = 0;

        for (WalEvent event : filtered) {
            switch (event.type()) {
                case REMEMBER -> {
                    // Payload contains the serialized data. For replay, we store
                    // the payload bytes and extract text from the WAL event's ID context.
                    // The REMEMBER payload in Spector WAL is the quantized vector bytes.
                    liveMemories.put(event.memoryId(), new ReplayEntry(
                            event.memoryId(),
                            event.payload(),
                            event.timestamp()
                    ));
                    rememberCount++;
                }
                case FORGET -> {
                    liveMemories.remove(event.memoryId());
                }
                case REINFORCE -> {
                    // Reinforce updates valence — record for header reconstruction
                    ReplayEntry entry = liveMemories.get(event.memoryId());
                    if (entry != null && event.payload().length >= 1) {
                        entry.valence = event.payload()[0];
                    }
                }
                default -> {
                    // TAG_MERGE, RECALL_HIT, REFLECT — informational only for replay
                }
            }
        }

        int liveCount = liveMemories.size();
        log.info("WAL replay: {} live memories after processing ({} remembered, {} forgotten)",
                liveCount, rememberCount, rememberCount - liveCount);

        // Step 3: Allocate ephemeral off-heap segment for the reconstructed tier
        CognitiveRecordLayout layout = new CognitiveRecordLayout(quantizedVecBytes);
        int stride = layout.stride();

        Arena arena = Arena.ofConfined();
        MemorySegment segment;
        if (liveCount > 0) {
            segment = arena.allocate((long) liveCount * stride, 64);
            segment.fill((byte) 0);
        } else {
            segment = MemorySegment.NULL;
        }

        // Step 4: Write each live memory into the segment and register in index
        int slot = 0;
        for (ReplayEntry entry : liveMemories.values()) {
            long offset = (long) slot * stride;

            // Build a CognitiveHeader for this replay entry
            CognitiveRecordLayout.CognitiveHeader header =
                    CognitiveRecordLayout.CognitiveHeader.createWithArousal(
                            entry.timestamp.toEpochMilli(),
                            0L,             // synaptic tags (not stored in WAL payload)
                            0f,             // exact norm (not available in WAL)
                            0.5f,           // importance default for replay
                            (short) 0,      // centroid ID
                            MemoryType.SEMANTIC,
                            entry.valence,
                            (byte) 0        // arousal default
                    );
            layout.writeHeader(segment, offset, header);

            // Write quantized vector from payload (if available and correct size)
            int headerSize = layout.stride() - quantizedVecBytes;
            if (entry.payload != null && entry.payload.length >= quantizedVecBytes) {
                MemorySegment.copy(
                        MemorySegment.ofArray(entry.payload), 0,
                        segment, offset + headerSize,
                        quantizedVecBytes
                );
            }

            // Register in the ephemeral index
            // For replay, all memories go into SEMANTIC tier
            MemoryLocation location = new MemoryLocation(MemoryType.SEMANTIC, offset, -1);
            index.register(entry.id, location, "", MemorySource.OBSERVED, new String[0]);

            slot++;
        }

        log.info("WAL replay complete: {} memories reconstructed at {}",
                liveCount, targetTimestamp);

        return new ReplaySnapshot(index, null, arena, liveCount, filtered.size(), targetTimestamp);
    }

    /**
     * Mutable entry used during WAL replay to accumulate state for a single memory.
     */
    private static final class ReplayEntry {
        final String id;
        final byte[] payload;
        final Instant timestamp;
        byte valence = 0;

        ReplayEntry(String id, byte[] payload, Instant timestamp) {
            this.id = id;
            this.payload = payload;
            this.timestamp = timestamp;
        }
    }
}
