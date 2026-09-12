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
package com.spectrayan.spector.memory.replication;

import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Streams ordered, gap-free WAL tail records beyond snapshot HWM with strict truncation detection
 * (ADR-0034 §10, Req R4.1, R4.2, R4.4, Task 3.6, 3.7, 3.9).
 */
public final class WalTailStreamer {

    private static final Logger log = LoggerFactory.getLogger(WalTailStreamer.class);

    private WalTailStreamer() {}

    /**
     * Extracts an ordered, gap-free slice of WAL events from {@code afterHwm} up to {@code targetHwm}
     * or latest available.
     *
     * @param wal       source MemoryWal
     * @param afterHwm  the follower's acknowledged HWM
     * @param targetHwm upper bound sequence number, or Long.MAX_VALUE for all available
     * @return gap-free ordered list of events
     * @throws WalTruncationLagException if the follower's requested sequence was truncated on the owner
     * @throws WalTailGapException        if a sequence gap is detected within the stream
     */
    public static List<WalEvent> streamTail(MemoryWal wal, long afterHwm, long targetHwm) {
        Objects.requireNonNull(wal, "wal must not be null");

        List<WalEvent> candidateEvents = wal.replay(afterHwm);
        if (candidateEvents.isEmpty()) {
            return List.of();
        }

        // 1. Truncation detection (Req R4.4, Task 3.9):
        // If the first candidate sequence is greater than afterHwm + 1, records were truncated!
        WalEvent firstEvent = candidateEvents.get(0);
        if (firstEvent.sequence() > afterHwm + 1) {
            log.error("[WalTailStreamer] Follower requested sequence {} but oldest available is {} (truncated)",
                    afterHwm, firstEvent.sequence());
            throw new WalTruncationLagException(afterHwm, firstEvent.sequence());
        }

        // 2. Ordered gap-free verification (Req R4.2, Task 3.7)
        List<WalEvent> validated = new ArrayList<>();
        long expectedSeq = afterHwm + 1;

        for (WalEvent event : candidateEvents) {
            if (event.sequence() > targetHwm) {
                break;
            }
            if (event.sequence() != expectedSeq) {
                log.error("[WalTailStreamer] WAL tail sequence gap detected: expected {}, got {}",
                        expectedSeq, event.sequence());
                throw new WalTailGapException(expectedSeq, event.sequence());
            }
            validated.add(event);
            expectedSeq++;
        }

        return List.copyOf(validated);
    }
}
