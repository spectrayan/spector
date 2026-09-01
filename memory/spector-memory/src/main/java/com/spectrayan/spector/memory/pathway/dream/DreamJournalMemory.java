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
package com.spectrayan.spector.memory.pathway.dream;

import com.spectrayan.spector.memory.pathway.dream.relay.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal.TriageOutcome;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, off-heap append-only journal store for raw dream narratives and metadata.
 *
 * <h3>Biological Analog: Hippocampal CA3/Episodic Trace Buffer</h3>
 * <p>Stores high-fidelity raw dream narratives and simulation records purely as an append-only audit
 * trail, strictly isolated from standard associative recall to prevent imagination from laundering into factual belief.</p>
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

    public static final long MAGIC = 0x535045435444524DL; // "SPECTDRM"
    public static final int VERSION = 1;

    // Header layout: magic(8, off 0) + version(4, off 8) + capacity(4, off 12) + count(8, off 16) + max_text_bytes(4, off 24) + reserved(4, off 28)
    private static final long HEADER_SIZE = 32L;
    private static final long OFFSET_MAGIC = 0L;
    private static final long OFFSET_VERSION = 8L;
    private static final long OFFSET_CAPACITY = 12L;
    private static final long OFFSET_COUNT = 16L;
    private static final long OFFSET_MAX_TEXT_BYTES = 24L;

    // Entry offsets relative to entry base
    private static final long ENTRY_OFF_ID_LEN = 0L;
    private static final long ENTRY_OFF_ID_BYTES = 4L;
    private static final long ID_MAX_BYTES = 36L;
    private static final long ENTRY_OFF_TIMESTAMP = 40L;
    private static final long ENTRY_OFF_MODE = 48L;
    private static final long ENTRY_OFF_OUTCOME = 49L;
    private static final long ENTRY_OFF_QUALITY = 52L;
    private static final long ENTRY_OFF_NARRATIVE_LEN = 56L;
    private static final long ENTRY_OFF_NARRATIVE_BYTES = 60L;

    private final ReentrantLock lock = new ReentrantLock();
    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    private final int maxTextBytes;
    private final long entryStride;

    private long currentCount = 0;

    public DreamJournalMemory(Path filePath, int capacity, int maxTextBytes) throws Exception {
        this.capacity = capacity;
        this.maxTextBytes = Math.max(64, maxTextBytes);
        this.arena = Arena.ofShared();

        // Stride: header metadata (60 bytes) + narrative (maxTextBytes) + insightLen(4) + insight (maxTextBytes)
        this.entryStride = 64L + (long) this.maxTextBytes * 2L;
        long totalSize = HEADER_SIZE + ((long) capacity * entryStride);

        if (filePath != null) {
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {
                this.segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, totalSize, arena);
            }
        } else {
            this.segment = arena.allocate(totalSize, 8);
        }

        initOrLoadHeader();
    }

    public static DreamJournalMemory heap(int capacity, int maxTextBytes) {
        try {
            return new DreamJournalMemory(null, capacity, maxTextBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate in-memory DreamJournalMemory: " + e.getMessage(), e);
        }
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
            throw new IllegalStateException("Invalid magic number in DreamJournalMemory header: " + Long.toHexString(magic));
        }
    }

    public void append(DreamJournalEntry entry) {
        if (entry == null) return;
        lock.lock();
        try {
            if (currentCount >= capacity) {
                return;
            }

            long entryOffset = HEADER_SIZE + (currentCount * entryStride);

            // 1. ID
            byte[] idBytes = entry.id() != null ? entry.id().getBytes(StandardCharsets.UTF_8) : new byte[0];
            int idLen = (int) Math.min(ID_MAX_BYTES, idBytes.length);
            segment.set(ValueLayout.JAVA_INT, entryOffset + ENTRY_OFF_ID_LEN, idLen);
            for (int i = 0; i < idLen; i++) {
                segment.set(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_ID_BYTES + i, idBytes[i]);
            }

            // 2. Timestamp
            long epochMs = entry.timestamp() != null ? entry.timestamp().toEpochMilli() : System.currentTimeMillis();
            segment.set(ValueLayout.JAVA_LONG, entryOffset + ENTRY_OFF_TIMESTAMP, epochMs);

            // 3. Mode
            byte modeOrdinal = (byte) (entry.mode() != null ? entry.mode().ordinal() : 0);
            segment.set(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_MODE, modeOrdinal);

            // 4. Outcome
            byte outcomeOrdinal = (byte) (entry.triageOutcome() != null ? entry.triageOutcome().ordinal() : 0);
            segment.set(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_OUTCOME, outcomeOrdinal);

            // 5. Quality Score
            segment.set(ValueLayout.JAVA_FLOAT, entryOffset + ENTRY_OFF_QUALITY, entry.qualityScore());

            // 6. Narrative Text
            byte[] narrBytes = entry.narrativeText() != null ? entry.narrativeText().getBytes(StandardCharsets.UTF_8) : new byte[0];
            int narrLen = Math.min(maxTextBytes, narrBytes.length);
            segment.set(ValueLayout.JAVA_INT, entryOffset + ENTRY_OFF_NARRATIVE_LEN, narrLen);
            for (int i = 0; i < narrLen; i++) {
                segment.set(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_NARRATIVE_BYTES + i, narrBytes[i]);
            }

            // 7. Insight Text
            long insightLenOffset = entryOffset + ENTRY_OFF_NARRATIVE_BYTES + maxTextBytes;
            byte[] insightBytes = entry.insightText() != null ? entry.insightText().getBytes(StandardCharsets.UTF_8) : new byte[0];
            int insightLen = Math.min(maxTextBytes, insightBytes.length);
            segment.set(ValueLayout.JAVA_INT, insightLenOffset, insightLen);
            for (int i = 0; i < insightLen; i++) {
                segment.set(ValueLayout.JAVA_BYTE, insightLenOffset + 4 + i, insightBytes[i]);
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
            int toRead = (int) Math.min(Math.max(0, limit), currentCount);
            if (toRead == 0) return Collections.emptyList();

            List<DreamJournalEntry> entries = new ArrayList<>(toRead);
            long startIdx = currentCount - toRead;

            for (long idx = startIdx; idx < currentCount; idx++) {
                long entryOffset = HEADER_SIZE + (idx * entryStride);

                // ID
                int idLen = segment.get(ValueLayout.JAVA_INT, entryOffset + ENTRY_OFF_ID_LEN);
                byte[] idBytes = new byte[Math.min((int) ID_MAX_BYTES, Math.max(0, idLen))];
                for (int i = 0; i < idBytes.length; i++) {
                    idBytes[i] = segment.get(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_ID_BYTES + i);
                }
                String id = new String(idBytes, StandardCharsets.UTF_8);

                // Timestamp
                long epochMs = segment.get(ValueLayout.JAVA_LONG, entryOffset + ENTRY_OFF_TIMESTAMP);
                Instant timestamp = Instant.ofEpochMilli(epochMs);

                // Mode
                byte modeOrd = segment.get(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_MODE);
                DreamMode mode = modeOrd >= 0 && modeOrd < DreamMode.values().length ? DreamMode.values()[modeOrd] : DreamMode.REM;

                // Outcome
                byte outOrd = segment.get(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_OUTCOME);
                TriageOutcome outcome = outOrd >= 0 && outOrd < TriageOutcome.values().length ? TriageOutcome.values()[outOrd] : TriageOutcome.NOISE;

                // Quality
                float quality = segment.get(ValueLayout.JAVA_FLOAT, entryOffset + ENTRY_OFF_QUALITY);

                // Narrative
                int narrLen = segment.get(ValueLayout.JAVA_INT, entryOffset + ENTRY_OFF_NARRATIVE_LEN);
                byte[] narrBytes = new byte[Math.min(maxTextBytes, Math.max(0, narrLen))];
                for (int i = 0; i < narrBytes.length; i++) {
                    narrBytes[i] = segment.get(ValueLayout.JAVA_BYTE, entryOffset + ENTRY_OFF_NARRATIVE_BYTES + i);
                }
                String narrative = new String(narrBytes, StandardCharsets.UTF_8);

                // Insight
                long insightLenOffset = entryOffset + ENTRY_OFF_NARRATIVE_BYTES + maxTextBytes;
                int insightLen = segment.get(ValueLayout.JAVA_INT, insightLenOffset);
                byte[] insightBytes = new byte[Math.min(maxTextBytes, Math.max(0, insightLen))];
                for (int i = 0; i < insightBytes.length; i++) {
                    insightBytes[i] = segment.get(ValueLayout.JAVA_BYTE, insightLenOffset + 4 + i);
                }
                String insight = new String(insightBytes, StandardCharsets.UTF_8);

                entries.add(new DreamJournalEntry(id, timestamp, mode, outcome, quality, narrative, insight, Collections.emptyList()));
            }

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
        if (arena.scope().isAlive()) {
            arena.close();
        }
    }
}
