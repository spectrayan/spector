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
package com.spectrayan.spector.kernel.region;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * Validates per-region schema versions on bundle open.
 *
 * <p>Each region slice in a partition or runtime bundle carries its own schema version in
 * two places: the {@link RegionEntry} in the bundle directory, and the region's own
 * {@link RegionPreamble} at the start of its mapped slice. This class validates that:
 * <ol>
 *   <li>The preamble version is at least 1 (no uninitialized preambles).</li>
 *   <li>The preamble version does not exceed the directory entry's version (no out-of-band updates).</li>
 * </ol>
 *
 * <p>Note: the directory entry stores the <em>current</em> layout version (e.g., {@code EpisodicLayout.VERSION = 2}),
 * while the preamble retains the version it was <em>originally written with</em> (e.g., 1). This
 * is expected after schema evolution — a preamble written by an older binary is readable by the
 * newer binary, so we only reject the case where the preamble is <em>newer</em> than the directory entry.</p>
 *
 * <p>This addresses a known limitation documented in
 * {@code docs/memory/bundle-format-compatibility.md}: PR #995 gated the
 * <em>directory-level</em> schema version but the per-region versions were not range-checked.
 * A region written by a future layout at the same bundle version would be accepted without
 * this gate.
 *
 * <h2>Design — why no per-RegionId registry yet</h2>
 * <p>All current layouts ({@code EngramLayout}, {@code EpisodicLayout}, {@code TextBlobLayout},
 * {@code StrengthLayout}, {@code SemanticLayout}, {@code ProceduralLayout}, etc.) declare
 * {@code schemaVersion() == 1}. Until a layout actually bumps its version, a declarative
 * mapping of {@code RegionId → [min, max]} would be a table of identical rows. Instead, this
 * class validates that region versions agree and are positive. When the first layout version
 * bump occurs, this class should be extended to hold explicit per-RegionId ranges.</p>
 *
 * @see RegionPreamble#readSchemaVersion
 * @see RegionEntry#schemaVersion()
 */
public final class RegionVersionRegistry {

    private RegionVersionRegistry() {
        // Utility class
    }

    /**
     * Validates all region entries against their on-disk preambles.
     *
     * <p>For each region entry that has a non-zero offset (i.e., is actually present in the
     * bundle), this method reads the preamble at that offset and checks:
     * <ol>
     *   <li>The preamble is valid (magic + CRC).</li>
     *   <li>The preamble's {@code schemaVersion} agrees with the directory entry's.</li>
     *   <li>The version is at least 1 (no zero-version regions should exist).</li>
     * </ol>
     *
     * @param masterSegment the fully mapped bundle segment
     * @param entries       the region entries parsed from the bundle directory
     * @throws SpectorStorageException if any region version fails validation
     */
    public static void validateRegionVersions(MemorySegment masterSegment, List<RegionEntry> entries) {
        for (RegionEntry entry : entries) {
            if (entry.offset() == 0 || entry.allocatedSize() == 0) {
                // Region not materialized in this bundle (e.g., optional region).
                continue;
            }

            long regionOffset = entry.offset();

            // Safety check: ensure we have enough bytes to read a preamble.
            // A region whose offset is at or beyond the segment boundary means the file
            // was not yet extended to include its data pages (common for freshly mmap-created
            // bundles). This is not corruption — skip rather than reject.
            if (regionOffset + RegionPreamble.PREAMBLE_BYTES > masterSegment.byteSize()) {
                continue;
            }

            // Validate preamble integrity (magic + CRC)
            if (!RegionPreamble.isValid(masterSegment, regionOffset)) {
                // Not all regions have SMKM preambles (e.g., graph regions with custom headers).
                // Skip validation for regions that don't use the standard preamble format.
                continue;
            }

            // Read the version from the actual region preamble
            int preambleVersion = RegionPreamble.readSchemaVersion(masterSegment, regionOffset);

            // Sanity check: version must be at least 1
            if (preambleVersion < 1) {
                throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID, String.format(
                        "region %s (id=%d) has schema version %d, which is below the minimum (1). "
                                + "This suggests a corrupted or uninitialized region preamble.",
                        entry.regionId().name(), entry.regionId().id(), preambleVersion));
            }

            // Check that the preamble version is not newer than the directory entry's version.
            // The directory entry stores the current layout version (e.g. EpisodicLayout.VERSION = 2),
            // while the preamble retains the version it was originally written with (e.g. 1). This
            // is expected after schema evolution — a preamble written by an older binary is readable
            // by the newer binary, but a preamble newer than what the directory expects would indicate
            // an out-of-band modification.
            if (preambleVersion > entry.schemaVersion()) {
                throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID, String.format(
                        "region %s (id=%d) preamble schema version %d exceeds the directory entry's "
                                + "version %d. The preamble appears newer than what this binary expects.",
                        entry.regionId().name(), entry.regionId().id(),
                        preambleVersion, entry.schemaVersion()));
            }
        }
    }
}
