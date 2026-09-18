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
package com.spectrayan.spector.memory.cortex;
import com.spectrayan.spector.kernel.store.WorkingMemory;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.score.SynapticTagEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkingMemory} — volatile circular buffer.
 */
class WorkingMemoryTest {

    private static final int VEC_BYTES = 32; // small vectors for testing
    private WorkingMemory store;

    @BeforeEach
    void setUp() {
        store = new WorkingMemory(VEC_BYTES, 5); // capacity of 5 for easy testing
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void putAndSize() {
        assertThat(store.size()).isZero();

        store.put(createHeader("java"), new byte[VEC_BYTES]);
        assertThat(store.size()).isEqualTo(1);

        store.put(createHeader("python"), new byte[VEC_BYTES]);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void fifoEvictionWhenFull() {
        // Fill to capacity
        for (int i = 0; i < 5; i++) {
            store.put(createHeader("tag-" + i), new byte[VEC_BYTES]);
        }
        assertThat(store.size()).isEqualTo(5);

        // One more should evict oldest (FIFO, size stays at capacity)
        store.put(createHeader("tag-overflow"), new byte[VEC_BYTES]);
        assertThat(store.size()).isEqualTo(5); // stays at capacity
    }

    @Test
    void scanReturnsMatchingOffsets() {
        long javaTag = SynapticTagEncoder.encode("java");
        long pythonTag = SynapticTagEncoder.encode("python");

        store.put(createHeader("java"), new byte[VEC_BYTES]);
        store.put(createHeader("python"), new byte[VEC_BYTES]);
        store.put(createHeader("java", "performance"), new byte[VEC_BYTES]);

        // Scan for "java" tag
        long[] matches = store.scan(javaTag);
        assertThat(matches.length).isGreaterThanOrEqualTo(2); // at least 2 java-tagged

        // Scan with no filter (0 mask matches everything)
        long[] all = store.scan(0L);
        assertThat(all.length).isEqualTo(3);
    }

    @Test
    void scanSkipsTombstones() {
        store.put(createHeader("java"), new byte[VEC_BYTES]);
        store.put(createHeader("python"), new byte[VEC_BYTES]);

        // Tombstone the first record
        store.layout().tombstone(store.segment(), 0);

        long[] all = store.scan(0L);
        assertThat(all.length).isEqualTo(1); // only the non-tombstoned one
    }

    @Test
    void capacityIsCorrect() {
        assertThat(store.capacity()).isEqualTo(5);
    }

    private EncodingHeader createHeader(String... tags) {
        return EncodingHeader.create(
                System.currentTimeMillis(),
                SynapticTagEncoder.encode(tags),
                1.0f,
                1.0f,
                (short) 0,
                MemoryType.WORKING
        );
    }
}
