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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContinuityRecordMemoryTest {

    @Test
    void ephemeralHeapOperationsAndRingBuffer() {
        try (ContinuityRecordMemory memory = ContinuityRecordMemory.heap(5)) {
            assertThat(memory.capacity()).isEqualTo(5);
            assertThat(memory.size()).isZero();
            assertThat(memory.totalSnapshots()).isZero();
            assertThat(memory.latestSnapshot()).isEmpty();

            long baseTime = 1000L;
            for (int i = 1; i <= 8; i++) {
                IdentityTrajectorySnapshot snap = new IdentityTrajectorySnapshot(
                        baseTime + i * 100,
                        0.5f + i * 0.05f,
                        10.0f + i,
                        0.01f * i,
                        (byte) i,
                        (byte) (i * 2),
                        (byte) (100 - i),
                        (short) 1
                );
                memory.appendSnapshot(snap);
            }

            assertThat(memory.totalSnapshots()).isEqualTo(8);
            assertThat(memory.size()).isEqualTo(5); // capped at capacity

            Optional<IdentityTrajectorySnapshot> latest = memory.latestSnapshot();
            assertThat(latest).isPresent();
            assertThat(latest.get().timestamp()).isEqualTo(baseTime + 800);
            assertThat(latest.get().phiCc()).isEqualTo(0.5f + 8 * 0.05f);

            List<IdentityTrajectorySnapshot> history = memory.readHistory(3);
            assertThat(history).hasSize(3);
            assertThat(history.get(0).timestamp()).isEqualTo(baseTime + 800);
            assertThat(history.get(1).timestamp()).isEqualTo(baseTime + 700);
            assertThat(history.get(2).timestamp()).isEqualTo(baseTime + 600);

            assertThat(memory.calculateLongitudinalDrift()).isGreaterThan(0.07f);
        }
    }

    @Test
    void fileBackedPersistenceReload(@TempDir Path tempDir) {
        Path file = tempDir.resolve("continuity.smd");

        try (ContinuityRecordMemory memory = ContinuityRecordMemory.open(file, 100)) {
            IdentityTrajectorySnapshot s1 = new IdentityTrajectorySnapshot(
                    1724350000000L, 0.88f, 15.2f, 0.03f, (byte) 10, (byte) 20, (byte) 90, (short) 2
            );
            IdentityTrajectorySnapshot s2 = new IdentityTrajectorySnapshot(
                    1724350060000L, 0.92f, 15.5f, 0.05f, (byte) 15, (byte) 25, (byte) 85, (short) 2
            );
            memory.appendSnapshot(s1);
            memory.appendSnapshot(s2);
        }

        // Reopen and verify persistence
        try (ContinuityRecordMemory memory = ContinuityRecordMemory.open(file, 100)) {
            assertThat(memory.totalSnapshots()).isEqualTo(2);
            assertThat(memory.size()).isEqualTo(2);

            Optional<IdentityTrajectorySnapshot> latest = memory.latestSnapshot();
            assertThat(latest).isPresent();
            assertThat(latest.get().timestamp()).isEqualTo(1724350060000L);
            assertThat(latest.get().phiCc()).isEqualTo(0.92f);

            List<IdentityTrajectorySnapshot> history = memory.readHistory(10);
            assertThat(history).hasSize(2);
            assertThat(history.get(0).timestamp()).isEqualTo(1724350060000L);
            assertThat(history.get(1).timestamp()).isEqualTo(1724350000000L);
        }
    }
}
