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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.persist.DataLayoutVersion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DataLayoutVersion} — reading and writing the {@code layout.version}
 * marker at a memory data root.
 *
 * <p>Validates Requirements 17.2, 17.3, 17.5: the stored layout version is persisted,
 * read back, defaults to legacy flat when absent, and the write/read round-trip is
 * monotonic under repeated migration steps.</p>
 */
class DataLayoutVersionTest {

    @TempDir
    Path dataRoot;

    @Test
    void constantsMatchDesign() {
        assertThat(DataLayoutVersion.CURRENT).isEqualTo(4);
        assertThat(DataLayoutVersion.LEGACY_FLAT).isEqualTo(0);
    }

    @Test
    void readReturnsLegacyFlatWhenFileAbsent() {
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);
    }

    @Test
    void writeThenReadRoundTrips() {
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
    }

    @Test
    void writeCreatesDataRootWhenMissing() {
        Path nested = dataRoot.resolve("does").resolve("not").resolve("exist");
        DataLayoutVersion.write(nested, DataLayoutVersion.CURRENT);
        assertThat(DataLayoutVersion.read(nested)).isEqualTo(DataLayoutVersion.CURRENT);
    }

    @Test
    void writeOverwritesExistingVersion() {
        DataLayoutVersion.write(dataRoot, 1);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(1);
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
    }

    // ── isLegacyFlat (Requirement 17.3 — no-op at/above current) ──

    @Test
    void isLegacyFlatTrueWhenAbsent() {
        assertThat(DataLayoutVersion.isLegacyFlat(dataRoot)).isTrue();
    }

    @Test
    void isLegacyFlatTrueWhenBelowCurrent() {
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.LEGACY_FLAT);
        assertThat(DataLayoutVersion.isLegacyFlat(dataRoot)).isTrue();
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT - 1);
        assertThat(DataLayoutVersion.isLegacyFlat(dataRoot)).isTrue();
    }

    @Test
    void isLegacyFlatFalseAtCurrent() {
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT);
        assertThat(DataLayoutVersion.isLegacyFlat(dataRoot)).isFalse();
    }

    @Test
    void isLegacyFlatFalseAboveCurrent() {
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT + 1);
        assertThat(DataLayoutVersion.isLegacyFlat(dataRoot)).isFalse();
    }

    // ── Robustness: malformed / empty markers treated as legacy flat ──

    @Test
    void readTreatsEmptyFileAsLegacyFlat() throws IOException {
        Files.writeString(dataRoot.resolve(DataLayoutVersion.FILE_LAYOUT_VERSION), "",
                StandardCharsets.UTF_8);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);
    }

    @Test
    void readTreatsMalformedFileAsLegacyFlat() throws IOException {
        Files.writeString(dataRoot.resolve(DataLayoutVersion.FILE_LAYOUT_VERSION), "not-a-number",
                StandardCharsets.UTF_8);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);
    }

    @Test
    void readIgnoresSurroundingWhitespace() throws IOException {
        Files.writeString(dataRoot.resolve(DataLayoutVersion.FILE_LAYOUT_VERSION),
                "  " + DataLayoutVersion.CURRENT + "\n", StandardCharsets.UTF_8);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
    }
}
