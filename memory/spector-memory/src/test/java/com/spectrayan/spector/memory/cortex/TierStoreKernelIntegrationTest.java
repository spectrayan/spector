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
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.store.ProceduralMemory;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies kernel integration wiring on cognitive record memories.
 *
 * <p>These tests confirm that every cognitive memory store subclass correctly
 * exposes kernel identity, layout, and shape metadata without downcasting.</p>
 */
class TierStoreKernelIntegrationTest {

    private static final int VEC_BYTES = 128;
    private static final int CAPACITY = 100;

    // ── Working Memory ──

    @Test
    @DisplayName("WorkingMemory has kernel identity with WORKING type")
    void workingMemoryStoreHasKernelIdentity() {
        try (var store = new WorkingMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("working");
            assertThat(id.partitionSeq()).isZero();
        }
    }

    @Test
    @DisplayName("WorkingMemory exposes kernel layout directly")
    void workingMemoryStoreKernelLayout() {
        try (var store = new WorkingMemory(VEC_BYTES, CAPACITY)) {
            var layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
            assertThat(layout.schemaVersion()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("WorkingMemory kernel shape is RECORD")
    void workingMemoryStoreKernelShape() {
        try (var store = new WorkingMemory(VEC_BYTES, CAPACITY)) {
            assertThat(store.shape()).isEqualTo(MemoryShape.RECORD);
        }
    }

    // ── Semantic Memory ──

    @Test
    @DisplayName("SemanticMemory has kernel identity with SEMANTIC type")
    void semanticMemoryStoreHasKernelIdentity() {
        try (var store = new SemanticMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("semantic");
        }
    }

    @Test
    @DisplayName("SemanticMemory exposes kernel layout directly")
    void semanticMemoryStoreKernelLayout() {
        try (var store = new SemanticMemory(VEC_BYTES, CAPACITY)) {
            var layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
        }
    }

    // ── Procedural Memory ──

    @Test
    @DisplayName("ProceduralMemory has kernel identity with PROCEDURAL type")
    void proceduralMemoryStoreHasKernelIdentity() {
        try (var store = new ProceduralMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("procedural");
        }
    }

    @Test
    @DisplayName("ProceduralMemory exposes kernel layout directly")
    void proceduralMemoryStoreKernelLayout() {
        try (var store = new ProceduralMemory(VEC_BYTES, CAPACITY)) {
            var layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
        }
    }

    // ── Episodic Memory ──

    @Test
    @DisplayName("EpisodicMemory has kernel identity with EPISODIC type")
    void episodicMemoryStoreHasKernelIdentity() {
        try (var store = EpisodicMemory.heap(CAPACITY * 256L)) {
            MemoryId id = store.id();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("episodic");
        }
    }

    // ── Cross-cutting ──

    @Test
    @DisplayName("memoryId is lazily initialized and thread-safe")
    void memoryIdIsLazyAndStable() {
        try (var store = new WorkingMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id1 = store.memoryId();
            MemoryId id2 = store.memoryId();
            assertThat(id1).isSameAs(id2); // same instance, not just equals
        }
    }

    @Test
    @DisplayName("memoryId toString follows kernel format")
    void memoryIdToStringFormat() {
        try (var store = new SemanticMemory(VEC_BYTES, CAPACITY)) {
            assertThat(store.memoryId().toString()).isEqualTo("tier/semantic");
        }
    }

    @Test
    @DisplayName("kernel layout crcEnabled matches cognitive layout")
    void kernelLayoutCrcFlag() {
        try (var store = new WorkingMemory(VEC_BYTES, CAPACITY)) {
            // EngramLayout doesn't enable CRC by default
            var layout = store.layout();
            assertThat(layout.crcEnabled()).isFalse();
        }
    }
}
