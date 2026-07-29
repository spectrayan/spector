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
package com.spectrayan.spector.memory.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.shape.DefaultRecordMemory;
import com.spectrayan.spector.memory.kernel.shape.DefaultAppendMemory;
import com.spectrayan.spector.memory.kernel.shape.DefaultRegistryMemory;
import com.spectrayan.spector.memory.kernel.shape.RegistryLayout;
import com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.memory.kernel.layout.IdBlobLayout;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCsr;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

import static org.assertj.core.api.Assertions.assertThat;

class WalRecoveryDispatcherTest {

    @TempDir
    Path tempDir;

    private float getEdgeWeight(HebbianGraphCsr graph, int u, int v) {
        for (HebbianEdge edge : graph.neighbors(u)) {
            if (edge.neighborIndex() == v) {
                return edge.weight();
            }
        }
        return 0.0f;
    }

    @Test
    void testFullRoundTripRecovery() throws Exception {
        Path walDir = tempDir.resolve("wal");
        Path recordFile = tempDir.resolve("record.bin");
        Path appendFile = tempDir.resolve("append.bin");
        Path registryFile = tempDir.resolve("registry.bin");
        Path entityFile = tempDir.resolve("entity.bin");
        Path chainFile = tempDir.resolve("chain.bin");

        // Backup paths
        Path backupDir = tempDir.resolve("backup");
        Files.createDirectories(backupDir);
        Path recordBackup = backupDir.resolve("record.bin");
        Path appendBackup = backupDir.resolve("append.bin");
        Path registryBackup = backupDir.resolve("registry.bin");
        Path entityBackup = backupDir.resolve("entity.bin");
        Path chainBackup = backupDir.resolve("chain.bin");

        // 1. Initialize and write initial checkpoint state
        try (MemoryWal wal = new MemoryWal(walDir);
             DefaultRecordMemory<IndexEntryLayout> recordMem = new DefaultRecordMemory<>(
                     MemoryId.of("test", "record"), new IndexEntryLayout(), 10, 400, recordFile);
             DefaultAppendMemory<IdBlobLayout> appendMem = new DefaultAppendMemory<>(
                     MemoryId.of("test", "append"), new IdBlobLayout(), 10, 1000, appendFile);
             DefaultRegistryMemory registryMem = new DefaultRegistryMemory(
                     MemoryId.of("test", "registry"), new RegistryLayout(), 10, 1000, registryFile);
             EntityGraph entityGraph = new EntityGraph(entityFile, 10, 20);
             TemporalChainMemory temporalChain = new TemporalChainMemory(chainFile, 10)) {

            recordMem.bindWal(wal);
            appendMem.bindWal(wal);
            registryMem.bindWal(wal);
            entityGraph.bindWal(wal);
            temporalChain.bindWal(wal);

            // Write checkpoint mutations
            byte[] bytes = new byte[40];
            bytes[0] = 11;
            recordMem.write(0, MemorySegment.ofArray(bytes));
            registryMem.intern("CHECKPOINT_KEY");
            entityGraph.addEntity("CheckpointEntity", "TypeA");
            temporalChain.link(0, 1, 100);

            // Flush all to disk
            recordMem.flush();
            appendMem.flush();
            registryMem.flush();
            entityGraph.flush();
            temporalChain.flush();
        }

        // Copy checkpoint files to backup
        Files.copy(recordFile, recordBackup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(appendFile, appendBackup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(registryFile, registryBackup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(entityFile, entityBackup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(chainFile, chainBackup, StandardCopyOption.REPLACE_EXISTING);

        // 2. Open memories and perform crash mutations (logged to WAL and modifying files)
        try (MemoryWal wal = new MemoryWal(walDir);
             DefaultRecordMemory<IndexEntryLayout> recordMem = new DefaultRecordMemory<>(
                     MemoryId.of("test", "record"), new IndexEntryLayout(), 10, 400, recordFile);
             DefaultAppendMemory<IdBlobLayout> appendMem = new DefaultAppendMemory<>(
                     MemoryId.of("test", "append"), new IdBlobLayout(), 10, 1000, appendFile);
             DefaultRegistryMemory registryMem = new DefaultRegistryMemory(
                     MemoryId.of("test", "registry"), new RegistryLayout(), 10, 1000, registryFile);
             EntityGraph entityGraph = new EntityGraph(entityFile, 10, 20);
             TemporalChainMemory temporalChain = new TemporalChainMemory(chainFile, 10);
             HebbianGraphCsr hebbianGraph = new HebbianGraphCsr(10)) {

            recordMem.bindWal(wal);
            appendMem.bindWal(wal);
            registryMem.bindWal(wal);
            entityGraph.bindWal(wal);
            temporalChain.bindWal(wal);
            hebbianGraph.bindWal(wal);

            // Mutate RecordMemory
            byte[] bytes = new byte[40];
            bytes[0] = 42;
            recordMem.write(1, MemorySegment.ofArray(bytes));

            // Mutate AppendMemory
            byte[] appendBytes = new byte[]{9, 9, 9};
            appendMem.append(MemorySegment.ofArray(appendBytes));

            // Mutate RegistryMemory
            registryMem.intern("CRASH_KEY");

            // Mutate EntityGraph
            int e1 = entityGraph.addEntity("CrashEntity1", "TypeA");
            int e2 = entityGraph.addEntity("CrashEntity2", "TypeB");
            entityGraph.addRelation(e1, e2, "REL");
            entityGraph.linkEntityToMemory(e1, 7);

            // Mutate TemporalChain
            temporalChain.link(2, 3, 999);

            // Mutate HebbianGraphCsr
            hebbianGraph.strengthen(2, 3, 1.5f);

            // Flush all to disk to record WAL events but do NOT write SNAPSHOT_MARK (so recovery replays these)
            recordMem.flush();
            appendMem.flush();
            registryMem.flush();
            entityGraph.flush();
            temporalChain.flush();
            hebbianGraph.flush();
        }

        // Restore files to backup state (simulating crash loss of segment updates since backup)
        Files.copy(recordBackup, recordFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(appendBackup, appendFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(registryBackup, registryFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(entityBackup, entityFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(chainBackup, chainFile, StandardCopyOption.REPLACE_EXISTING);

        // 3. Reopen memories (they are back to checkpoint state) and perform recovery
        try (DefaultRecordMemory<IndexEntryLayout> recordMem = new DefaultRecordMemory<>(
                     MemoryId.of("test", "record"), new IndexEntryLayout(), 10, 400, recordFile);
             DefaultAppendMemory<IdBlobLayout> appendMem = new DefaultAppendMemory<>(
                     MemoryId.of("test", "append"), new IdBlobLayout(), 10, 1000, appendFile);
             DefaultRegistryMemory registryMem = new DefaultRegistryMemory(
                     MemoryId.of("test", "registry"), new RegistryLayout(), 10, 1000, registryFile);
             EntityGraph entityGraph = new EntityGraph(entityFile, 10, 20);
             TemporalChainMemory temporalChain = new TemporalChainMemory(chainFile, 10);
             HebbianGraphCsr hebbianGraph = new HebbianGraphCsr(10)) {

            // Verify they are back to checkpoint state
            byte[] readBytes = new byte[40];
            MemorySegment.copy(recordMem.segment(), recordMem.recordOffset(1), MemorySegment.ofArray(readBytes), 0, 40);
            assertThat(readBytes[0]).isEqualTo((byte) 0); // was reset
            
            assertThat(registryMem.idOf("CRASH_KEY")).isEqualTo(-1);
            assertThat(entityGraph.findEntity("CrashEntity1")).isEqualTo(-1);
            assertThat(temporalChain.prev(2)).isEqualTo(-1);
            assertThat(getEdgeWeight(hebbianGraph, 2, 3)).isEqualTo(0.0f);

            // Run recovery
            try (MemoryWal wal = new MemoryWal(walDir)) {
                Map<MemoryId, Memory<?>> memories = new HashMap<>();
                memories.put(recordMem.id(), recordMem);
                memories.put(appendMem.id(), appendMem);
                memories.put(registryMem.id(), registryMem);
                memories.put(entityGraph.id(), entityGraph);
                memories.put(MemoryId.of("temporal", "chain"), temporalChain);
                memories.put(hebbianGraph.id(), hebbianGraph);

                WalRecoveryDispatcher.recover(wal, memories);
            }

            // Verify they have recovered the crash state
            MemorySegment.copy(recordMem.segment(), recordMem.recordOffset(1), MemorySegment.ofArray(readBytes), 0, 40);
            assertThat(readBytes[0]).isEqualTo((byte) 42); // recovered!

            assertThat(registryMem.idOf("CRASH_KEY")).isNotEqualTo(-1);
            assertThat(entityGraph.findEntity("CrashEntity1")).isNotEqualTo(-1);
            assertThat(temporalChain.prev(2)).isEqualTo(3);
            assertThat(getEdgeWeight(hebbianGraph, 2, 3)).isEqualTo(1.5f);
        }
    }
}
