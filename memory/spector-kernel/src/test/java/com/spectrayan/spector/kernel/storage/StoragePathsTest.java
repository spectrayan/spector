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
package com.spectrayan.spector.kernel.storage;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StoragePaths} — directory resolvers, partition naming,
 * WAL file naming, and legacy path resolvers.
 */
@DisplayName("StoragePaths")
class StoragePathsTest {

    private static final Path BASE = Path.of("/data/spector");

    // ══════════════════════════════════════════════════════════════
    // Top-level directories
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("directory resolvers")
    class DirectoryTests {

        @Test void runtimeDir() {
            assertThat(StoragePaths.runtimeDir(BASE)).isEqualTo(BASE.resolve("runtime"));
        }

        @Test void partitionsDir() {
            assertThat(StoragePaths.partitionsDir(BASE)).isEqualTo(BASE.resolve("partitions"));
        }

        @Test void walDir() {
            assertThat(StoragePaths.walDir(BASE)).isEqualTo(BASE.resolve("wal"));
        }

        @Test void namespacesDir() {
            assertThat(StoragePaths.namespacesDir(BASE)).isEqualTo(BASE.resolve("namespaces"));
        }

        @Test void namespaceDir() {
            assertThat(StoragePaths.namespaceDir(BASE, "agent-alpha"))
                    .isEqualTo(BASE.resolve("namespaces").resolve("agent-alpha"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Global file resolvers
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("global file resolvers")
    class GlobalFileTests {

        @Test void manifest() {
            assertThat(StoragePaths.manifest(BASE)).isEqualTo(BASE.resolve("manifest.json"));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Partition naming
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("partition naming")
    class PartitionNamingTests {

        @Test void dirName() {
            assertThat(StoragePaths.partitionDirName(0, 1717430400))
                    .isEqualTo("000_1717430400");
        }

        @Test void dirNameHighSeq() {
            assertThat(StoragePaths.partitionDirName(42, 1717430400))
                    .isEqualTo("042_1717430400");
        }

        @Test void partitionDir() {
            assertThat(StoragePaths.partitionDir(BASE, 3, 1717603200))
                    .isEqualTo(BASE.resolve("partitions/003_1717603200"));
        }

        @Test void parseSeqNo() {
            assertThat(StoragePaths.parsePartitionSeqNo("003_1717603200")).isEqualTo(3);
        }

        @Test void parseEpoch() {
            assertThat(StoragePaths.parsePartitionEpoch("003_1717603200")).isEqualTo(1717603200L);
        }

        @Test void isPartitionDir_valid() {
            assertThat(StoragePaths.isPartitionDir("000_1717430400")).isTrue();
            assertThat(StoragePaths.isPartitionDir("042_9999999999")).isTrue();
        }

        @Test void isPartitionDir_invalid() {
            assertThat(StoragePaths.isPartitionDir(null)).isFalse();
            assertThat(StoragePaths.isPartitionDir("")).isFalse();
            assertThat(StoragePaths.isPartitionDir("abc")).isFalse();
            assertThat(StoragePaths.isPartitionDir("000-1717430400")).isFalse(); // wrong separator
            assertThat(StoragePaths.isPartitionDir("0_1")).isFalse(); // too short seq
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Partition file resolvers
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("partition file resolvers")
    class PartitionFileTests {

        private final Path partDir = Path.of("/data/spector/partitions/000_1717430400");

        @Test void partitionFile() { assertThat(StoragePaths.partitionFile(partDir, "test.dat").getFileName().toString()).isEqualTo("test.dat"); }
    }

    // ══════════════════════════════════════════════════════════════
    // Cross-partition file resolvers
    // ══════════════════════════════════════════════════════════════



    // ══════════════════════════════════════════════════════════════
    // WAL file resolvers
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WAL files")
    class WalTests {

        @Test void walFileName() {
            assertThat(StoragePaths.walFileName(1)).isEqualTo("wal-000001.bin");
            assertThat(StoragePaths.walFileName(42)).isEqualTo("wal-000042.bin");
        }

        @Test void walFile() {
            assertThat(StoragePaths.walFile(BASE, 1))
                    .isEqualTo(BASE.resolve("wal/wal-000001.bin"));
        }
    }



    // ══════════════════════════════════════════════════════════════
    // Constants
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("magic numbers are non-zero")
    void magicNumbers() {
        assertThat(StoragePaths.TEXT_DAT_MAGIC).isNotZero();
        assertThat(StoragePaths.INDEX_MIDX_MAGIC).isNotZero();
    }

    @Test
    @DisplayName("partition pattern matches valid names")
    void partitionPattern() {
        var matcher = StoragePaths.PARTITION_DIR_PATTERN.matcher("042_1717430400");
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("042");
        assertThat(matcher.group(2)).isEqualTo("1717430400");
    }
}
