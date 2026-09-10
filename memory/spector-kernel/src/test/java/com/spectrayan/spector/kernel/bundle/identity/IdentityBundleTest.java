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
package com.spectrayan.spector.kernel.bundle.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.commons.error.SpectorMemoryException;

@DisplayName("IdentityBundle Specifications")
class IdentityBundleTest {

    @Nested
    @DisplayName("In-Memory Heap Bundle")
    class HeapBundleTests {

        @Test
        @DisplayName("Initial state is empty across all regions")
        void initialEmpty() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                for (IdentityRegionId region : IdentityRegionId.values()) {
                    assertThat(bundle.isEmpty(region)).isTrue();
                    assertThat(bundle.getVersion(region)).isZero();
                    assertThat(bundle.readRaw(region)).isEmpty();
                }
            }
        }

        @Test
        @DisplayName("Writes and reads raw payload for each region")
        void readWriteRaw() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                for (IdentityRegionId region : IdentityRegionId.values()) {
                    byte[] data = ("payload-for-" + region.name()).getBytes(StandardCharsets.UTF_8);
                    bundle.writeRaw(region, data);

                    assertThat(bundle.isEmpty(region)).isFalse();
                    assertThat(bundle.getVersion(region)).isEqualTo(1);

                    Optional<byte[]> read = bundle.readRaw(region);
                    assertThat(read).isPresent();
                    assertThat(read.get()).isEqualTo(data);
                }
            }
        }

        @Test
        @DisplayName("Clears region and resets state")
        void clearRegion() {
            try (IdentityBundle bundle = IdentityBundle.heap()) {
                byte[] data = "some-data".getBytes(StandardCharsets.UTF_8);
                bundle.writeRaw(IdentityRegionId.CONTINUITY, data);
                assertThat(bundle.readRaw(IdentityRegionId.CONTINUITY)).isPresent();

                bundle.clearRegion(IdentityRegionId.CONTINUITY);
                assertThat(bundle.readRaw(IdentityRegionId.CONTINUITY)).isEmpty();
                assertThat(bundle.isEmpty(IdentityRegionId.CONTINUITY)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("File-Backed Identity Bundle")
    class FileBundleTests {

        @Test
        @DisplayName("Persists data across close and reopen")
        void persistenceAcrossReopen(@TempDir Path tempDir) {
            Path bundlePath = tempDir.resolve("accounts/01/23/0123456789abc/identity.bundle");

            byte[] soulData = "soul-data".getBytes(StandardCharsets.UTF_8);
            byte[] salienceData = "salience-profile-bytes".getBytes(StandardCharsets.UTF_8);

            try (IdentityBundle bundle = IdentityBundle.open(bundlePath, true)) {
                bundle.writeRaw(IdentityRegionId.SOUL, soulData);
                bundle.writeRaw(IdentityRegionId.SALIENCE, salienceData);
            }

            assertThat(Files.exists(bundlePath)).isTrue();
            assertThat(bundlePath.toFile().length()).isEqualTo(IdentityBundleHeader.TOTAL_INITIAL_SIZE);

            try (IdentityBundle reopened = IdentityBundle.open(bundlePath, false)) {
                Optional<byte[]> soul = reopened.readRaw(IdentityRegionId.SOUL);
                assertThat(soul).isPresent();
                assertThat(soul.get()).isEqualTo(soulData);

                Optional<byte[]> salience = reopened.readRaw(IdentityRegionId.SALIENCE);
                assertThat(salience).isPresent();
                assertThat(salience.get()).isEqualTo(salienceData);
            }
        }

        @Test
        @DisplayName("Throws when payload exceeds region allocated capacity")
        void capacityExceeded(@TempDir Path tempDir) {
            Path bundlePath = tempDir.resolve("overflow/identity.bundle");

            try (IdentityBundle bundle = IdentityBundle.open(bundlePath, true)) {
                byte[] hugePayload = new byte[(int) IdentityBundleHeader.DEFAULT_REGION_ALLOCATION + 10];
                assertThatThrownBy(() -> bundle.writeRaw(IdentityRegionId.SOUL, hugePayload))
                        .isInstanceOf(SpectorMemoryException.class);
            }
        }
    }
}
