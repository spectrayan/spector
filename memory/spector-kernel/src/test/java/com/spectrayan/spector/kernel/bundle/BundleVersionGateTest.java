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
package com.spectrayan.spector.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.shape.MemoryShape;

/**
 * Verifies that a bundle whose format version this binary does not claim to read is refused.
 *
 * <h3>Why this is not a "nice to have"</h3>
 * <p>{@code BundleFileLayout.SCHEMA_VERSION} was written into two places per file and read back by nothing.
 * Worse than a missing check: {@code Init.open} maps the file {@code READ_WRITE}, and {@code close()} rewrites
 * the directory with the <em>current</em> version — so an unfamiliar bundle was not merely accepted, it was
 * relabelled as current, destroying the only evidence that another binary wrote it. The next open then saw a
 * well-formed current-version file whose regions were laid out by a different writer.</p>
 *
 * <p>The directory says where every region begins, so misreading it surfaces as corrupt <em>records</em> —
 * far from the actual cause.</p>
 */
@DisplayName("Bundle Format Version Gate")
class BundleVersionGateTest {

    private static final int MAX_REGIONS = 4;

    /** Builds a structurally valid partition directory in memory, at the current format version. */
    private static BundleDirectory validDirectory() {
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.CONTINUITY,
                        ContinuityLayout.DATA_START + 8L * ContinuityLayout.RECORD_STRIDE,
                        8, ContinuityLayout.RECORD_STRIDE,
                        ContinuityLayout.LAYOUT_ID, ContinuityLayout.SCHEMA_VERSION, false));
        return BundleFileLayoutCalculator.compute(BundleSubHeader.MAGIC_PARTITION, specs).directory();
    }

    private static MemorySegment writtenBundle(Arena arena) {
        BundleDirectory dir = validDirectory();
        MemorySegment seg = arena.allocate(1 << 20, 4096);
        dir.write(seg);
        return seg;
    }

    /**
     * Rewrites the bundle-level version in the offset-0 preamble, preserving every other field and
     * recomputing the CRC — so the resulting bundle is structurally valid and differs only in version.
     */
    private static void setPreambleVersion(MemorySegment seg, int version) {
        RegionPreamble.write(seg, BundleDirectory.HEADER_OFFSET,
                version,
                MemoryShape.BUNDLE,
                RegionPreamble.readFlags(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readCapacity(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readCount(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readRecordStride(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readLayoutId(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readCreatedAt(seg, BundleDirectory.HEADER_OFFSET),
                RegionPreamble.readLastFlush(seg, BundleDirectory.HEADER_OFFSET));
    }

    /** Rewrites the bundle-level version in the sub-header, preserving every other field. */
    private static void setSubHeaderVersion(MemorySegment seg, int version) {
        BundleSubHeader.write(seg,
                BundleSubHeader.readBundleMagic(seg),
                version,
                BundleSubHeader.readTotalFileSize(seg),
                BundleSubHeader.readDirChecksum(seg),
                BundleSubHeader.readCapacityConfig(seg),
                BundleSubHeader.readRegionCount(seg),
                BundleSubHeader.readDataStartOffset(seg));
    }

    private static void setBothVersions(MemorySegment seg, int version) {
        setPreambleVersion(seg, version);
        setSubHeaderVersion(seg, version);
    }

    @Test
    @DisplayName("a bundle at the current version opens")
    void currentVersionOpens() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);

            assertThatCode(() -> BundleDirectory.read(seg)).doesNotThrowAnyException();
            assertThat(RegionPreamble.readSchemaVersion(seg, BundleDirectory.HEADER_OFFSET))
                    .isEqualTo(BundleFileLayout.SCHEMA_VERSION);
        }
    }

    @Test
    @DisplayName("a bumped version is refused, naming the file's version and the readable range")
    void newerVersionIsRefused() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);
            setBothVersions(seg, BundleFileLayout.SCHEMA_VERSION + 1);

            assertThatThrownBy(() -> BundleDirectory.read(seg))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(e -> assertThat(((SpectorStorageException) e).errorCode())
                            .isEqualTo(ErrorCode.FILE_FORMAT_INVALID))
                    .hasMessageContaining(String.valueOf(BundleFileLayout.SCHEMA_VERSION + 1))
                    .hasMessageContaining(String.valueOf(BundleFileLayout.SCHEMA_VERSION))
                    // A refusal that does not say what to do just relocates the confusion.
                    .hasMessageContaining("newer Spector");
        }
    }

    @Test
    @DisplayName("a version below the readable floor is refused, and says to migrate with an older release")
    void olderVersionIsRefused() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);
            setBothVersions(seg, BundleFileLayout.MIN_READABLE_SCHEMA_VERSION - 1);

            assertThatThrownBy(() -> BundleDirectory.read(seg))
                    .isInstanceOf(SpectorStorageException.class)
                    .hasMessageContaining("migrate it with an older release");
        }
    }

    @Test
    @DisplayName("the two recorded copies of the version must agree")
    void disagreeingVersionCopiesAreRefused() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);
            // Only the sub-header rewritten: the shape a partial or interrupted rewrite would leave behind.
            setSubHeaderVersion(seg, BundleFileLayout.SCHEMA_VERSION + 5);

            assertThatThrownBy(() -> BundleDirectory.read(seg))
                    .isInstanceOf(SpectorStorageException.class)
                    .hasMessageContaining("disagrees")
                    .hasMessageContaining("Refusing to guess");
        }
    }

    @Test
    @DisplayName("a partition bundle is refused when a runtime bundle was expected, and vice versa")
    void mismatchedBundleMagicIsRefused() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment partition = writtenBundle(arena);

            assertThatThrownBy(() -> BundleDirectory.read(partition, BundleSubHeader.MAGIC_RUNTIME))
                    .isInstanceOf(SpectorStorageException.class)
                    .satisfies(e -> assertThat(((SpectorStorageException) e).errorCode())
                            .isEqualTo(ErrorCode.FILE_FORMAT_INVALID))
                    // Names both roles, so the operator knows which file they pointed at what.
                    .hasMessageContaining("partition")
                    .hasMessageContaining("runtime");
        }
    }

    @Test
    @DisplayName("passing magic 0 accepts either bundle type")
    void zeroMagicAcceptsEither() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);

            assertThatCode(() -> BundleDirectory.read(seg, 0)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the readable range is a closed, non-empty interval ending at the written version")
    void readableRangeIsCoherent() {
        // A floor above the write version would make every bundle this binary writes unreadable by itself.
        assertThat(BundleFileLayout.MIN_READABLE_SCHEMA_VERSION)
                .isLessThanOrEqualTo(BundleFileLayout.SCHEMA_VERSION)
                .isPositive();
    }

    @Test
    @DisplayName("the published compatibility matrix matches the constants it claims to describe")
    void publishedMatrixMatchesTheCode() throws Exception {
        // docs/memory/bundle-format-compatibility.md states "The table above is generated by reading those
        // constants, so it cannot drift from the code without a test failing." This is that test. Without it
        // the doc would be exactly the kind of confident-but-unchecked claim this spec family exists to
        // remove.
        java.nio.file.Path doc = java.nio.file.Path.of("..", "..", "docs", "memory",
                "bundle-format-compatibility.md").normalize();
        assertThat(java.nio.file.Files.exists(doc))
                .as("compatibility matrix must exist at %s", doc.toAbsolutePath()).isTrue();

        String text = java.nio.file.Files.readString(doc);
        String expectedRow = String.format("| current `main` | `%d` | `%d` |",
                BundleFileLayout.SCHEMA_VERSION, BundleFileLayout.MIN_READABLE_SCHEMA_VERSION);
        assertThat(text)
                .as("the documented write/read versions must match BundleFileLayout. Expected row: %s",
                        expectedRow)
                .contains(expectedRow);
        assertThat(text)
                .as("the documented refusal range must match the constants")
                .contains(String.format("anything outside `[%d, %d]`",
                        BundleFileLayout.MIN_READABLE_SCHEMA_VERSION, BundleFileLayout.SCHEMA_VERSION));
    }

    @Test
    @DisplayName("region count is unchanged by the gate")
    void gateDoesNotAlterTheDirectory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = writtenBundle(arena);

            BundleDirectory read = BundleDirectory.read(seg);

            assertThat(read.bundleMagic()).isEqualTo(BundleSubHeader.MAGIC_PARTITION);
            assertThat(read.liveRegionCount()).isEqualTo(1);
            assertThat(MAX_REGIONS).isGreaterThanOrEqualTo(read.liveRegionCount());
        }
    }
}
