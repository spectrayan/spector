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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.kernel.id.SystemMemoryId;

import com.spectrayan.spector.memory.kernel.id.MemoryId;

import com.spectrayan.spector.memory.kernel.region.RegionSizeSpec;

import com.spectrayan.spector.memory.kernel.region.RegionId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance coverage for {@link EntityDirectory}: identity, adjacency, {@code fanFactor},
 * and {@code .edir} save/load round-trip.
 */
class EntityDirectoryTest {

    @Test
    @DisplayName("fanFactor calculates degree-derived factor")
    void fanFactor_calculation() {
        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        try (EntityDirectory dir = new EntityDirectory(64, reg)) {
            int alice = dir.intern("Alice", "PERSON");
            assertThat(dir.fanFactor(alice)).isEqualTo(1.0f);

            dir.linkEntityToMemory(alice, 0);
            assertThat(dir.fanFactor(alice)).isEqualTo(1.0f);

            dir.linkEntityToMemory(alice, 1);
            assertThat(dir.fanFactor(alice)).isEqualTo(1.0f / (float) Math.sqrt(2));
        }
    }

    @Test
    @DisplayName("entity-directory.edir round-trips identity + adjacency through save/load")
    void edir_saveLoadRoundTrip(@TempDir Path tmp) throws Exception {
        Path edir = tmp.resolve("runtime").resolve("entity-directory.edir");
        Files.createDirectories(edir.getParent());

        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        int aliceId;
        int soloId;
        int savedCount;
        try (EntityDirectory dir = new EntityDirectory(edir, 64, reg)) {
            aliceId = dir.intern("Alice", "PERSON");
            int bobId = dir.intern("Bob", "PERSON");
            int alphaId = dir.intern("Project Alpha", "PROJECT");
            soloId = dir.intern("Solo", "CONCEPT");

            dir.linkEntityToMemory(aliceId, 0);
            dir.linkEntityToMemory(bobId, 0);
            dir.linkEntityToMemory(alphaId, 0);
            dir.linkEntityToMemory(aliceId, 1);
            dir.linkEntityToMemory(alphaId, 1);
            dir.linkEntityToMemory(soloId, 2);

            savedCount = dir.entityCount();
            dir.save(edir);
        }

        assertThat(Files.exists(edir)).isTrue();

        // Reload from the .edir container + sidecar and assert logical equality.
        TypeRegistryMemory reg2 = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        try (EntityDirectory reloaded = EntityDirectory.load(edir, 64, reg2)) {
            assertThat(reloaded.entityCount()).isEqualTo(savedCount);
            assertThat(reloaded.findEntity("Alice")).isEqualTo(aliceId);
            assertThat(reloaded.findEntity("Solo")).isEqualTo(soloId);
            assertThat(reloaded.memoriesForEntity(soloId)).containsExactly(2);

            int[] aliceMems = reloaded.memoriesForEntity(aliceId);
            Arrays.sort(aliceMems);
            assertThat(aliceMems).containsExactly(0, 1);
        }
    }

    @Test
    @DisplayName("intern allocates a dense id space and dedups by normalized name")
    void intern_denseIdsAndDedup() {
        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        EntityDirectory dir = new EntityDirectory(16, reg);
        try {
            int a = dir.intern("Kubernetes", "TECHNOLOGY");
            int b = dir.intern("Docker", "TECHNOLOGY");
            int aAgain = dir.intern("kubernetes", "TECHNOLOGY"); // case-insensitive dedup
            assertThat(a).isEqualTo(0);
            assertThat(b).isEqualTo(1);
            assertThat(aAgain).isEqualTo(a);
            assertThat(dir.entityCount()).isEqualTo(2);

            dir.linkEntityToMemory(a, 5);
            dir.linkEntityToMemory(a, 7);
            assertThat(dir.memoryRefCount(a)).isEqualTo(2);
            assertThat(dir.fanFactor(a)).isEqualTo(1.0f / (float) Math.sqrt(2));
        } finally {
            dir.close();
        }
    }

    @Test
    @DisplayName("fromBundle gracefully clamps capacity when reopened with larger requested capacity than physical region")
    void fromBundle_gracefullyClampsToPhysicalRegion(@TempDir Path tmp) {
        Path bundlePath = tmp.resolve("runtime.bundle");
        java.util.List<com.spectrayan.spector.memory.kernel.region.RegionSizeSpec> specs = java.util.List.of(
                new com.spectrayan.spector.memory.kernel.region.RegionSizeSpec(
                        com.spectrayan.spector.memory.kernel.region.RegionId.ENTITY_DIRECTORY,
                        8192, 100, 64, 0x45444952, 1, false),
                new com.spectrayan.spector.memory.kernel.region.RegionSizeSpec(
                        com.spectrayan.spector.memory.kernel.region.RegionId.ENTITY_NAMES,
                        16384, 1, 8, 0x45444952, 1, true)
        );

        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);

