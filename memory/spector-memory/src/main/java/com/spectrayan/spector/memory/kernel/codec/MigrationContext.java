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

import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

import java.nio.file.Path;
import java.util.Map;

/**
 * Encapsulates execution context for a migration hop.
 */
public record MigrationContext(
        Path sourcePath,
        MemoryId memoryId,
        MemoryLayout layout,
        DataEncryptor encryptor,
        Map<String, Path> sidecars,
        boolean keepBackup,
        boolean dryRun
) {
    public MigrationContext {
        if (sourcePath == null) {
            throw new IllegalArgumentException("sourcePath cannot be null");
        }
        if (sidecars == null) {
            sidecars = Map.of();
        }
    }
}
