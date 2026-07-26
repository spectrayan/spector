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

import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierRouterTest {

    @Test
    @DisplayName("get() returns correct store for each MemoryType")
    void getReturnsCorrectStoreForEachType() {
        // TierRouter.close() cascades to child stores, so only close the router
        WorkingMemoryStore working = new WorkingMemoryStore(128, 100);
        EpisodicMemoryStore episodic = new EpisodicMemoryStore(128, 100);
        SemanticMemoryStore semantic = new SemanticMemoryStore(128, 100);
        ProceduralMemoryStore procedural = new ProceduralMemoryStore(128, 100);

        try (TierRouter router = new TierRouter(working, episodic, semantic, procedural)) {
            assertThat(router.get(MemoryType.WORKING)).isSameAs(working);
            assertThat(router.get(MemoryType.EPISODIC)).isSameAs(episodic);
            assertThat(router.get(MemoryType.SEMANTIC)).isSameAs(semantic);
            assertThat(router.get(MemoryType.PROCEDURAL)).isSameAs(procedural);
        }
    }

    @Test
    @DisplayName("Typed accessors return correct instances")
    void typedAccessorsReturnCorrectInstances() {
        WorkingMemoryStore working = new WorkingMemoryStore(128, 100);
        EpisodicMemoryStore episodic = new EpisodicMemoryStore(128, 100);
        SemanticMemoryStore semantic = new SemanticMemoryStore(128, 100);
        ProceduralMemoryStore procedural = new ProceduralMemoryStore(128, 100);

        try (TierRouter router = new TierRouter(working, episodic, semantic, procedural)) {
            assertThat(router.working()).isSameAs(working);
            assertThat(router.episodic()).isSameAs(episodic);
            assertThat(router.semantic()).isSameAs(semantic);
            assertThat(router.procedural()).isSameAs(procedural);
        }
    }

    @Test
    @DisplayName("totalCount sums all tiers")
    void totalCountSumsAllTiers() {
        WorkingMemoryStore working = new WorkingMemoryStore(128, 100);
        EpisodicMemoryStore episodic = new EpisodicMemoryStore(128, 100);
        SemanticMemoryStore semantic = new SemanticMemoryStore(128, 100);
        ProceduralMemoryStore procedural = new ProceduralMemoryStore(128, 100);

        try (TierRouter router = new TierRouter(working, episodic, semantic, procedural)) {
            assertThat(router.totalCount()).isZero();
        }
    }

    @Test
    @DisplayName("shouldScan with null target scans all")
    void shouldScanNullTargetScansAll() {
        assertThat(TierRouter.shouldScan(MemoryType.SEMANTIC, null)).isTrue();
        assertThat(TierRouter.shouldScan(MemoryType.WORKING, null)).isTrue();
    }

    @Test
    @DisplayName("shouldScan for specific type")
    void shouldScanSpecificType() {
        MemoryType[] targets = { MemoryType.SEMANTIC };
        assertThat(TierRouter.shouldScan(MemoryType.SEMANTIC, targets)).isTrue();
        assertThat(TierRouter.shouldScan(MemoryType.WORKING, targets)).isFalse();
    }

    @Test
    @DisplayName("shouldScan with empty target scans all")
    void shouldScanEmptyTargetScansAll() {
        MemoryType[] targets = new MemoryType[0];
        assertThat(TierRouter.shouldScan(MemoryType.SEMANTIC, targets)).isTrue();
        assertThat(TierRouter.shouldScan(MemoryType.WORKING, targets)).isTrue();
    }

    @Test
    @DisplayName("close() closes all stores without throwing")
    void closeClosesAllStores() {
        WorkingMemoryStore working = new WorkingMemoryStore(128, 100);
        EpisodicMemoryStore episodic = new EpisodicMemoryStore(128, 100);
        SemanticMemoryStore semantic = new SemanticMemoryStore(128, 100);
        ProceduralMemoryStore procedural = new ProceduralMemoryStore(128, 100);
        
        TierRouter router = new TierRouter(working, episodic, semantic, procedural);
        router.close();
        
        // When closed, operations usually throw an exception if accessed, but closing a router should not throw.
        // We can just verify it does not throw an exception on close.
    }
}
