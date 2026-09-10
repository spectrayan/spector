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
package com.spectrayan.spector.kernel.sync;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Summary report of header schema migration execution (R9.1).
 */
public record MigrationReport(
        int fromVersion,
        int toVersion,
        int recordsMigrated,
        long bytesBefore,
        long bytesAfter,
        long durationNanos,
        Path backupPath,
        boolean lossy,
        boolean success,
        String message
) {
    public MigrationReport(int recordsMigrated, long bytesBefore, long bytesAfter,
                           Duration duration, Path backupPath, boolean lossy) {
        this(0, 0, recordsMigrated, bytesBefore, bytesAfter, duration != null ? duration.toNanos() : 0L,
             backupPath, lossy, true, "Success");
    }

    public static MigrationReport noop(int version) {
        return new MigrationReport(version, version, 0, 0L, 0L, 0L, null, false, true, "No migration needed");
    }
}
