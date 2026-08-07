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
package com.spectrayan.spector.memory.insula;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.memory.kernel.bundle.RegionSizeSpec;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsularCortexTest {

    private static final byte[] TEST_JSON = """
            {
              "soul": { "name": "forge" },
              "salience": { "icnu_weights": { "interest": 0.5 } }
            }
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] SECOND_JSON = """
            {
              "soul": { "name": "forge-updated" }
            }
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void heapFactoryBehavesCorrectly() {
        try (InsularCortex insula = InsularCortex.heap()) {
            assertThat(insula.isPresent()).isFalse();
            assertThat(insula.size()).isEqualTo(0);
            assertThat(insula.capacity()).isEqualTo(1);
            assertThat(insula.version()).isEqualTo(0);
            assertThat(insula.updatedAt()).isEqualTo(0L);
            assertThat(insula.shape()).isEqualTo(MemoryShape.INSULAR);
            assertThat(insula.layout().layoutId()).isEqualTo(InsularLayout.LAYOUT_ID);

            // Put first self-model
            long startMs = System.currentTimeMillis();
            int v1 = insula.put(TEST_JSON);
            long endMs = System.currentTimeMillis();

            assertThat(v1).isEqualTo(1);
            assertThat(insula.isPresent()).isTrue();
            assertThat(insula.size()).isEqualTo(1);
            assertThat(insula.version()).isEqualTo(1);
            assertThat(insula.updatedAt()).isBetween(startMs, endMs);

            Optional<byte[]> retrieved = insula.get();
            assertThat(retrieved).isPresent();
            assertThat(new String(retrieved.get(), StandardCharsets.UTF_8)).contains("forge");

            // Put second self-model
            int v2 = insula.put(SECOND_JSON);
            assertThat(v2).isEqualTo(2);
            assertThat(insula.version()).isEqualTo(2);

            Optional<byte[]> retrievedSecond = insula.get();
            assertThat(retrievedSecond).isPresent();
            assertThat(new String(retrievedSecond.get(), StandardCharsets.UTF_8)).contains("forge-updated");

            // Clear self-model
            boolean cleared = insula.clear();
            assertThat(cleared).isTrue();
            assertThat(insula.isPresent()).isFalse();
            assertThat(insula.size()).isEqualTo(0);
            assertThat(insula.version()).isEqualTo(3); // version increments on clear
            assertThat(insula.get()).isEmpty();

            // Clear again
            boolean clearedAgain = insula.clear();
            assertThat(clearedAgain).isFalse();
            assertThat(insula.version()).isEqualTo(3); // no change
        }
    }

    @Test
    void putThrowsWhenPayloadExceedsCapacity() {
        try (InsularCortex insula = InsularCortex.heap()) {
            byte[] hugePayload = new byte[64 * 1024]; // entire segment size
            assertThatThrownBy(() -> insula.put(hugePayload))
                    .isInstanceOf(SpectorMemoryException.class)
                    .hasMessageContaining("exceeds allocated capacity");
        }
    }

    @Test
    void getThrowsWhenChecksumMismatch() {
        try (InsularCortex insula = InsularCortex.heap()) {
            insula.put(TEST_JSON);

            // Manually corrupt one byte of the JSON payload in the segment
            long dataStartOffset = MemoryHeader.HEADER_BYTES + InsularLayout.INSULAR_HEADER_BYTES;
            byte originalByte = insula.segment().get(ValueLayout.JAVA_BYTE, dataStartOffset);
            insula.segment().set(ValueLayout.JAVA_BYTE, dataStartOffset, (byte) (originalByte ^ 0xFF));

            assertThatThrownBy(insula::get)
                    .isInstanceOf(SpectorMemoryException.class)
                    .hasMessageContaining("CRC32C checksum mismatch");
        }
    }

    @Test
    void fromBundleIntegrationWorks(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(
                        RegionId.INSULA,
                        16 * 1024L, // 16KB data bytes
                        1,
                        0,
                        InsularLayout.LAYOUT_ID,
                        InsularLayout.SCHEMA_VERSION,
                        false
                )
        );

        // 1. Create bundle and initialize InsularCortex
        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            MemorySegment slice = bundle.regionSegment(RegionId.INSULA);
            try (InsularCortex insula = InsularCortex.fromBundle(bundle.arena(), slice, true)) {
                assertThat(insula.isPresent()).isFalse();
                insula.put(TEST_JSON);
                assertThat(insula.isPresent()).isTrue();
            }
        }

        // 2. Reopen bundle and load InsularCortex
        try (RuntimeBundle reopened = RuntimeBundle.Init.open(bundlePath)) {
            MemorySegment slice = reopened.regionSegment(RegionId.INSULA);
            try (InsularCortex insula = InsularCortex.fromBundle(reopened.arena(), slice, false)) {
                assertThat(insula.isPresent()).isTrue();
                assertThat(insula.version()).isEqualTo(1);
                Optional<byte[]> retrieved = insula.get();
                assertThat(retrieved).isPresent();
                assertThat(new String(retrieved.get(), StandardCharsets.UTF_8)).contains("forge");
            }
        }
    }
}
