/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.spring.autoconfigure;

import com.spectrayan.spector.memory.SpectorMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SpectorHealthIndicator} testing each readiness failure
 * mode independently (Req R2.6, R6.5, R6.6, T5, Tasks 2.5, 2.6, 5.6-5.10).
 */
class SpectorHealthIndicatorTest {

    @Test
    @DisplayName("Task 5.9: V4 map-count arithmetic at ADR §14 figures (hotCap=2000 needs ~4.3k vs V3's ~30k)")
    void testV4MapCountArithmetic() {
        int hotCap = 2000;
        int walSegments = 64;
        int overhead = 256;

        long requiredMaps = SpectorHealthIndicator.computeRequiredMaps(hotCap, walSegments, overhead);
        // (2000 * 2) + 64 + 256 = 4320 maps
        assertThat(requiredMaps).isEqualTo(4320L);

        // Standard 262,144 sysctl default provides ~60x headroom under V4 (~2 maps/ns)
        // whereas under V3 (~15 maps/ns, ~30,000 maps) headroom was much narrower
        assertThat(262144L).isGreaterThan(requiredMaps * 50);

        // Single namespace
        assertThat(SpectorHealthIndicator.computeRequiredMaps(1, 0, 0)).isEqualTo(2L);
        // 500 hotCap (replica cap)
        assertThat(SpectorHealthIndicator.computeRequiredMaps(500, 64, 256)).isEqualTo(1320L);
    }

    @Test
    @DisplayName("Task 2.5 & 2.6: Readiness FAILS when data volume is unwritable (Failure Mode 1)")
    void testReadinessFailsWhenVolumeUnwritable(@TempDir Path tempDir) {
        Path nonWritableDir = tempDir.resolve("non-writable-dir");
        // Create directory and set non-writable
        try {
            Files.createDirectories(nonWritableDir);
            nonWritableDir.toFile().setWritable(false);
        } catch (Exception ignored) {
        }

        SpectorHealthIndicator indicator = new SpectorHealthIndicator(null)
                .setDataDir(nonWritableDir)
                .setClusterMode(false);

        // Only assert failure if OS actually enforced unwritable flag
        if (!Files.isWritable(nonWritableDir)) {
            Health health = indicator.health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("disk.status", "UNWRITABLE");
            assertThat(health.getDetails().get("disk.error")).isNotNull();
        }
    }

    @Test
    @DisplayName("Task 5.8 & 5.10: Readiness FAILS when vm.max_map_count is insufficient, naming both numbers (Failure Mode 2)")
    void testReadinessFailsWhenMapCountInsufficient(@TempDir Path tempDir) throws IOException {
        Path sysctlFile = tempDir.resolve("max_map_count");
        // Host has only 1000 max maps
        Files.writeString(sysctlFile, "1000\n");

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);

        SpectorHealthIndicator indicator = new SpectorHealthIndicator(null)
                .setDataDir(dataDir)
                .setHotCap(2000)
                .setWalSegments(64)
                .setMapCountOverhead(256)
                .setSysctlMaxMapCountPath(sysctlFile)
                .setClusterMode(false);

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("map_count.status", "INSUFFICIENT");
        assertThat(health.getDetails()).containsEntry("map_count.actual", 1000L);
        assertThat(health.getDetails()).containsEntry("map_count.required", 4320L);

        // Warning message MUST name both actual map count (1000) and hotCap (2000)
        String errorMsg = (String) health.getDetails().get("map_count.error");
        assertThat(errorMsg)
                .contains("1000")
                .contains("2000")
                .contains("4320");
    }

    @Test
    @DisplayName("Task 2.5 & 2.6: Readiness FAILS when cluster ring is not loaded (Failure Mode 3)")
    void testReadinessFailsWhenRingNotLoaded(@TempDir Path tempDir) throws IOException {
        Path sysctlFile = tempDir.resolve("max_map_count");
        Files.writeString(sysctlFile, "262144\n");

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);

        SpectorHealthIndicator indicator = new SpectorHealthIndicator(null)
                .setDataDir(dataDir)
                .setHotCap(2000)
                .setSysctlMaxMapCountPath(sysctlFile)
                .setClusterMode(true)
                .setRingLoadedSupplier(() -> false); // Ring NOT loaded

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("ring.status", "NOT_LOADED");
        assertThat(health.getDetails().get("ring.error")).isNotNull();
    }

    @Test
    @DisplayName("Readiness PASSES (UP) when disk writable, map_count sufficient, and ring loaded")
    void testReadinessPassesWhenAllHealthy(@TempDir Path tempDir) throws IOException {
        Path sysctlFile = tempDir.resolve("max_map_count");
        Files.writeString(sysctlFile, "262144\n");

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);

        SpectorMemory mockMemory = Mockito.mock(SpectorMemory.class);
        Mockito.when(mockMemory.totalMemories()).thenReturn(42);

        SpectorHealthIndicator indicator = new SpectorHealthIndicator(mockMemory)
                .setDataDir(dataDir)
                .setHotCap(2000)
                .setSysctlMaxMapCountPath(sysctlFile)
                .setClusterMode(true)
                .setRingLoadedSupplier(() -> true); // Ring IS loaded

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("disk.status", "OK");
        assertThat(health.getDetails()).containsEntry("map_count.status", "OK");
        assertThat(health.getDetails()).containsEntry("ring.status", "OK");
        assertThat(health.getDetails()).containsEntry("memory.total", 42);
        assertThat(health.getDetails()).containsKey("simd");
    }

    @Test
    @DisplayName("Task 5.6: Filesystem check validates supported types (XFS / ext4)")
    void testSupportedFilesystemCheck() {
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("xfs")).isTrue();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("ext4")).isTrue();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("EXT4")).isTrue();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("XFS")).isTrue();

        assertThat(SpectorHealthIndicator.isSupportedFilesystem("btrfs")).isFalse();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("apfs")).isFalse();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem("nfs")).isFalse();
        assertThat(SpectorHealthIndicator.isSupportedFilesystem(null)).isFalse();
    }

    @Test
    @DisplayName("Standalone mode passes without ring requirement")
    void testStandaloneModePassesWithoutRing(@TempDir Path tempDir) throws IOException {
        Path sysctlFile = tempDir.resolve("max_map_count");
        Files.writeString(sysctlFile, "262144\n");

        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);

        SpectorHealthIndicator indicator = new SpectorHealthIndicator(null)
                .setDataDir(dataDir)
                .setHotCap(2000)
                .setSysctlMaxMapCountPath(sysctlFile)
                .setClusterMode(false);

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("ring.status", "STANDALONE");
    }
}
