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
package com.spectrayan.spector.memory.kernel.codec;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodecChainTest {

    static final class TestLayout implements MemoryLayout {
        @Override public int layoutId() { return 0x434F4443; }
        @Override public int schemaVersion() { return 2; }
        @Override public int recordStride() { return 16; }
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestCodecLayout"; }
    }

    static final class TestCodec implements Codec<TestLayout> {
        private final TestLayout layout = new TestLayout();

        @Override public TestLayout layout() { return layout; }
        @Override public Set<Integer> legacyMagics() { return Set.of(0x54455354); }
        @Override public int versionOf(int magic, MemorySegment headerPrefix) { return 1; }
        @Override
        public List<CodecStep> steps() {
            return List.of(new IdentityStep(FormatId.smkm(2)));
        }
    }

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("freshFileReturnsNoHops")
    void freshFileReturnsNoHops() throws Exception {
        TestCodec codec = new TestCodec();
        Path file = tempDir.resolve("non_existent.dat");
        MigrationContext ctx = new MigrationContext(
                file, MemoryId.of("test", "codec"), codec.layout(), null, null, false, false
        );

        MigrationResult result = codec.ensureCurrent(ctx);
        assertThat(result.migrated()).isFalse();
        assertThat(result.hops()).isEmpty();
    }
}
