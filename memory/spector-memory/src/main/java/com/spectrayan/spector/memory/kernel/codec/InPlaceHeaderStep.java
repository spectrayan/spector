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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Template for header-only migrations. Rewrites header region in-place.
 */
public abstract non-sealed class InPlaceHeaderStep implements CodecStep {

    @Override
    public final Kind kind() {
        return Kind.IN_PLACE_HEADER;
    }

    @Override
    public final void apply(MigrationContext ctx) throws IOException {
        try (FileChannel ch = FileChannel.open(ctx.sourcePath(), READ, WRITE);
             Arena arena = Arena.ofConfined()) {
            MemorySegment mapped = ch.map(FileChannel.MapMode.READ_WRITE, 0, ch.size(), arena);
            rewriteHeader(mapped, ctx);
            mapped.force();
        }
    }

    protected abstract void rewriteHeader(MemorySegment mapped, MigrationContext ctx);
}
