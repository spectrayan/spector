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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Off-heap {@link TokenVectorTable} storing per-token embeddings in managed native memory (R8.1, R8.3).
 */
public final class DefaultTokenVectorTable implements TokenVectorTable {

    private final int maxEntries;
    private final int maxTokens;
    private final int dims;
    private final Map<String, Entry> entries;
    private final AtomicLong allocatedBytes;
    private final ReadWriteLock rwLock;
    private final Consumer<DefaultTokenVectorTable> onCloseCallback;
    private volatile boolean closed;

    private record Entry(MemorySegment segment, Arena arena, int tokenCount, int tokenDims, long accessTime, long byteSize) {}

    public DefaultTokenVectorTable(int maxEntries, int maxTokens, int dims) {
        this(maxEntries, maxTokens, dims, null);
    }

    public DefaultTokenVectorTable(int maxEntries, int maxTokens, int dims, Consumer<DefaultTokenVectorTable> onCloseCallback) {
        if (maxEntries <= 0 || maxTokens <= 0 || dims <= 0) {
            throw new IllegalArgumentException("maxEntries, maxTokens, and dims must be positive: entries="
                    + maxEntries + ", tokens=" + maxTokens + ", dims=" + dims);
        }
        this.maxEntries = maxEntries;
        this.maxTokens = maxTokens;
        this.dims = dims;
        this.entries = new ConcurrentHashMap<>();
        this.allocatedBytes = new AtomicLong(0L);
        this.rwLock = new ReentrantReadWriteLock();
        this.onCloseCallback = onCloseCallback;
        this.closed = false;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("TokenVectorTable has already been closed");
        }
    }

    @Override
    public void put(String docId, float[][] tokenVectors) {
        if (closed || docId == null || tokenVectors == null || tokenVectors.length == 0) {
            return;
        }
        ensureOpen();

        final int tokenCount = Math.min(tokenVectors.length, maxTokens);
        final int tokenDims = Math.min(tokenVectors[0].length, dims);
        final long byteSize = (long) tokenCount * tokenDims * ValueLayout.JAVA_FLOAT.byteSize();

        rwLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }

            // Evict LRU if at capacity
            if (entries.size() >= maxEntries && !entries.containsKey(docId)) {
                evictOldest();
            }

            // If entry already exists, close previous arena and adjust bytes
            Entry oldEntry = entries.remove(docId);
            if (oldEntry != null) {
                allocatedBytes.addAndGet(-oldEntry.byteSize());
                try {
                    oldEntry.arena().close();
                } catch (Exception ignored) {}
            }

            Arena entryArena = Arena.ofShared();
            MemorySegment segment = entryArena.allocate(byteSize, 8);

            for (int t = 0; t < tokenCount; t++) {
                long tokenOffset = (long) t * tokenDims * ValueLayout.JAVA_FLOAT.byteSize();
                MemorySegment.copy(tokenVectors[t], 0, segment, ValueLayout.JAVA_FLOAT, tokenOffset, tokenDims);
            }

            entries.put(docId, new Entry(segment, entryArena, tokenCount, tokenDims, System.currentTimeMillis(), byteSize));
            allocatedBytes.addAndGet(byteSize);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public boolean get(String docId, float[][] dest) {
        if (closed || docId == null || dest == null) {
            return false;
        }

        rwLock.readLock().lock();
        try {
            Entry entry = entries.get(docId);
            if (entry == null) {
                return false;
            }

            int tokenCount = Math.min(entry.tokenCount(), dest.length);
            int tokenDims = entry.tokenDims();

            for (int t = 0; t < tokenCount; t++) {
                int copyDims = Math.min(tokenDims, dest[t].length);
                long tokenOffset = (long) t * tokenDims * ValueLayout.JAVA_FLOAT.byteSize();
                MemorySegment.copy(entry.segment(), ValueLayout.JAVA_FLOAT, tokenOffset, dest[t], 0, copyDims);
            }

            entries.put(docId, new Entry(entry.segment(), entry.arena(), entry.tokenCount(), entry.tokenDims(), System.currentTimeMillis(), entry.byteSize()));
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public float[][] get(String docId) {
        if (closed || docId == null) {
            return null;
        }

        rwLock.readLock().lock();
        try {
            Entry entry = entries.get(docId);
            if (entry == null) {
                return null;
            }

            int tokenCount = entry.tokenCount();
            int tokenDims = entry.tokenDims();
            float[][] result = new float[tokenCount][tokenDims];

            for (int t = 0; t < tokenCount; t++) {
                long tokenOffset = (long) t * tokenDims * ValueLayout.JAVA_FLOAT.byteSize();
                MemorySegment.copy(entry.segment(), ValueLayout.JAVA_FLOAT, tokenOffset, result[t], 0, tokenDims);
            }

            entries.put(docId, new Entry(entry.segment(), entry.arena(), tokenCount, tokenDims, System.currentTimeMillis(), entry.byteSize()));
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public int maxEntries() {
        return maxEntries;
    }

    @Override
    public int maxTokens() {
        return maxTokens;
    }

    @Override
    public int dims() {
        return dims;
    }

    @Override
    public void evictOldest() {
        rwLock.writeLock().lock();
        try {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                if (e.getValue().accessTime() < oldestTime) {
                    oldestTime = e.getValue().accessTime();
                    oldestKey = e.getKey();
                }
            }

            if (oldestKey != null) {
                Entry removed = entries.remove(oldestKey);
                if (removed != null) {
                    allocatedBytes.addAndGet(-removed.byteSize());
                    try {
                        removed.arena().close();
                    } catch (Exception ignored) {}
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        rwLock.writeLock().lock();
        try {
            for (Entry entry : entries.values()) {
                try {
                    entry.arena().close();
                } catch (Exception ignored) {}
            }
            entries.clear();
            allocatedBytes.set(0L);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public long allocatedBytes() {
        return allocatedBytes.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        rwLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            clear();
            if (onCloseCallback != null) {
                onCloseCallback.accept(this);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
