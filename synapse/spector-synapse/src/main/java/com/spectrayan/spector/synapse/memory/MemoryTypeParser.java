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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;

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
