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
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.store.ProceduralMemory;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.engram.EncodingHeader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.layout.WorkingLayout;
import com.spectrayan.spector.kernel.layout.SemanticLayout;
import com.spectrayan.spector.kernel.layout.ProceduralLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests file-backed persistence for Working, Semantic, and Procedural
 * memory tier stores, plus MemoryIndex save/load round-trip.
 */
class MemoryPersistenceTest {

    private static final int VEC_BYTES = 32;
    private static final int CAPACITY = 50;

    @TempDir
    Path tmpDir;

    private EncodingHeader createHeader(long timestamp, float importance) {
        return EncodingHeader.create(timestamp, 0xCAFEL, 1.0f, importance, (short) 0, MemoryType.WORKING);
    }

    private byte[] dummyVec(int len, byte fill) {
        byte[] vec = new byte[len];
        java.util.Arrays.fill(vec, fill);
        return vec;
    }

    private static MemorySegment mapSlice(Path file, long size, Arena arena) throws IOException {
        try (var fc = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            if (fc.size() < size) {
                fc.position(size - 1);
                fc.write(ByteBuffer.wrap(new byte[]{0}));
            }
            return fc.map(FileChannel.MapMode.READ_WRITE, 0, size, arena);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // WORKING MEMORY STORE — round-trip persistence
    // ══════════════════════════════════════════════════════════════

    @Test
    void workingStore_persistsAndRecoversCircularBuffer() throws Exception {
        Path file = tmpDir.resolve("working.mem");
        long size = RegionPreamble.PREAMBLE_BYTES + (long) CAPACITY * new WorkingLayout(VEC_BYTES).stride();

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = WorkingMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, true)) {
                for (int i = 0; i < 5; i++) {
                    store.put(createHeader(1000L + i, 0.5f + i * 0.1f), dummyVec(VEC_BYTES, (byte) (i + 1)));
                }
                assertThat(store.size()).isEqualTo(5);
                assertThat(store.isPersistent()).isTrue();
            }
        }

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = WorkingMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, false)) {
                assertThat(store.size()).isEqualTo(5);

                store.put(createHeader(2000L, 0.9f), dummyVec(VEC_BYTES, (byte) 99));
                store.put(createHeader(2001L, 0.95f), dummyVec(VEC_BYTES, (byte) 100));
                assertThat(store.size()).isEqualTo(7);
            }
        }

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = WorkingMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, false)) {
                assertThat(store.size()).isEqualTo(7);
            }
        }
    }

    @Test
    void workingStore_circularBufferWraparound_survivesPersistence() throws Exception {
        Path file = tmpDir.resolve("working_wrap.mem");
        int smallCap = 5;
        long size = RegionPreamble.PREAMBLE_BYTES + (long) smallCap * new WorkingLayout(VEC_BYTES).stride();

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = WorkingMemory.fromBundle(arena, slice, smallCap, VEC_BYTES, file, true)) {
                for (int i = 0; i < 8; i++) {
                    store.put(createHeader(1000L + i, 0.5f), dummyVec(VEC_BYTES, (byte) i));
                }
                assertThat(store.size()).isEqualTo(smallCap);
            }
        }

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = WorkingMemory.fromBundle(arena, slice, smallCap, VEC_BYTES, file, false)) {
                assertThat(store.size()).isEqualTo(smallCap);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SEMANTIC MEMORY STORE — round-trip persistence
    // ══════════════════════════════════════════════════════════════

    @Test
    void semanticStore_persistsAndRecoversHeaders() throws Exception {
        Path file = tmpDir.resolve("semantic.mem");
        long size = RegionPreamble.PREAMBLE_BYTES + (long) CAPACITY * new SemanticLayout(VEC_BYTES).stride();

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = SemanticMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, true)) {
                for (int i = 0; i < 3; i++) {
                    var header = EncodingHeader.create(
                            System.currentTimeMillis(), 0xBEEFL, 1.0f, 0.7f + i * 0.1f, (short) i, MemoryType.SEMANTIC);
                    store.store(header);
                }
                assertThat(store.size()).isEqualTo(3);
            }
        }

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = SemanticMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, false)) {
                assertThat(store.size()).isEqualTo(3);
                var h0 = store.readHeader(0);
                assertThat(h0.importance()).isCloseTo(0.7f, org.assertj.core.data.Offset.offset(0.01f));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PROCEDURAL MEMORY STORE — round-trip persistence
    // ══════════════════════════════════════════════════════════════

    @Test
    void proceduralStore_persistsAndRecoversRecords() throws Exception {
        Path file = tmpDir.resolve("procedural.mem");
        long size = RegionPreamble.PREAMBLE_BYTES + (long) CAPACITY * new ProceduralLayout(VEC_BYTES).stride();

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = ProceduralMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, true)) {
                for (int i = 0; i < 4; i++) {
                    store.append(createHeader(3000L + i, 1.0f), dummyVec(VEC_BYTES, (byte) (i + 10)));
                }
                assertThat(store.size()).isEqualTo(4);
            }
        }

        try (var arena = Arena.ofShared()) {
            var slice = mapSlice(file, size, arena);
            try (var store = ProceduralMemory.fromBundle(arena, slice, CAPACITY, VEC_BYTES, file, false)) {
                assertThat(store.size()).isEqualTo(4);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MEMORY INDEX — save / load round-trip
    // ══════════════════════════════════════════════════════════════

    @Test
    void memoryIndex_saveAndLoad_preservesAllMaps() {
        Path file = tmpDir.resolve("memory-index.mem");

        MemoryIndex original = new MemoryIndex();

        // Register 3 entries with different types and tags
        original.register("mem-1",
                new MemoryLocation(MemoryType.EPISODIC, 64L, 0),
                "The cat sat on the mat", MemorySource.OBSERVED, new String[]{"animal", "location"});

        original.register("mem-2",
                new MemoryLocation(MemoryType.SEMANTIC, 128L, -1),
                "Java 25 supports Panama FFM API", MemorySource.USER_STATED, new String[]{"java", "panama"});

        original.register("mem-3",
                new MemoryLocation(MemoryType.PROCEDURAL, 0L, -1),
                "Use ScalarQuantizer for 8-bit encoding", MemorySource.PROCEDURAL, new String[]{});

        // Save
        original.save(file);

        // Load
        MemoryIndex loaded = MemoryIndex.load(file);

        // Verify sizes
        assertThat(loaded.size()).isEqualTo(3);

        // Verify forward index
        assertThat(loaded.locate("mem-1")).isNotNull();
        assertThat(loaded.locate("mem-1").type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(loaded.locate("mem-1").offset()).isEqualTo(64L);
        assertThat(loaded.locate("mem-1").graphSlot()).isEqualTo(0);
        assertThat(loaded.text("mem-1")).isEqualTo("The cat sat on the mat");
        assertThat(loaded.source("mem-1")).isEqualTo(MemorySource.OBSERVED);
        assertThat(loaded.tags("mem-1")).containsExactly("animal", "location");

        assertThat(loaded.locate("mem-2").type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(loaded.text("mem-2")).isEqualTo("Java 25 supports Panama FFM API");
        assertThat(loaded.source("mem-2")).isEqualTo(MemorySource.USER_STATED);

        assertThat(loaded.text("mem-3")).isEqualTo("Use ScalarQuantizer for 8-bit encoding");
        assertThat(loaded.tags("mem-3")).isEmpty();

        // Verify reverse index
        assertThat(loaded.findIdByOffset(MemoryType.EPISODIC, 64L)).isEqualTo("mem-1");
        assertThat(loaded.findIdByOffset(MemoryType.SEMANTIC, 128L)).isEqualTo("mem-2");
        assertThat(loaded.findIdByOffset(MemoryType.PROCEDURAL, 0L)).isEqualTo("mem-3");
    }

    @Test
    void memoryIndex_load_missingFile_returnsEmpty() {
        MemoryIndex loaded = MemoryIndex.load(tmpDir.resolve("nonexistent.mem"));
        assertThat(loaded.size()).isEqualTo(0);
    }

    @Test
    void memoryIndex_load_nullPath_returnsEmpty() {
        MemoryIndex loaded = MemoryIndex.load(null);
        assertThat(loaded.size()).isEqualTo(0);
    }
}
