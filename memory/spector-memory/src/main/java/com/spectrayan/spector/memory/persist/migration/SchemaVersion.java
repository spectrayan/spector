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
package com.spectrayan.spector.memory.persist.migration;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Represents a semantic version for namespace schema.
 *
 * <p>Used by the {@link MigrationPipeline} to detect stale namespaces
 * and run lazy migrations on load.</p>
 *
 * <p>Version history:</p>
 * <ul>
 *   <li>{@link #V1_0_0} — original flat layout, unencrypted</li>
 *   <li>{@link #V1_1_0} — added encryption marker support</li>
 *   <li>{@link #V2_0_0} — sharded directory layout + analytics metadata</li>
 * </ul>
 *
 * @param major breaking changes (layout incompatible)
 * @param minor new features (backward compatible)
 * @param patch bug fixes
 */
public record SchemaVersion(int major, int minor, int patch)
        implements Comparable<SchemaVersion> {

    // ── Well-known versions ──

    /** Original flat layout, unencrypted. */
    public static final SchemaVersion V1_0_0 = new SchemaVersion(1, 0, 0);

    /** Added encryption marker support. */
    public static final SchemaVersion V1_1_0 = new SchemaVersion(1, 1, 0);

    /** Sharded directory layout + analytics metadata. */
    public static final SchemaVersion V2_0_0 = new SchemaVersion(2, 0, 0);

    /** The current schema version that new namespaces are created with. */
    public static final SchemaVersion CURRENT = V2_0_0;

    /**
     * Parses a version string like "1.0.0" or "2.1.3".
     *
     * @param versionString dot-separated version (e.g., "1.0.0")
     * @return parsed SchemaVersion
     * @throws SpectorValidationException if format is invalid
     */
    public static SchemaVersion parse(String versionString) {
        if (versionString == null || versionString.isBlank()) {
            return V1_0_0; // assume oldest version for missing/blank
        }
        String[] parts = versionString.trim().split("\\.");
        if (parts.length != 3) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "schema version", versionString + " (expected format: major.minor.patch)");
        }
        try {
            return new SchemaVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, e,
                    "schema version", versionString);
        }
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        return Integer.compare(this.patch, other.patch);
    }

    /**
     * Returns true if this version is older (less) than the other.
     */
    public boolean isOlderThan(SchemaVersion other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Returns true if this version needs migration to reach the target.
     */
    public boolean needsMigration(SchemaVersion target) {
        return this.isOlderThan(target);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
