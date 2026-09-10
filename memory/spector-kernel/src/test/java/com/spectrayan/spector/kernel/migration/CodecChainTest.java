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

import com.spectrayan.spector.kernel.migration.FormatCodec;

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodecChainTest {

    static final class TestLayout implements RegionLayout {
        @Override public int layoutId() { return 0x434F4443; }
        @Override public int schemaVersion() { return 2; }
        @Override public int recordStride() { return 16; }
        @Override public boolean crcEnabled() { return false; }
        @Override public String name() { return "TestCodecLayout"; }
    }

    static final class TestCodec implements FormatCodec<TestLayout> {
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
