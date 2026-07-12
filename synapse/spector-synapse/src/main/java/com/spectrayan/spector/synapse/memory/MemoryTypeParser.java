/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Shared utility for safely parsing MemoryType and MemorySource enums.
 */
public final class MemoryTypeParser {

    private MemoryTypeParser() {}

    public static MemoryType safeMemoryType(String name, MemoryType fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemoryType.valueOf(name.toUpperCase().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static MemorySource safeMemorySource(String name, MemorySource fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return MemorySource.valueOf(name.toUpperCase().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
