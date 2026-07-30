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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.codec.FormatId;
import com.spectrayan.spector.memory.kernel.codec.MigrationContext;
import com.spectrayan.spector.memory.kernel.codec.RewriteFileStep;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Migration step converting legacy Type Registry format (TREG) to SMKM format.
 */
public final class TregToSmkmStep extends RewriteFileStep {

    public static final FormatId FROM_FORMAT = new FormatId(0x54524547, 1);
    public static final FormatId TO_FORMAT = FormatId.smkm(1);

    @Override
    public FormatId from() {
        return FROM_FORMAT;
    }

    @Override
    public FormatId to() {
        return TO_FORMAT;
    }

    @Override
    protected void rewrite(Path source, Path target, MigrationContext ctx) throws IOException {
        try (FileChannel srcCh = FileChannel.open(source, READ);
             FileChannel dstCh = FileChannel.open(target, CREATE, READ, WRITE);
             Arena arena = Arena.ofConfined()) {

            long srcSize = srcCh.size();
            long dataSize = Math.max(0, srcSize - 16);
            long totalDstSize = MemoryHeader.HEADER_BYTES + dataSize;
            long now = System.currentTimeMillis();

            MemorySegment dstMapped = dstCh.map(FileChannel.MapMode.READ_WRITE, 0, totalDstSize, arena);
            MemoryHeader.write(dstMapped, 0L, ctx.layout().schemaVersion(), MemoryShape.APPEND, 1,
                    0L, dataSize, ctx.layout().recordStride(), ctx.layout().layoutId(), now, now);

            if (dataSize > 0) {
                MemorySegment srcMapped = srcCh.map(FileChannel.MapMode.READ_ONLY, 16, dataSize, arena);
                MemorySegment.copy(srcMapped, 0, dstMapped, MemoryHeader.HEADER_BYTES, dataSize);
            }
            dstMapped.force();
        }
    }
}
