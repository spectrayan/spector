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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityReverseIndexAdapter Tests")
class EntityReverseIndexAdapterTest {

    @Test
    @DisplayName("Should hydrate reverse index view from forward adjacency slab and report accurate stats")
    void shouldHydrateAndReportStats() {
        TypeRegistryMemory reg = TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        try (EntityDirectory dir = new EntityDirectory(64, reg)) {
            int alice = dir.intern("Alice", "PERSON");
            int bob = dir.intern("Bob", "PERSON");

            dir.linkEntityToMemory(alice, 10);
            dir.linkEntityToMemory(alice, 20);
            dir.linkEntityToMemory(bob, 20);

            EntityReverseIndexAdapter adapter = new EntityReverseIndexAdapter(dir);
            assertThat(adapter.name()).isEqualTo(EntityReverseIndexAdapter.NAME);
            assertThat(adapter.kind()).isEqualTo(IndexKind.DERIVED_CHEAP);
            assertThat(adapter.dependsOn()).containsExactly("EntityDirectory");

            // Execute hydration
            adapter.hydrate().toCompletableFuture().join();

            // Verify reverse index view
            assertThat(dir.reverseIndexSize()).isEqualTo(2); // memory slots 10 and 20
            assertThat(dir.entityIdsForMemory(10)).containsExactly(alice);
            assertThat(dir.entityIdsForMemory(20)).containsExactlyInAnyOrder(alice, bob);

            Map<Integer, String> slot20Entities = dir.entitiesForMemory(20);
            assertThat(slot20Entities).containsEntry(alice, "alice").containsEntry(bob, "bob");

            // Verify stats and generation computation
            IndexStats stats = adapter.stats();
            assertThat(stats.entries()).isEqualTo(2);
            assertThat(stats.generation()).isGreaterThan(0L);
            assertThat(stats.heapBytes()).isGreaterThan(0L);
        }
    }
}
