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

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.migration.FormatId;
import com.spectrayan.spector.kernel.migration.MigrationContext;
import com.spectrayan.spector.kernel.migration.RewriteFileStep;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Migration step converting legacy TPCH v1 format to SMKM v2 format.
 */
public final class TpchToSmkmStep extends RewriteFileStep {

    public static final FormatId FROM_FORMAT = new FormatId(0x54504348, 1);
    public static final FormatId TO_FORMAT = FormatId.smkm(2);

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
            long totalDstSize = RegionPreamble.PREAMBLE_BYTES + dataSize;
            long now = System.currentTimeMillis();

            MemorySegment dstMapped = dstCh.map(FileChannel.MapMode.READ_WRITE, 0, totalDstSize, arena);
            RegionPreamble.write(dstMapped, 0L, ctx.layout().schemaVersion(), MemoryShape.CHAIN, 1,
                    0L, dataSize, ctx.layout().recordStride(), ctx.layout().layoutId(), now, now);

            if (dataSize > 0) {
                MemorySegment srcMapped = srcCh.map(FileChannel.MapMode.READ_ONLY, 16, dataSize, arena);
                MemorySegment.copy(srcMapped, 0, dstMapped, RegionPreamble.PREAMBLE_BYTES, dataSize);
            }
            dstMapped.force();
        }
    }
}
