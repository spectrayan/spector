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
package com.spectrayan.spector.kernel.scratch;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of {@link ScratchMemory} providing per-namespace scratch tracking (R8.1, R8.4, R8.5).
 */
public final class DefaultScratchMemory implements ScratchMemory {

    private final List<DefaultTokenVectorTable> tokenTables;
    private final List<DefaultFloatScratch> floatBuffers;
    private final AtomicLong totalAllocatedBytes;
    private volatile boolean closed;

    public DefaultScratchMemory() {
        this.tokenTables = new CopyOnWriteArrayList<>();
        this.floatBuffers = new CopyOnWriteArrayList<>();
        this.totalAllocatedBytes = new AtomicLong(0L);
        this.closed = false;
    }

    @Override
    public TokenVectorTable tokenTable(int maxEntries, int maxTokens, int dims) {
        if (closed) {
            throw new IllegalStateException("ScratchMemory has already been released");
        }
        DefaultTokenVectorTable table = new DefaultTokenVectorTable(maxEntries, maxTokens, dims, tokenTables::remove);
        tokenTables.add(table);
        return table;
    }

    @Override
    public FloatScratch floats(int capacity) {
        if (closed) {
            throw new IllegalStateException("ScratchMemory has already been released");
        }
        DefaultFloatScratch buffer = new DefaultFloatScratch(capacity, floatBuffers::remove);
        floatBuffers.add(buffer);
        return buffer;
    }

    @Override
    public long allocatedBytes() {
        if (closed) {
            return 0L;
        }
        long sum = 0L;
        for (DefaultTokenVectorTable table : tokenTables) {
            sum += table.allocatedBytes();
        }
        for (DefaultFloatScratch buffer : floatBuffers) {
            sum += buffer.byteSize();
        }
        return sum;
    }

    @Override
    public synchronized void releaseAll() {
        if (closed) {
            return;
        }
        closed = true;

        for (DefaultTokenVectorTable table : tokenTables) {
            try {
                table.close();
            } catch (Exception ignored) {}
        }
        tokenTables.clear();

        for (DefaultFloatScratch buffer : floatBuffers) {
            try {
                buffer.close();
            } catch (Exception ignored) {}
        }
        floatBuffers.clear();

        totalAllocatedBytes.set(0L);
    }
}
