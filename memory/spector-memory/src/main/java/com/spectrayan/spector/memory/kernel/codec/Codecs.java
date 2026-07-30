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
import com.spectrayan.spector.memory.cortex.TextAppendCodec;
import com.spectrayan.spector.memory.cortex.TypeRegistryCodec;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCodec;
import com.spectrayan.spector.memory.index.IndexRecordCodec;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.temporal.TemporalChainCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Static entry point for ensuring memory files are converted to current SMKM schema.
 */
public final class Codecs {

    private static final CodecRegistry DEFAULT_REGISTRY = CodecRegistry.builder()
            .register(new TemporalChainCodec())
            .register(new TextAppendCodec())
            .register(new TypeRegistryCodec())
            .register(new IndexRecordCodec())
            .register(new HebbianGraphCodec())
            .build();

    private Codecs() {}

    public static CodecRegistry defaultRegistry() {
        return DEFAULT_REGISTRY;
    }

    public static MigrationResult ensureCurrent(CodecRegistry registry, MemoryId id,
                                                MemoryLayout layout, Path filePath,
                                                DataEncryptor enc, Map<String, Path> sidecars)
            throws IOException {
        if (filePath == null) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        CodecRegistry reg = (registry != null) ? registry : DEFAULT_REGISTRY;
        Optional<Codec<?>> codecOpt = reg.byLayoutId(layout.layoutId());
        if (codecOpt.isEmpty()) {
            return MigrationResult.freshFile(FormatId.smkm(layout.schemaVersion()));
        }

        MigrationContext ctx = new MigrationContext(
                filePath, id, layout, enc, sidecars, true, false
        );

        return codecOpt.get().ensureCurrent(ctx);
    }
}
