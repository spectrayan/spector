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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Static entry point for ensuring memory files are converted to current SMKM schema.
 */
public final class Codecs {

    private Codecs() {}

    public static MigrationResult ensureCurrent(CodecRegistry registry, MemoryId id,
                                                MemoryLayout layout, Path filePath,
                                                DataEncryptor enc, Map<String, Path> sidecars)
            throws IOException {
        if (filePath == null || registry == null) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        Optional<Codec<?>> codecOpt = registry.byLayoutId(layout.layoutId());
        if (codecOpt.isEmpty()) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        MigrationContext ctx = new MigrationContext(
                filePath, id, layout, enc, sidecars, true, false
        );

        return codecOpt.get().ensureCurrent(ctx);
    }
}
