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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.memory.pathway.reflect.spi.local.FileReflectCheckpointStore;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.InMemoryReflectCheckpointStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReflectCheckpoint: Persistence & Telemetry Tests")
class ReflectCheckpointTest {

    @Test
    @DisplayName("Initial checkpoint creates clean state")
    void testInitialCheckpoint() {
        ReflectCheckpoint cp = ReflectCheckpoint.initial("sweep-alpha");
        assertThat(cp.sweepId()).isEqualTo("sweep-alpha");
        assertThat(cp.lastCompletedSessionId()).isEqualTo(0L);
        assertThat(cp.sessionsCompleted()).isEqualTo(0);
        assertThat(cp.status()).isEqualTo(ReflectSweepStatus.IDLE);
    }

    @Test
    @DisplayName("ReflectSweepProgress converts correctly from checkpoint")
    void testProgressConversion() {
        Instant startedAt = Instant.now();
        ReflectCheckpoint cp = new ReflectCheckpoint(
                "sweep-1",
                0,
                12345L,
                999L,
                10,
                25,
                40,
                5,
                Instant.now(),
                ReflectSweepStatus.RUNNING
        );

        ReflectSweepProgress progress = ReflectSweepProgress.from(cp, startedAt);
        assertThat(progress.sweepId()).isEqualTo("sweep-1");
        assertThat(progress.sessionsCompleted()).isEqualTo(10);
        assertThat(progress.factsIngested()).isEqualTo(25);
        assertThat(progress.turnsMarked()).isEqualTo(40);
        assertThat(progress.backlogRemaining()).isEqualTo(5);
        assertThat(progress.status()).isEqualTo(ReflectSweepStatus.RUNNING);
        assertThat(progress.startedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("InMemoryReflectCheckpointStore saves, loads, and deletes checkpoints")
    void testInMemoryStore() {
        InMemoryReflectCheckpointStore store = new InMemoryReflectCheckpointStore();
        ReflectCheckpoint cp = new ReflectCheckpoint(
                "sweep-2",
                1,
                555L,
                100L,
                2,
                4,
                8,
                0,
                Instant.now(),
                ReflectSweepStatus.COMPLETE
        );

        assertThat(store.load("sweep-2")).isEmpty();
        store.save(cp);
        Optional<ReflectCheckpoint> loaded = store.load("sweep-2");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().lastCompletedSessionId()).isEqualTo(555L);
        assertThat(loaded.get().status()).isEqualTo(ReflectSweepStatus.COMPLETE);

        store.delete("sweep-2");
        assertThat(store.load("sweep-2")).isEmpty();
    }

    @Test
    @DisplayName("FileReflectCheckpointStore atomically persists and reloads checkpoints from disk")
    void testFileStore(@TempDir Path tempDir) {
        FileReflectCheckpointStore store = new FileReflectCheckpointStore(tempDir);
        ReflectCheckpoint cp = new ReflectCheckpoint(
                "sweep-disk",
                0,
                888L,
                2048L,
                7,
                14,
                21,
                12,
                Instant.now(),
                ReflectSweepStatus.RUNNING
        );

        assertThat(store.load("sweep-disk")).isEmpty();
        store.save(cp);

        // Load back through another store instance to verify true disk persistence
        FileReflectCheckpointStore reloadedStore = new FileReflectCheckpointStore(tempDir);
        Optional<ReflectCheckpoint> loaded = reloadedStore.load("sweep-disk");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().sweepId()).isEqualTo("sweep-disk");
        assertThat(loaded.get().lastCompletedSessionId()).isEqualTo(888L);
        assertThat(loaded.get().lastCompletedTurnOffset()).isEqualTo(2048L);
        assertThat(loaded.get().sessionsCompleted()).isEqualTo(7);
        assertThat(loaded.get().factsIngested()).isEqualTo(14);
        assertThat(loaded.get().turnsMarked()).isEqualTo(21);
        assertThat(loaded.get().backlogRemaining()).isEqualTo(12);
        assertThat(loaded.get().status()).isEqualTo(ReflectSweepStatus.RUNNING);

        store.delete("sweep-disk");
        assertThat(reloadedStore.load("sweep-disk")).isEmpty();
    }

    @Test
    @DisplayName("FileReflectCheckpointStore guards against path traversal in sweepId")
    void testFileStorePathTraversal(@TempDir Path tempDir) {
        FileReflectCheckpointStore store = new FileReflectCheckpointStore(tempDir);

        String maliciousSweepId = "../../../etc/passwd";
        ReflectCheckpoint cp = new ReflectCheckpoint(
                maliciousSweepId,
                0,
                100L,
                200L,
                1,
                2,
                3,
                4,
                Instant.now(),
                ReflectSweepStatus.COMPLETE
        );

        // Save sanitizes and persists strictly within tempDir
        store.save(cp);

        // Verify no file was created outside tempDir
        assertThat(tempDir.resolve("../passwd")).doesNotExist();

        // Load works safely through sanitized path
        Optional<ReflectCheckpoint> loaded = store.load(maliciousSweepId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().lastCompletedSessionId()).isEqualTo(100L);

        // Delete cleans up safely
        store.delete(maliciousSweepId);
        assertThat(store.load(maliciousSweepId)).isEmpty();

        // Blank/null sweepId handled gracefully
        assertThat(store.load(null)).isEmpty();
        assertThat(store.load("")).isEmpty();
        assertThat(store.load("   ")).isEmpty();
    }
}
