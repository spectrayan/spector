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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 (#455) acceptance coverage for {@link EntityDirectory}: derive-from-legacy parity (including
 * single-entity adjacency), {@code fanFactor} parity vs the legacy graph, and {@code .edir}
 * save/load round-trip.
 */
class EntityDirectoryTest {

    /** Builds a small legacy entity graph with both single-entity and multi-entity memories. */
    private static EntityGraphMemory buildLegacyGraph() {
        EntityGraphMemory eg = new EntityGraphMemory(64, 512, 32, EdgeImportance.DEFAULT);
        int alice = eg.addEntity("Alice", "PERSON");
        int bob = eg.addEntity("Bob", "PERSON");
        int alpha = eg.addEntity("Project Alpha", "PROJECT");
        int solo = eg.addEntity("Solo", "CONCEPT"); // single-entity memory only — no hyperedge

        // memory 0: Alice + Bob + Alpha (multi-entity)
        eg.linkEntityToMemory(alice, 0);
        eg.linkEntityToMemory(bob, 0);
        eg.linkEntityToMemory(alpha, 0);
        // memory 1: Alice + Alpha
        eg.linkEntityToMemory(alice, 1);
        eg.linkEntityToMemory(alpha, 1);
        // memory 2: Solo only (single-entity — the case that has no hyperedge)
        eg.linkEntityToMemory(solo, 2);
        // reinforce Alice→memory 0 (LTP)
        eg.linkEntityToMemory(alice, 0);
        return eg;
    }

    @Test
    @DisplayName("deriveFrom preserves identity + single-entity adjacency with exact id alignment")
    void deriveFrom_preservesIdentityAndSingleEntityAdjacency() {
        try (EntityGraphMemory eg = buildLegacyGraph()) {
            EntityDirectory dir = new EntityDirectory(64, eg.entityTypeRegistry());
            dir.deriveFrom(eg);

            assertThat(dir.entityCount()).isEqualTo(eg.entityCount());

            // Name→id alignment must match exactly (hyperedge vertex ids depend on it).
            for (Map.Entry<String, Integer> e : eg.nameIndex().entrySet()) {
                assertThat(dir.findEntity(e.getKey()))
                        .as("id alignment for '%s'", e.getKey())
                        .isEqualTo(e.getValue());
            }

            for (int id = 0; id < eg.entityCount(); id++) {
                assertThat(dir.entityType(id)).as("type[%d]", id).isEqualTo(eg.entityType(id));
                int[] dirMems = dir.memoriesForEntity(id);
                int[] egMems = eg.memoriesForEntity(id);
                Arrays.sort(dirMems);
                Arrays.sort(egMems);
                assertThat(dirMems).as("memoriesForEntity[%d]", id).isEqualTo(egMems);
            }

            // Single-entity memory (Solo → memory 2) survives in the directory.
            int solo = dir.findEntity("Solo");
            assertThat(solo).isGreaterThanOrEqualTo(0);
            assertThat(dir.memoriesForEntity(solo)).containsExactly(2);

            dir.close();
        }
    }

    @Test
    @DisplayName("fanFactor is byte-identical to the legacy graph after derive")
    void fanFactor_parityWithLegacy() {
        try (EntityGraphMemory eg = buildLegacyGraph()) {
            EntityDirectory dir = new EntityDirectory(64, eg.entityTypeRegistry());
            dir.deriveFrom(eg);
            for (int id = 0; id < eg.entityCount(); id++) {
                assertThat(dir.fanFactor(id)).as("fanFactor[%d]", id).isEqualTo(eg.fanFactor(id));
            }
            dir.close();
        }
    }

    @Test
    @DisplayName("entity-directory.edir round-trips identity + adjacency through save/load")
    void edir_saveLoadRoundTrip(@TempDir Path tmp) throws Exception {
        Path edir = tmp.resolve("runtime").resolve("entity-directory.edir");
        Files.createDirectories(edir.getParent());

        TypeRegistryMemory reg = TypeRegistryMemory.seeded("entity-type", EntityType.SEED);
        int aliceId;
        int soloId;
        int savedCount;
        try (EntityGraphMemory eg = buildLegacyGraph()) {
            EntityDirectory dir = new EntityDirectory(edir, 64, reg);
            dir.deriveFrom(eg);
            aliceId = dir.findEntity("Alice");
            soloId = dir.findEntity("Solo");
            savedCount = dir.entityCount();
            dir.save(edir);
            dir.close();
        }

        assertThat(Files.exists(edir)).isTrue();

        // Reload from the .edir container + sidecar and assert logical equality.
        TypeRegistryMemory reg2 = TypeRegistryMemory.seeded("entity-type", EntityType.SEED);
        EntityDirectory reloaded = EntityDirectory.load(edir, 64, reg2);
        try {
            assertThat(reloaded.entityCount()).isEqualTo(savedCount);
            assertThat(reloaded.findEntity("Alice")).isEqualTo(aliceId);
            assertThat(reloaded.findEntity("Solo")).isEqualTo(soloId);
            assertThat(reloaded.memoriesForEntity(soloId)).containsExactly(2);

            int[] aliceMems = reloaded.memoriesForEntity(aliceId);
            Arrays.sort(aliceMems);
            assertThat(aliceMems).containsExactly(0, 1);
            assertThat(reloaded.entityType(aliceId)).isEqualTo("PERSON");
        } finally {
            reloaded.close();
        }
    }

    @Test
    @DisplayName("intern allocates a dense id space and dedups by normalized name")
    void intern_denseIdsAndDedup() {
        TypeRegistryMemory reg = TypeRegistryMemory.seeded("entity-type", EntityType.SEED);
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
}
