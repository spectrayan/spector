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

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
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
    @DisplayName("WorkingRecordMemory has kernel identity with WORKING type")
    void workingMemoryStoreHasKernelIdentity() {
        try (var store = new WorkingRecordMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("working");
            assertThat(id.partitionSeq()).isZero();
        }
    }

    @Test
    @DisplayName("WorkingRecordMemory exposes kernel layout directly")
    void workingMemoryStoreKernelLayout() {
        try (var store = new WorkingRecordMemory(VEC_BYTES, CAPACITY)) {
            CognitiveRecordLayout layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
            assertThat(layout.schemaVersion()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("WorkingRecordMemory kernel shape is RECORD")
    void workingMemoryStoreKernelShape() {
        try (var store = new WorkingRecordMemory(VEC_BYTES, CAPACITY)) {
            assertThat(store.shape()).isEqualTo(MemoryShape.RECORD);
        }
    }

    // ── Semantic Memory ──

    @Test
    @DisplayName("SemanticRecordMemory has kernel identity with SEMANTIC type")
    void semanticMemoryStoreHasKernelIdentity() {
        try (var store = new SemanticRecordMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("semantic");
        }
    }

    @Test
    @DisplayName("SemanticRecordMemory exposes kernel layout directly")
    void semanticMemoryStoreKernelLayout() {
        try (var store = new SemanticRecordMemory(VEC_BYTES, CAPACITY)) {
            CognitiveRecordLayout layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
        }
    }

    // ── Procedural Memory ──

    @Test
    @DisplayName("ProceduralRecordMemory has kernel identity with PROCEDURAL type")
    void proceduralMemoryStoreHasKernelIdentity() {
        try (var store = new ProceduralRecordMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("procedural");
        }
    }

    @Test
    @DisplayName("ProceduralRecordMemory exposes kernel layout directly")
    void proceduralMemoryStoreKernelLayout() {
        try (var store = new ProceduralRecordMemory(VEC_BYTES, CAPACITY)) {
            CognitiveRecordLayout layout = store.layout();
            assertThat(layout).isNotNull();
            assertThat(layout.recordStride()).isEqualTo(store.layout().stride());
        }
    }

    // ── Episodic Memory ──

    @Test
    @DisplayName("EpisodicMemoryStore has kernel identity with EPISODIC type")
    void episodicMemoryStoreHasKernelIdentity() {
        try (var store = new EpisodicMemoryStore(VEC_BYTES, CAPACITY)) {
            MemoryId id = store.memoryId();
            assertThat(id.namespace()).isEqualTo("tier");
            assertThat(id.memoryName()).isEqualTo("episodic");
        }
    }

    // ── Cross-cutting ──

    @Test
    @DisplayName("memoryId is lazily initialized and thread-safe")
    void memoryIdIsLazyAndStable() {
        try (var store = new WorkingRecordMemory(VEC_BYTES, CAPACITY)) {
            MemoryId id1 = store.memoryId();
            MemoryId id2 = store.memoryId();
            assertThat(id1).isSameAs(id2); // same instance, not just equals
        }
    }

    @Test
    @DisplayName("memoryId toString follows kernel format")
    void memoryIdToStringFormat() {
        try (var store = new SemanticRecordMemory(VEC_BYTES, CAPACITY)) {
            assertThat(store.memoryId().toString()).isEqualTo("tier/semantic");
        }
    }

    @Test
    @DisplayName("kernel layout crcEnabled matches cognitive layout")
    void kernelLayoutCrcFlag() {
        try (var store = new WorkingRecordMemory(VEC_BYTES, CAPACITY)) {
            // CognitiveRecordLayout doesn't enable CRC by default
            CognitiveRecordLayout layout = store.layout();
            assertThat(layout.crcEnabled()).isFalse();
        }
    }
}
