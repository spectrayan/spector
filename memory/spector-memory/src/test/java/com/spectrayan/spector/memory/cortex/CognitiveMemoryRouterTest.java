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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveMemoryRouterTest {

    @Test
    @DisplayName("get() returns correct store for all MemoryTypes including EPISODIC")
    void getReturnsCorrectStoreForEachType() {
        // CognitiveMemoryRouter.close() cascades to child stores, so only close the router
        WorkingMemory working = new WorkingMemory(128, 100);
        SemanticMemory semantic = new SemanticMemory(128, 100);
        ProceduralMemory procedural = new ProceduralMemory(128, 100);
        EpisodicMemory episodic = EpisodicMemory.heap(1024 * 1024L);

        try (CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodic)) {
            assertThat(router.get(MemoryType.WORKING)).isSameAs(working);
            assertThat(router.get(MemoryType.SEMANTIC)).isSameAs(semantic);
            assertThat(router.get(MemoryType.PROCEDURAL)).isSameAs(procedural);
            assertThat(router.get(MemoryType.EPISODIC)).isSameAs(episodic);
        }
    }

    @Test
    @DisplayName("Typed accessors return correct instances")
    void typedAccessorsReturnCorrectInstances() {
        WorkingMemory working = new WorkingMemory(128, 100);
        SemanticMemory semantic = new SemanticMemory(128, 100);
        ProceduralMemory procedural = new ProceduralMemory(128, 100);
        EpisodicMemory episodic = EpisodicMemory.heap(1024 * 1024L);

        try (CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodic)) {
            assertThat(router.working()).isSameAs(working);
            assertThat(router.episodic()).isSameAs(episodic);
            assertThat(router.semantic()).isSameAs(semantic);
            assertThat(router.procedural()).isSameAs(procedural);
        }
    }

    @Test
    @DisplayName("totalCount sums all stores")
    void totalCountSumsAllStores() {
        WorkingMemory working = new WorkingMemory(128, 100);
        SemanticMemory semantic = new SemanticMemory(128, 100);
        ProceduralMemory procedural = new ProceduralMemory(128, 100);
        EpisodicMemory episodic = EpisodicMemory.heap(1024 * 1024L);

        try (CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodic)) {
            assertThat(router.totalCount()).isZero();
        }
    }

    @Test
    @DisplayName("shouldScan with null target scans all")
    void shouldScanNullTargetScansAll() {
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, null)).isTrue();
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.WORKING, null)).isTrue();
    }

    @Test
    @DisplayName("shouldScan for specific type")
    void shouldScanSpecificType() {
        MemoryType[] targets = { MemoryType.SEMANTIC };
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, targets)).isTrue();
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.WORKING, targets)).isFalse();
    }

    @Test
    @DisplayName("shouldScan with empty target scans all")
    void shouldScanEmptyTargetScansAll() {
        MemoryType[] targets = new MemoryType[0];
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, targets)).isTrue();
        assertThat(CognitiveMemoryRouter.shouldScan(MemoryType.WORKING, targets)).isTrue();
    }

    @Test
    @DisplayName("close() closes all stores without throwing")
    void closeClosesAllStores() {
        WorkingMemory working = new WorkingMemory(128, 100);
        SemanticMemory semantic = new SemanticMemory(128, 100);
        ProceduralMemory procedural = new ProceduralMemory(128, 100);
        EpisodicMemory episodic = EpisodicMemory.heap(1024 * 1024L);
        
        CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodic);
        router.close();
    }

    @Test
    @DisplayName("write with MemoryType.EPISODIC throws ARGUMENT_INVALID")
    void writeEpisodicThrowsValidationException() {
        WorkingMemory working = new WorkingMemory(128, 100);
        SemanticMemory semantic = new SemanticMemory(128, 100);
        ProceduralMemory procedural = new ProceduralMemory(128, 100);
        EpisodicMemory episodic = EpisodicMemory.heap(1024 * 1024L);

        try (CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodic)) {
            var header = EncodingHeader.create(System.currentTimeMillis(), 0L, 1.0f, 0.5f, (short) 0, MemoryType.EPISODIC);
            byte[] quantized = new byte[128];
            assertThatThrownBy(() -> router.write(MemoryType.EPISODIC, header, quantized))
                    .isInstanceOf(SpectorValidationException.class)
                    .satisfies(ex -> assertThat(((SpectorValidationException) ex).errorCode()).isEqualTo(ErrorCode.ARGUMENT_INVALID));
        }
    }
}
