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

import com.spectrayan.spector.memory.kernel.MemoryLayout;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

/**
 * Migration authority for a single MemoryLayout.
 * Owns the ordered set of CodecSteps leading to current SMKM schema version.
 */
public interface Codec<L extends MemoryLayout> {

    L layout();

    default FormatId current() {
        return FormatId.smkm(layout().schemaVersion());
    }

    Set<Integer> legacyMagics();

    int versionOf(int magic, MemorySegment headerPrefix);

    List<CodecStep> steps();

    default MigrationResult ensureCurrent(MigrationContext ctx) throws IOException {
        return CodecChain.of(this).run(ctx);
    }
}
