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
