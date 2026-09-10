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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.store.SemanticMemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failing staleness test suite required by Task 2.6 (Req: R2.3, R2.4).
 *
 * <p>Demonstrates the two fundamental staleness hazards present prior to {@code RegionRef}
 * and {@code RegionOpener}:</p>
 * <ol>
 *   <li><b>grow-then-access</b>: Growing a runtime region unmaps the arena, causing any held
 *       segment handle to throw {@link IllegalStateException}.</li>
 *   <li><b>roll-while-held</b>: Rolling a partition leaves existing held store handles pointing
 *       to the frozen previous bundle rather than rebinding to the new active partition.</li>
 * </ol>
 */
@DisplayName("Region Staleness and Invalidation Tests (R2.3, R2.4)")
class RegionStalenessTest {

    private static final int DIMS = 8;
    private static final EngramLayout LAYOUT = new EngramLayout(DIMS);

    @Test
    @DisplayName("(a) grow-then-access: held handle reads and writes remapped slice after growRegion")
    void growThenAccessReadsRemappedSlice(@TempDir Path tempDir) {
        Path bundlePath = tempDir.resolve("runtime.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.BM25, 4096, 10, 64, 0x42494458, 1, true)
        );

        try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs)) {
            // Write initial data to BM25
            RegionRef bm25Ref = bundle.regionRef(RegionId.BM25);
            long baseOffset = RegionPreamble.PREAMBLE_BYTES;
            bm25Ref.resolve().set(ValueLayout.JAVA_LONG, baseOffset, 0x1122334455667788L);

            // Hold a memory handle created before growth
            // With RegionRef, this rebinds dynamically on growth/remap.
            RegionRef heldRef = bm25Ref;

            // Grow the runtime region
            bundle.growRegion(RegionId.BM25);

            // Assert that reads through held handle land on the remapped slice and do not crash
            long readVal = heldRef.resolve().get(ValueLayout.JAVA_LONG, baseOffset);
            assertThat(readVal).isEqualTo(0x1122334455667788L);
        }
    }

    @Test
    @DisplayName("(b) roll-while-held: active store handle does not continue reading previous bundle after partition roll")
    void rollWhileHeldDoesNotReadPreviousBundle() {
        int quantizedVecBytes = DIMS;
        int semanticCap = 10;
        int proceduralCap = 10;

        TextBlobLayout textLayout = new TextBlobLayout();

        PartitionBundle part0Bundle = PartitionBundle.Init.heap(
                semanticCap, 4096, proceduralCap, 4096, quantizedVecBytes,
                LAYOUT.layoutId(), LAYOUT.schemaVersion(),
                textLayout.layoutId(), textLayout.schemaVersion());

        PartitionBundle part1Bundle = PartitionBundle.Init.heap(
                semanticCap, 4096, proceduralCap, 4096, quantizedVecBytes,
                LAYOUT.layoutId(), LAYOUT.schemaVersion(),
                textLayout.layoutId(), textLayout.schemaVersion());

        // Open semantic store from part0
        SemanticMemory heldSemantic = part0Bundle.openSemantic(semanticCap, quantizedVecBytes);
        assertThat(heldSemantic.size()).isEqualTo(0);

        // Roll partition bundle from part0 to part1
        part0Bundle.rollTo(part1Bundle);

        // Ingest a record into the newly rolled active partition (part1)
        SemanticMemory activeSemantic = part1Bundle.openSemantic(semanticCap, quantizedVecBytes);
        EncodingHeader header = EncodingHeader.create(System.currentTimeMillis(), 0L, 1.0f, 0.5f, (short) 0, MemoryType.SEMANTIC);
        activeSemantic.append(header, new byte[quantizedVecBytes]);

        // Assert that the caller holding the active semantic handle observes the new active partition
        assertThat(heldSemantic.size())
                .as("held store handle must reflect the active partition, not continue reading previous bundle")
                .isEqualTo(1);
    }
}
