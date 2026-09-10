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
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;

import java.nio.file.Path;
import java.util.Map;

/**
 * Encapsulates execution context for a migration hop.
 */
public record MigrationContext(
        Path sourcePath,
        MemoryId memoryId,
        RegionLayout layout,
        Object encryptor,
        Map<String, Path> sidecars,
        boolean keepBackup,
        boolean dryRun
) {
    public MigrationContext {
        if (sourcePath == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "sourcePath");
        }
        if (sidecars == null) {
            sidecars = Map.of();
        }
    }
}
