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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PartitionRollReplicationTrigger enforcing throw on null/missing bundle path (G9)
 * and verified snapshot trigger calculation.
 */
@DisplayName("Task 2.2: PartitionRollReplicationTrigger Tests")
class PartitionRollReplicationTriggerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("G9: onPartitionRolled throws IllegalStateException when bundlePath is null or missing")
    void testThrowsOnMissingBundlePath() {
        PartitionRollReplicationTrigger trigger = new PartitionRollReplicationTrigger(entry -> {});

        assertThatThrownBy(() -> trigger.onPartitionRolled(1, tempDir, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot trigger snapshot for rolled partition seq=1: bundle path is null or not a regular file");

        Path nonExistent = tempDir.resolve("non_existent.bundle");
        assertThatThrownBy(() -> trigger.onPartitionRolled(2, tempDir, nonExistent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot trigger snapshot for rolled partition seq=2: bundle path is null or not a regular file");
    }

    @Test
    @DisplayName("G9: onPartitionRolled computes sha256 and passes sealed entry on valid bundle file")
    void testSuccessfulPartitionRollTrigger() throws IOException {
        Path validBundle = tempDir.resolve("partition-10.bundle");
        Files.writeString(validBundle, "valid bundle content for checksum calculation");

        AtomicReference<SnapshotManifest.SealedPartitionEntry> captured = new AtomicReference<>();
        PartitionRollReplicationTrigger trigger = new PartitionRollReplicationTrigger(captured::set);

        trigger.onPartitionRolled(10, tempDir, validBundle);

        assertThat(trigger.rollTriggerCount()).isEqualTo(1L);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().id()).isEqualTo("partition-10.bundle");
        assertThat(captured.get().sha256()).isEqualTo(SnapshotVerifier.calculateSha256(validBundle));
    }
}
