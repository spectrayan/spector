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

import com.spectrayan.spector.memory.kernel.StorageLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EntityGraphMigrationCli} — the P3 migration from
 * {@code entity.graph} to {@code entity-directory.edir} (ADR-0003 #457).
 */
class EntityGraphMigrationCliTest {

    @TempDir Path tempDir;

    @Test
    void migrate_convertsLegacyEntityGraph_toEntityDirectory() throws Exception {
        // Setup: simulate a namespace with a legacy entity graph
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        // Create and populate a legacy EntityGraphMemory
        Path legacyFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY);
        EntityGraphMemory legacyGraph = new EntityGraphMemory(256, 512);
        int alice = legacyGraph.addEntity("alice", "PERSON");
        int bob = legacyGraph.addEntity("bob", "PERSON");
        int project = legacyGraph.addEntity("spector", "PROJECT");
        legacyGraph.linkEntityToMemory(alice, 0);
        legacyGraph.linkEntityToMemory(alice, 1);
        legacyGraph.linkEntityToMemory(bob, 2);
        legacyGraph.linkEntityToMemory(project, 0);
        legacyGraph.addRelation(alice, bob, "RELATED_TO");
        legacyGraph.save(legacyFile, null);
        legacyGraph.close();

        assertThat(legacyFile).exists();

        // Migrate
        var result = EntityGraphMigrationCli.migrate(tempDir);

        // Verify result
        assertThat(result.status()).isEqualTo(EntityGraphMigrationCli.MigrationResult.Status.MIGRATED);
        assertThat(result.legacyEntityCount()).isEqualTo(3);
        assertThat(result.migratedEntityCount()).isEqualTo(3);

        // Verify edir file was created
        Path edirFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY_DIRECTORY);
        assertThat(edirFile).exists();

        // Verify backup was created
        Path backupFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY + ".bak");
        assertThat(backupFile).exists();

        // Verify the migrated directory can be loaded and has correct data
        TypeRegistryMemory typeReg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        EntityDirectory loaded = EntityDirectory.load(edirFile, 256, typeReg, null);
        assertThat(loaded.entityCount()).isEqualTo(3);
        assertThat(loaded.findEntity("alice")).isEqualTo(alice);
        assertThat(loaded.findEntity("bob")).isEqualTo(bob);
        assertThat(loaded.findEntity("spector")).isEqualTo(project);

        // Verify memory refs round-tripped
        assertThat(loaded.memoryRefCount(alice)).isEqualTo(2);
        assertThat(loaded.memoryRefCount(bob)).isEqualTo(1);
        assertThat(loaded.memoryRefCount(project)).isEqualTo(1);
        assertThat(loaded.memoryRefAt(alice, 0)).isEqualTo(0);
        assertThat(loaded.memoryRefAt(alice, 1)).isEqualTo(1);
        assertThat(loaded.memoryRefAt(bob, 0)).isEqualTo(2);

        loaded.close();
    }

    @Test
    void migrate_skipsWhenNoLegacyFile() {
        // No entity.graph in tempDir
        var result = EntityGraphMigrationCli.migrate(tempDir);
        assertThat(result.status()).isEqualTo(EntityGraphMigrationCli.MigrationResult.Status.SKIPPED);
    }

    @Test
    void migrate_skipsWhenAlreadyMigrated() throws Exception {
        // Setup: create both files
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        Path legacyFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY);
        EntityGraphMemory legacyGraph = new EntityGraphMemory(64, 128);
        legacyGraph.addEntity("test-entity", "OTHER");
        legacyGraph.save(legacyFile, null);
        legacyGraph.close();

        // Create a dummy edir to simulate already-migrated state
        Path edirFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY_DIRECTORY);
        TypeRegistryMemory typeReg = TypeRegistryMemory.seeded(com.spectrayan.spector.memory.kernel.SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
        EntityDirectory dir = new EntityDirectory(edirFile, 64, typeReg);
        dir.save(edirFile);
        dir.close();

        var result = EntityGraphMigrationCli.migrate(tempDir);
        assertThat(result.status()).isEqualTo(EntityGraphMigrationCli.MigrationResult.Status.ALREADY_MIGRATED);
    }

    @Test
    void migrate_idempotent_doubleRunSameResult() throws Exception {
        // Setup: namespace with legacy graph
        Path runtimeDir = StorageLayout.runtimeDir(tempDir);
        Files.createDirectories(runtimeDir);

        Path legacyFile = runtimeDir.resolve(StorageLayout.FILE_ENTITY);
        EntityGraphMemory legacyGraph = new EntityGraphMemory(128, 256);
        legacyGraph.addEntity("entity-a", "CONCEPT");
        legacyGraph.addEntity("entity-b", "CONCEPT");
        legacyGraph.linkEntityToMemory(0, 5);
        legacyGraph.save(legacyFile, null);
        legacyGraph.close();

        // First migration
        var result1 = EntityGraphMigrationCli.migrate(tempDir);
        assertThat(result1.status()).isEqualTo(EntityGraphMigrationCli.MigrationResult.Status.MIGRATED);

        // Second run should detect already-migrated
        var result2 = EntityGraphMigrationCli.migrate(tempDir);
        assertThat(result2.status()).isEqualTo(EntityGraphMigrationCli.MigrationResult.Status.ALREADY_MIGRATED);
    }
}
