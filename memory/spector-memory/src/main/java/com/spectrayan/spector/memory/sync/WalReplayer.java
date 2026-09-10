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

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.persist.DataEncryptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs point-in-time memory state from WAL events (R9.1, R9.3).
 */
public final class WalReplayer {

    private static final Logger log = LoggerFactory.getLogger(WalReplayer.class);

    private WalReplayer() {} // utility class

    public static ReplaySnapshot replay(MemoryWal wal, Instant targetTimestamp,
                                         int maxEvents, int quantizedVecBytes) {
        return replay(wal, targetTimestamp, maxEvents, quantizedVecBytes, DataEncryptor.NOOP);
    }

    public static ReplaySnapshot replay(MemoryWal wal, Instant targetTimestamp,
                                         int maxEvents, int quantizedVecBytes,
                                         DataEncryptor encryptor) {

        log.info("WAL replay starting: target={}, maxEvents={}, vecBytes={}",
                targetTimestamp, maxEvents, quantizedVecBytes);

        List<WalEvent> allEvents = wal.replay(0);
        List<WalEvent> filtered = allEvents.stream()
                .filter(e -> !e.timestamp().isAfter(targetTimestamp))
                .limit(maxEvents)
                .toList();

        log.info("WAL replay: {} events match target (of {} total)",
                filtered.size(), allEvents.size());

        MemoryIndex index = new MemoryIndex();
        Map<String, ReplayEntry> liveMemories = new HashMap<>();
        int rememberCount = 0;

        for (WalEvent event : filtered) {
            switch (event.type()) {
                case REMEMBER -> {
                    byte[] payload = encryptor.decryptPayload(event.payload());
                    liveMemories.put(event.memoryId(), new ReplayEntry(
                            event.memoryId(),
                            payload,
                            event.timestamp()
                    ));
                    rememberCount++;
                }
                case FORGET -> {
                    liveMemories.remove(event.memoryId());
                }
                case REINFORCE -> {
                    ReplayEntry entry = liveMemories.get(event.memoryId());
                    if (entry != null && event.payload().length >= 1) {
                        entry.valence = event.payload()[0];
                    }
                }
                default -> {}
            }
        }

        int liveCount = liveMemories.size();
        log.info("WAL replay: {} live memories after processing ({} remembered, {} forgotten)",
                liveCount, rememberCount, rememberCount - liveCount);

        long stride = 64L + quantizedVecBytes;
        int slot = 0;
        for (ReplayEntry entry : liveMemories.values()) {
            long offset = (long) slot * stride;
            MemoryLocation location = new MemoryLocation(MemoryType.SEMANTIC, offset, -1);
            index.register(entry.id, location, "", MemorySource.OBSERVED, new String[0]);
            slot++;
        }

        log.info("WAL replay complete: {} memories reconstructed at {}",
                liveCount, targetTimestamp);

        return new ReplaySnapshot(index, null, null, liveCount, filtered.size(), targetTimestamp);
    }

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
