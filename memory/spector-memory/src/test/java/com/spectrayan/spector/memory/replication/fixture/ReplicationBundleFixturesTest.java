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
package com.spectrayan.spector.memory.replication.fixture;

import com.spectrayan.spector.kernel.bundle.PartitionBundle;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the replication bundle fixture factory produces valid bundles and
 * all four corruption variants required for offline verification (ADR-0034 §9.7, §10.2, Req R12.8, Task 0.1).
 */
class ReplicationBundleFixturesTest {

    @Test
    @DisplayName("Generates valid partition and runtime bundles that open successfully")
    void testValidBundles(@TempDir Path tempDir) throws IOException {
        Path partPath = tempDir.resolve("partition.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(partPath);
        assertThat(Files.exists(partPath)).isTrue();
        assertThat(Files.size(partPath)).isGreaterThan(0);

        try (PartitionBundle opened = PartitionBundle.Init.open(partPath)) {
            assertThat(opened.directory().liveRegionCount()).isEqualTo(5);
        }

        Path rtPath = tempDir.resolve("runtime.bundle");
        ReplicationBundleFixtures.createValidRuntimeBundle(rtPath);
        assertThat(Files.exists(rtPath)).isTrue();
        assertThat(Files.size(rtPath)).isGreaterThan(0);

        try (RuntimeBundle opened = RuntimeBundle.Init.open(rtPath)) {
            assertThat(opened.directory().liveRegionCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Produces corruption fixture with bad preamble magic")
    void testCorruptedMagicFixture(@TempDir Path tempDir) throws IOException {
        Path partPath = tempDir.resolve("corrupted-magic.bundle");
        ReplicationBundleFixtures.createCorruptedMagicBundle(partPath);

        try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4).order(java.nio.ByteOrder.nativeOrder());
            ch.read(buf, 0);
            buf.flip();
            assertThat(buf.getInt()).isEqualTo(ReplicationBundleFixtures.BAD_MAGIC);
            assertThat(buf.getInt(0)).isNotEqualTo(RegionPreamble.MAGIC);
        }
    }

    @Test
    @DisplayName("Produces corruption fixture with bad preamble layout ID")
    void testCorruptedLayoutIdFixture(@TempDir Path tempDir) throws IOException {
        Path partPath = tempDir.resolve("corrupted-layout.bundle");
        ReplicationBundleFixtures.createCorruptedLayoutIdBundle(partPath);

        try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4).order(java.nio.ByteOrder.nativeOrder());
            ch.read(buf, 36);
            buf.flip();
            assertThat(buf.getInt()).isEqualTo(ReplicationBundleFixtures.BAD_LAYOUT_ID);
        }
    }

    @Test
    @DisplayName("Produces mismatched checksum fixture")
    void testMismatchedChecksumFixture(@TempDir Path tempDir) {
        Path partPath = tempDir.resolve("partition.bundle");
        ReplicationBundleFixtures.createValidPartitionBundle(partPath);
        String realChecksum = ReplicationBundleFixtures.calculateSha256(partPath);
        String mismatched = ReplicationBundleFixtures.createMismatchedChecksum(realChecksum);

        assertThat(mismatched).isNotEqualTo(realChecksum);
        assertThat(mismatched.length()).isEqualTo(realChecksum.length());
    }
}
