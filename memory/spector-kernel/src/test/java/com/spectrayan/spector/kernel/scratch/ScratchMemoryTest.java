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

import com.spectrayan.spector.kernel.api.KernelSpec;
import com.spectrayan.spector.kernel.api.NamespaceKernel;
import com.spectrayan.spector.kernel.api.NamespaceKernels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link ScratchMemory}, {@link TokenVectorTable}, and {@link FloatScratch} (R8.1, R8.4, R8.5).
 */
@DisplayName("Kernel ScratchMemory & TokenVectorTable Tests (R8)")
class ScratchMemoryTest {

    @Test
    @DisplayName("TokenVectorTable: put, get, update, eviction, and allocatedBytes accounting")
    void testTokenVectorTableOperations() {
        try (ScratchMemory scratch = new DefaultScratchMemory()) {
            TokenVectorTable table = scratch.tokenTable(3, 4, 2);
            assertThat(table.maxEntries()).isEqualTo(3);
            assertThat(table.maxTokens()).isEqualTo(4);
            assertThat(table.dims()).isEqualTo(2);

            float[][] doc1 = new float[][]{
                    {1.0f, 2.0f},
                    {3.0f, 4.0f}
            };
            table.put("doc-1", doc1);
            assertThat(table.size()).isEqualTo(1);
            // 2 tokens * 2 dims * 4 bytes = 16 bytes
            assertThat(table.allocatedBytes()).isEqualTo(16L);
            assertThat(scratch.allocatedBytes()).isEqualTo(16L);

            // get into allocated array
            float[][] retrieved = table.get("doc-1");
            assertThat(retrieved).isNotNull();
            assertThat(retrieved).hasDimensions(2, 2);
            assertThat(retrieved[0][0]).isCloseTo(1.0f, within(1e-6f));
            assertThat(retrieved[1][1]).isCloseTo(4.0f, within(1e-6f));

            // get into pre-allocated buffer
            float[][] dest = new float[2][2];
            boolean copied = table.get("doc-1", dest);
            assertThat(copied).isTrue();
            assertThat(dest[0][1]).isCloseTo(2.0f, within(1e-6f));

            // update existing docId replaces vectors without memory leak
            float[][] doc1Updated = new float[][]{
                    {10.0f, 20.0f},
                    {30.0f, 40.0f},
                    {50.0f, 60.0f}
            };
            table.put("doc-1", doc1Updated);
            assertThat(table.size()).isEqualTo(1);
            // 3 tokens * 2 dims * 4 bytes = 24 bytes
            assertThat(table.allocatedBytes()).isEqualTo(24L);
            assertThat(scratch.allocatedBytes()).isEqualTo(24L);

            // fill up to capacity
            table.put("doc-2", new float[][]{{1f, 1f}});
            table.put("doc-3", new float[][]{{2f, 2f}});
            assertThat(table.size()).isEqualTo(3);

            // Access doc-1 to make doc-2 oldest
            table.get("doc-1");
            table.get("doc-3");

            // inserting 4th entry evicts doc-2
            table.put("doc-4", new float[][]{{3f, 3f}});
            assertThat(table.size()).isEqualTo(3);
            assertThat(table.get("doc-2")).isNull();
            assertThat(table.get("doc-1")).isNotNull();
            assertThat(table.get("doc-4")).isNotNull();

            // clear
            table.clear();
            assertThat(table.size()).isEqualTo(0);
            assertThat(table.allocatedBytes()).isEqualTo(0L);
            assertThat(scratch.allocatedBytes()).isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("FloatScratch: get, set, fill, copyFrom, copyTo, and close accounting")
    void testFloatScratchOperations() {
        try (ScratchMemory scratch = new DefaultScratchMemory()) {
            FloatScratch floats = scratch.floats(100);
            assertThat(floats.capacity()).isEqualTo(100);
            assertThat(floats.byteSize()).isEqualTo(400L);
            assertThat(scratch.allocatedBytes()).isEqualTo(400L);

            floats.set(0, 42.5f);
            assertThat(floats.get(0)).isCloseTo(42.5f, within(1e-6f));

            floats.fill(7.0f);
            assertThat(floats.get(50)).isCloseTo(7.0f, within(1e-6f));

            float[] src = new float[]{1.1f, 2.2f, 3.3f};
            floats.copyFrom(src, 0, 10, 3);
            assertThat(floats.get(10)).isCloseTo(1.1f, within(1e-6f));
            assertThat(floats.get(11)).isCloseTo(2.2f, within(1e-6f));
            assertThat(floats.get(12)).isCloseTo(3.3f, within(1e-6f));

            float[] dst = new float[3];
            floats.copyTo(10, dst, 0, 3);
            assertThat(dst).containsExactly(1.1f, 2.2f, 3.3f);

            floats.close();
            assertThat(scratch.allocatedBytes()).isEqualTo(0L);

            assertThatThrownBy(() -> floats.get(0))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("Task 7.4 Assert release on close: NamespaceKernel.close() releases all scratch and allocatedBytes returns to zero (R8.4)")
    void testNamespaceKernelCloseReleasesScratch(@TempDir Path tempDir) {
        KernelSpec spec = KernelSpec.standard(32);
        NamespaceKernel kernel = NamespaceKernels.open(tempDir, spec);

        ScratchMemory scratch = kernel.scratch();
        assertThat(scratch).isNotNull();
        assertThat(scratch.allocatedBytes()).isEqualTo(0L);

        TokenVectorTable tokenTable = scratch.tokenTable(10, 8, 32);
        tokenTable.put("doc-test", new float[][]{new float[32], new float[32]});

        FloatScratch floatScratch = scratch.floats(256);
        floatScratch.fill(1.0f);

        long totalAllocated = scratch.allocatedBytes();
        assertThat(totalAllocated).isGreaterThan(0L);
        // tokenTable: 2 * 32 * 4 = 256 bytes; floatScratch: 256 * 4 = 1024 bytes; total = 1280
        assertThat(totalAllocated).isEqualTo(1280L);

        // Close kernel
        kernel.close();

        // After close, all scratch resources are released and allocatedBytes() returns to 0
        assertThat(scratch.allocatedBytes()).isEqualTo(0L);

        // Attempting to allocate from closed scratch throws IllegalStateException
        assertThatThrownBy(() -> scratch.floats(64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ScratchMemory has already been released");

        assertThatThrownBy(() -> scratch.tokenTable(5, 5, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ScratchMemory has already been released");
    }
}