        // First pass: create the bundle with 100 entity capacity
        try (com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle bundle =
                     com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle.Init.mmap(bundlePath, specs)) {
            EntityDirectory dir = bundle.openEntityDirectory(100, reg);
            dir.intern("Alice", "PERSON");
            dir.intern("Bob", "PERSON");
            assertThat(dir.entityCount()).isEqualTo(2);
            dir.save(bundlePath);
            dir.close();
        }

        // Second pass: reopen the bundle but request 50,000 capacity (as if upgraded in properties)
        try (com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle reopened =
                     com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle.Init.open(bundlePath)) {
            EntityDirectory dir = reopened.openEntityDirectory(50_000, reg);
            assertThat(dir.entityCount()).isEqualTo(2);
            assertThat(dir.findEntity("Alice")).isEqualTo(0);
            assertThat(dir.findEntity("Bob")).isEqualTo(1);
            assertThat(dir.capacity()).isLessThanOrEqualTo((int) ((8192 - com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.DATA_START) / com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENTITY_NODE_BYTES));
            dir.close();
        }
    }

    @Test
    @DisplayName("fromBundle links and rebuilds reverse index correctly across reopen")
    void fromBundle_linksAndRebuildsReverseIndexCorrectly(@TempDir Path tmp) {
        Path bundlePath = tmp.resolve("runtime.bundle");
        java.util.List<com.spectrayan.spector.memory.kernel.region.RegionSizeSpec> specs = java.util.List.of(
                new com.spectrayan.spector.memory.kernel.region.RegionSizeSpec(
                        com.spectrayan.spector.memory.kernel.region.RegionId.ENTITY_DIRECTORY,
                        8192, 100, 64, 0x45444952, 1, false),
                new com.spectrayan.spector.memory.kernel.region.RegionSizeSpec(
                        com.spectrayan.spector.memory.kernel.region.RegionId.ENTITY_NAMES,
                        16384, 1, 8, 0x45444952, 1, true)
        );

        TypeRegistryMemory reg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.id.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);

        // First pass: intern and link
        try (com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle bundle =
                     com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle.Init.mmap(bundlePath, specs)) {
            EntityDirectory dir = bundle.openEntityDirectory(100, reg);
            int e1 = dir.intern("Quantum Engine", "PROJECT");
            int e2 = dir.intern("DeepMind", "ORGANIZATION");

            dir.linkEntityToMemory(e1, 42);
            dir.linkEntityToMemory(e2, 42);
            dir.linkEntityToMemory(e1, 99);

            assertThat(dir.entitiesForMemory(42)).containsEntry(e1, "quantum engine");
            assertThat(dir.entitiesForMemory(42)).containsEntry(e2, "deepmind");
            assertThat(dir.memoriesForEntity(e1)).contains(42, 99);

            dir.save(bundlePath);
            dir.close();
        }

        // Second pass: reopen from existing bundle and verify reverse index is restored
        try (com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle reopened =
                     com.spectrayan.spector.memory.kernel.bundle.RuntimeBundle.Init.open(bundlePath)) {
            EntityDirectory dir = reopened.openEntityDirectory(100, reg);
            assertThat(dir.entityCount()).isEqualTo(2);

            int e1 = dir.findEntity("quantum engine");
            int e2 = dir.findEntity("deepmind");
            assertThat(e1).isGreaterThanOrEqualTo(0);
            assertThat(e2).isGreaterThanOrEqualTo(0);

            // Verify entitiesForMemory works via rebuildReverseIndex
            var mem42Entities = dir.entitiesForMemory(42);
            assertThat(mem42Entities).containsEntry(e1, "quantum engine");
            assertThat(mem42Entities).containsEntry(e2, "deepmind");

            var mem99Entities = dir.entitiesForMemory(99);
            assertThat(mem99Entities).containsEntry(e1, "quantum engine");

            assertThat(dir.memoriesForEntity(e1)).contains(42, 99);
            assertThat(dir.memoriesForEntity(e2)).contains(42);

            dir.close();
        }
    }
}
