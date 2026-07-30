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
package com.spectrayan.spector.memory.kernel.codec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Report of executed migration hops.
 */
public record MigrationResult(
        FormatId detected,
        FormatId current,
        List<FormatId> hops,
        List<Path> backups,
        Duration elapsed
) {
    public static MigrationResult freshFile(FormatId current) {
        return new MigrationResult(current, current, List.of(), List.of(), Duration.ZERO);
    }

    public boolean migrated() {
        return !hops.isEmpty();
    }
}
