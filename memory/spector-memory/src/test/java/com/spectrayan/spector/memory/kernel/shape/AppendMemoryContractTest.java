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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppendMemoryContractTest {

    static final class TestAppendLayout implements MemoryLayout {
        @Override public int layoutId() { return 0x54455355; }
        @Override public int schemaVersion() { return 1; }
        @Override public int recordStride() { return -1; } // Variable length
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestAppendLayout"; }
    }

    static final class TestAppendMemory extends AbstractAppendMemory<TestAppendLayout> {
        TestAppendMemory(MemoryId id, TestAppendLayout layout, long initialCapacityBytes) {
            super(id, layout, 0, initialCapacityBytes);
        }
        TestAppendMemory(MemoryId id, TestAppendLayout layout, long initialCapacityBytes, Path filePath) {
            super(id, layout, 0, initialCapacityBytes, filePath);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("appendAndReplayRoundTrip")
    void appendAndReplayRoundTrip() {
        TestAppendLayout layout = new TestAppendLayout();
        MemoryId id = MemoryId.of("test", "append");

        try (var mem = new TestAppendMemory(id, layout, 1024)) {
            assertThat(mem.shape()).isEqualTo(MemoryShape.APPEND);

            byte[] text1 = "Hello Kernel".getBytes(StandardCharsets.UTF_8);
            byte[] text2 = "Spector SMK".getBytes(StandardCharsets.UTF_8);

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg1 = arena.allocate(text1.length);
                MemorySegment.copy(MemorySegment.ofArray(text1), 0, seg1, 0, text1.length);

                MemorySegment seg2 = arena.allocate(text2.length);
                MemorySegment.copy(MemorySegment.ofArray(text2), 0, seg2, 0, text2.length);

                long offset1 = mem.append(seg1);
                long offset2 = mem.append(seg2);

                assertThat(offset1).isEqualTo(4L);
                assertThat(offset2).isEqualTo(4L + text1.length + 4L);
                assertThat(mem.appendCursor()).isEqualTo(4L + text1.length + 4L + text2.length);

                var iterator = mem.replay(0);
                assertThat(iterator.hasNext()).isTrue();

                MemorySegment entry1 = iterator.next();
                byte[] readBytes1 = new byte[(int) entry1.byteSize()];
                MemorySegment.copy(entry1, ValueLayout.JAVA_BYTE, 0, readBytes1, 0, readBytes1.length);
                assertThat(new String(readBytes1, StandardCharsets.UTF_8)).isEqualTo("Hello Kernel");

                assertThat(iterator.hasNext()).isTrue();
                MemorySegment entry2 = iterator.next();
                byte[] readBytes2 = new byte[(int) entry2.byteSize()];
                MemorySegment.copy(entry2, ValueLayout.JAVA_BYTE, 0, readBytes2, 0, readBytes2.length);
                assertThat(new String(readBytes2, StandardCharsets.UTF_8)).isEqualTo("Spector SMK");

                assertThat(iterator.hasNext()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("persistenceHeaderValidation")
    void persistenceHeaderValidation() {
        TestAppendLayout layout = new TestAppendLayout();
        MemoryId id = MemoryId.of("test", "persistent_append");
        Path file = tempDir.resolve("test_append.dat");

        try (var mem = new TestAppendMemory(id, layout, 2048, file)) {
            assertThat(mem.isPersistent()).isTrue();
            byte[] text = "Persistent Blob".getBytes(StandardCharsets.UTF_8);

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment seg = arena.allocate(text.length);
                MemorySegment.copy(MemorySegment.ofArray(text), 0, seg, 0, text.length);
                mem.append(seg);
                mem.flush();
            }
        }

        // Re-open and verify count/data
        try (var mem2 = new TestAppendMemory(id, layout, 2048, file)) {
            assertThat(mem2.appendCursor()).isEqualTo(4L + 15L);
            var iterator = mem2.replay(0);
            assertThat(iterator.hasNext()).isTrue();
            MemorySegment entry = iterator.next();
            byte[] readBytes = new byte[(int) entry.byteSize()];
            MemorySegment.copy(entry, ValueLayout.JAVA_BYTE, 0, readBytes, 0, readBytes.length);
            assertThat(new String(readBytes, StandardCharsets.UTF_8)).isEqualTo("Persistent Blob");
        }
    }
}
