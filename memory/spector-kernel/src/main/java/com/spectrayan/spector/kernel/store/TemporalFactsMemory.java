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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.kernel.shape.AbstractAppendMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * Durable append-only memory kernel store for 64-byte temporal fact records,
 * extending {@link AbstractAppendMemory} directly.
 */
public final class TemporalFactsMemory extends AbstractAppendMemory<TemporalFactLayout> {

    private static final MemoryId MEMORY_ID = SystemMemoryId.TEMPORAL_FACTS.id();

    public record FactLogEntry(long dataOffset, TemporalFact fact) {}

    /**
     * Creates an in-memory (heap) TemporalFactsMemory store with default capacity (64 KB).
     */
    public TemporalFactsMemory() {
        this(64L * 1024);
    }

    /**
     * Creates an in-memory (heap) TemporalFactsMemory store.
     *
     * @param initialSize initial byte capacity
     */
    public TemporalFactsMemory(long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize);
    }

    /**
     * Creates a file-backed (mmap) TemporalFactsMemory store.
     *
     * @param filePath    path to the facts data file
     * @param initialSize byte capacity for new files
     */
    public TemporalFactsMemory(Path filePath, long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize, filePath);
    }

    public static TemporalFactsMemory fromBundle(Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew) {
        return new TemporalFactsMemory(arena, regionSlice, bundlePath, isNew);
    }

    public static TemporalFactsMemory fromRegionRef(com.spectrayan.spector.kernel.bundle.RegionRef regionRef, Path bundlePath, boolean isNew) {
        return new TemporalFactsMemory(regionRef, bundlePath, isNew);
    }

    private TemporalFactsMemory(Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0L),
              true, bundlePath, null, true); // bundleManaged=true
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0L, new TemporalFactLayout().schemaVersion(), MemoryShape.APPEND, 0,
                    (int) segment().byteSize(), 0, 0, new TemporalFactLayout().layoutId(), now, now);
        }
    }

    private TemporalFactsMemory(com.spectrayan.spector.kernel.bundle.RegionRef regionRef, Path bundlePath, boolean isNew) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, regionRef,
              isNew ? 0 : (int) RegionPreamble.readCount(regionRef.resolve(), 0L),
              true, bundlePath);
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0L, new TemporalFactLayout().schemaVersion(), MemoryShape.APPEND, 0,
                    (int) segment().byteSize(), 0, 0, new TemporalFactLayout().layoutId(), now, now);
        }
    }

    /**
     * Appends a temporal fact to off-heap memory, computing CRC32C.
     *
     * @param fact the temporal fact to append
     * @return the offset in bytes where the fact record was written
     */
    public long appendFact(TemporalFact fact) {
        byte[] buffer = new byte[64];
        MemorySegment seg = MemorySegment.ofArray(buffer);
        fact.writeTo(seg, 0, layout());

        CRC32C crc = new CRC32C();
        buffer[56] = 0;
        buffer[57] = 0;
        buffer[58] = 0;
        buffer[59] = 0;
        crc.update(buffer);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, TemporalFactLayout.OFF_CRC32C, (int) crc.getValue());

        return append(seg);
    }

    /**
     * Reads a temporal fact from the specified payload offset.
     *
     * @param offset payload offset returned by appendFact or replay
     * @return the decoded TemporalFact
     */
    public TemporalFact readFact(long offset) {
        MemorySegment seg = read(offset, 64);
        return TemporalFact.readFrom(seg, 0, layout());
    }

    /**
     * Replays all facts from offset 0, returning a list of FactLogEntry.
     */
    public List<FactLogEntry> replayAllFacts() {
        List<FactLogEntry> entries = new ArrayList<>();
        long cursor = 0;
        Iterator<MemorySegment> it = replay(0);
        while (it.hasNext()) {
            MemorySegment seg = it.next();
            long segSize = seg.byteSize();
            if (segSize >= 64) {
                TemporalFact fact = TemporalFact.readFrom(seg, 0, layout());
                entries.add(new FactLogEntry(cursor + 4, fact));
            }
            cursor += 4 + segSize;
        }
        return entries;
    }

    /**
     * Copies all facts from another TemporalFactsMemory instance into this one.
     */
    public void copyAllFrom(TemporalFactsMemory legacy) {
        for (FactLogEntry entry : legacy.replayAllFacts()) {
            appendFact(entry.fact());
        }
        flush();
    }
}
