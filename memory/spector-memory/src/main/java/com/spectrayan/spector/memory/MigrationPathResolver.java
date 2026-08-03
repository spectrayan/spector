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
package com.spectrayan.spector.memory;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the load-priority of persisted memory artifacts when several
 * candidate locations exist (runtime dir, active partition dir, legacy flat
 * layout).
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.getNewerPath} as part
 * of the #437 god-class decomposition. The selection rule is unchanged: pick the
 * candidate with the most recent last-modified time, ignoring candidates that do
 * not exist or whose timestamp cannot be read.</p>
 *
 * @since 1.1.0
 */
final class MigrationPathResolver {

    private static final Logger log = LoggerFactory.getLogger(MigrationPathResolver.class);

    private MigrationPathResolver() {}

    /**
     * Returns whichever of the given candidate paths was modified most recently,
     * or {@code null} if none of them exist. {@code null} candidates are skipped.
     */
    static Path getNewerPath(Path runtimePath, Path partitionPath, Path legacyPath) {
        Path target = null;
        long latestTime = Long.MIN_VALUE;

        if (runtimePath != null && java.nio.file.Files.exists(runtimePath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(runtimePath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = runtimePath;
                }
            } catch (java.io.IOException e) {
                log.debug("Failed to read last modified time for path: {}", runtimePath, e);
            }
        }

        if (partitionPath != null && java.nio.file.Files.exists(partitionPath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(partitionPath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = partitionPath;
                }
            } catch (java.io.IOException e) {
                log.debug("Failed to read last modified time for path: {}", partitionPath, e);
            }
        }

        if (legacyPath != null && java.nio.file.Files.exists(legacyPath)) {
            try {
                long t = java.nio.file.Files.getLastModifiedTime(legacyPath).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    target = legacyPath;
                }
            } catch (java.io.IOException e) {
                log.debug("Failed to read last modified time for path: {}", legacyPath, e);
            }
        }

        return target;
    }
}
