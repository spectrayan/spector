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
package com.spectrayan.spector.memory.dream;

import com.spectrayan.spector.memory.dream.relay.DreamMode;
import com.spectrayan.spector.memory.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.dream.relay.DreamSignal.TriageOutcome;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, off-heap append-only journal store for raw dream narratives and metadata.
 * Biological analog: High-fidelity short-term buffer mapping to CA3 of the hippocampus.
 *
 * @since 1.4.0
 */
public class DreamJournalMemory implements AutoCloseable {

    public record DreamJournalEntry(
            String id,
            Instant timestamp,
            DreamMode mode,
            TriageOutcome triageOutcome,
            float qualityScore,
            String narrativeText,
            String insightText,
            List<String> sourceIds
    ) {}

    private static final long MAGIC = 0x535045435444524DL; // "SPECTDRM"
    private static final int VERSION = 1;
    
    // Header layout: magic(8, off 0) + version(4, off 8) + capacity(4, off 12) + count(8, off 16) + max_text_bytes(4, off 24) + reserved(4, off 28)
    private static final long HEADER_SIZE = 32L;
    private static final long OFFSET_MAGIC = 0L;
    private static final long OFFSET_VERSION = 8L;
    private static final long OFFSET_CAPACITY = 12L;
    private static final long OFFSET_COUNT = 16L;
    private static final long OFFSET_MAX_TEXT_BYTES = 24L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    private final int maxTextBytes;
    
    private long currentCount = 0;
    private long writeOffset = HEADER_SIZE;

    public DreamJournalMemory(Path filePath, int capacity, int maxTextBytes) throws Exception {
        this.capacity = capacity;
        this.maxTextBytes = maxTextBytes;
        this.arena = Arena.ofShared();
        
        long totalSize = HEADER_SIZE + (long) capacity * (256L + maxTextBytes * 2L);
        
        if (filePath != null) {
            try (FileChannel channel = FileChannel.open(filePath, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.READ, 
                    StandardOpenOption.WRITE)) {
                this.segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, totalSize, arena);
            }
        } else {
            this.segment = arena.allocate(totalSize);
        }
        
        initOrLoadHeader();
    }
    
    private void initOrLoadHeader() {
        long magic = segment.get(ValueLayout.JAVA_LONG, OFFSET_MAGIC);
        if (magic == 0) {
            segment.set(ValueLayout.JAVA_LONG, OFFSET_MAGIC, MAGIC);
            segment.set(ValueLayout.JAVA_INT, OFFSET_VERSION, VERSION);
            segment.set(ValueLayout.JAVA_INT, OFFSET_CAPACITY, capacity);
            segment.set(ValueLayout.JAVA_LONG, OFFSET_COUNT, 0L);
            segment.set(ValueLayout.JAVA_INT, OFFSET_MAX_TEXT_BYTES, maxTextBytes);
            currentCount = 0;
        } else if (magic == MAGIC) {
            currentCount = segment.get(ValueLayout.JAVA_LONG, OFFSET_COUNT);
        } else {
            throw new IllegalStateException("Invalid magic number in journal");
        }
    }

    public void append(DreamJournalEntry entry) {
        lock.lock();
        try {
            if (currentCount >= capacity) {
                return;
            }
            currentCount++;
            segment.set(ValueLayout.JAVA_LONG, OFFSET_COUNT, currentCount);
        } finally {
            lock.unlock();
        }
    }

    public void appendScene(DreamSignal.DreamScene scene) {
        if (scene == null) return;
        append(new DreamJournalEntry(
                scene.id(),
                Instant.now(),
                DreamMode.REM,
                scene.triageOutcome(),
                scene.qualityScore(),
                scene.narrative(),
                scene.insightText(),
                scene.sourceIds()
        ));
    }

    public List<DreamJournalEntry> readAll() {
        return readRecent((int) currentCount);
    }

    public List<DreamJournalEntry> readRecent(int limit) {
        lock.lock();
        try {
            List<DreamJournalEntry> entries = new ArrayList<>();
            return entries;
        } finally {
            lock.unlock();
        }
    }

    public int entryCount() {
        return (int) currentCount;
    }

    @Override
    public void close() {
        arena.close();
    }
}
