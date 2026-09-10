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
package com.spectrayan.spector.kernel.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.WorkingLayout;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticDeduplicatorTest {

    private void writeIdentityVector(MemorySegment seg, long vecOffset, float[] vector) {
        float min = -1.0f;
        float scale = 2.0f / 255.0f;
        for (int d = 0; d < vector.length; d++) {
            int q = Math.round((vector[d] - min) / scale);
            q = Math.max(0, Math.min(255, q));
            seg.set(ValueLayout.JAVA_BYTE, vecOffset + d, (byte) q);
        }
    }

    @Test
    void testFindDuplicateNearAndFar() {
        SemanticDeduplicator dedup = new SemanticDeduplicator(0.1f);
        WorkingLayout layout = new WorkingLayout(8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(layout.stride() * 2L);

            float[] vec0 = new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
            long off0 = 0L;
            writeIdentityVector(seg, layout.vectorOffset(off0), vec0);
            seg.set(EncodingHeaderFields.LAYOUT_FLAGS, off0 + EncodingHeaderFields.OFFSET_FLAGS, (byte) 0);

            float[] nearVec = new float[]{0.51f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f, 0.50f};
            Optional<Integer> dup = dedup.findDuplicate(nearVec, seg, 1, layout);
            assertThat(dup).isPresent().contains(0);

            float[] farVec = new float[]{0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f};
            Optional<Integer> noDup = dedup.findDuplicate(farVec, seg, 1, layout);
            assertThat(noDup).isEmpty();
        }
    }

    @Test
    void testSkipsTombstonedRecords() {
        SemanticDeduplicator dedup = new SemanticDeduplicator(0.5f);
        WorkingLayout layout = new WorkingLayout(4);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(layout.stride());

            float[] vec = new float[]{0.1f, 0.2f, 0.3f, 0.4f};
            writeIdentityVector(seg, layout.vectorOffset(0L), vec);

            byte tombstonedFlags = EncodingHeaderFields.FLAG_TOMBSTONE;
            seg.set(EncodingHeaderFields.LAYOUT_FLAGS, EncodingHeaderFields.OFFSET_FLAGS, tombstonedFlags);

            Optional<Integer> result = dedup.findDuplicate(vec, seg, 1, layout);
            assertThat(result).isEmpty();
        }
    }

    @Test
    void testMergeUpdatesTimestampImportanceAndTags() {
        SemanticDeduplicator dedup = new SemanticDeduplicator();
        WorkingLayout layout = new WorkingLayout(4);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(layout.stride());

            layout.writeTimestamp(seg, 0L, 100L);
            layout.writeImportance(seg, 0L, 0.4f);
            layout.mergeSynapticTags(seg, 0L, 0x0001L);

            EncodingHeader newHeader = EncodingHeader.create(
                    200L, 0x0002L, 1.0f, 0.8f, (short) 0, MemoryType.EPISODIC);

            dedup.merge(seg, 0L, layout, newHeader);

            assertThat(layout.readTimestamp(seg, 0L)).isEqualTo(200L);
            assertThat(layout.readImportance(seg, 0L)).isEqualTo(0.8f);
            assertThat(layout.readSynapticTags(seg, 0L)).isEqualTo(0x0003L);
        }
    }
}
