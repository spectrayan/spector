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
package com.spectrayan.spector.memory.kernel;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class FileChannelReleaseTest {

    static class TestLayout implements MemoryLayout {
        @Override
        public int schemaVersion() { return 1; }
        @Override
        public int layoutId() { return 1; }
        @Override
        public int recordStride() { return 64; }
        @Override
        public boolean crcEnabled() { return false; }
        @Override
        public String name() { return "TestLayout"; }
    }

    static class TestMemory extends AbstractMemory<TestLayout> {
        TestMemory(Path path) {
            super(MemoryId.of("test", "test"), new TestLayout(), 10, 640, path);
        }

        @Override
        public MemoryShape shape() {
            return MemoryShape.RECORD;
        }

        public FileChannel getChannelRef() throws Exception {
            Field f = AbstractMemory.class.getDeclaredField("fileChannel");
            f.setAccessible(true);
            return (FileChannel) f.get(this);
        }
        
        public void writeInt(long offset, int value) {
            segment.set(ValueLayout.JAVA_INT, dataOffset() + offset, value);
        }
        
        public int readInt(long offset) {
            return segment.get(ValueLayout.JAVA_INT, dataOffset() + offset);
        }
    }

    @Test
    void testFileChannelIsClosedAfterMmap(@TempDir Path tempDir) throws Exception {
        Path memoryFile = tempDir.resolve("test_memory.smkm");
        
        try (TestMemory memory = new TestMemory(memoryFile)) {
            // Write + read to verify the segment is valid
            memory.writeInt(0, 42);
            assertEquals(42, memory.readInt(0));
            
            // Verify fileChannel field is null
            assertNull(memory.getChannelRef(), "FileChannel should be null after mmap");
        }
    }
}
