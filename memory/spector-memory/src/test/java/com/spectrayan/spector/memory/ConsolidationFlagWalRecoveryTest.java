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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.cortex.consolidation.CadpContradictionResolver;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contradicted and retracted bits must survive a crash that skips the region flush.
 *
 * <p>The flag is set through the production path, which force-appends a WAL event. Close then
 * checkpoints that event and flushes the header. The test puts the pre-flag data image back,
 * including its checkpoint high-water mark, and leaves the WAL in place — the same shape as a
 * crash after the append and before the region flush. Reopen must replay the flag.</p>
 */
class ConsolidationFlagWalRecoveryTest {

    private static final String FACT = "The billing service deploy window is Thursday afternoon.";

    @Test
    void contradictedFlagSurvivesWhenRegionFlushIsSkipped(@TempDir Path dir) throws Exception {
        try (DefaultSpectorMemory memory = open(dir)) {
            memory.remember("older", "The deploy window is Wednesday.", MemoryType.SEMANTIC, "deploy");
            Thread.sleep(5);
            memory.remember("newer", "The deploy window is Thursday.", MemoryType.SEMANTIC, "deploy");
        }
        Path snapshot = snapshot(dir);

        String loserId;
        try (DefaultSpectorMemory memory = open(dir)) {
            CognitiveRecord older = atColocatedPartition(memory, memory.inspect("older"));
            CognitiveRecord newer = atColocatedPartition(memory, memory.inspect("newer"));
            var resolved = CadpContradictionResolver.resolve(
                    older, newer, memory.partitionManager(), null, null, null, null, memory.wal());
            loserId = resolved.loser().id();

            assertThat(memory.inspect(loserId).isContradicted()).isTrue();
            assertThat(memory.wal().replay(-1L))
                    .extracting(WalEvent::type)
                    .contains(WalEvent.EventType.MARK_CONTRADICTED);
        }

        restoreDataKeepWal(snapshot, dir);

        try (DefaultSpectorMemory reopened = open(dir)) {
            CognitiveRecord loser = reopened.inspect(loserId);
            assertThat(loser).isNotNull();
            assertThat(loser.isContradicted()).isTrue();
        }
    }

    @Test
    void retractedFlagSurvivesWhenRegionFlushIsSkipped(@TempDir Path dir) throws Exception {
        try (DefaultSpectorMemory memory = open(dir)) {
            memory.remember("fact-1", FACT, MemoryType.SEMANTIC, "deploy");
        }
        Path snapshot = snapshot(dir);

        try (DefaultSpectorMemory memory = open(dir)) {
            var loc = memory.index().locate("fact-1");
            // Same two calls forgetWithResult makes once it has resolved a derived fact.
            memory.partitionManager().routerFor(loc.colocatedPartition()).markRetracted(loc);
            memory.wal().appendMarkRetracted("fact-1", loc.type(), loc.offset());
            assertThat(EncodingHeaderFields.isRetracted(memory.inspect("fact-1").consolidationFlags())).isTrue();
            assertThat(memory.wal().replay(-1L))
                    .extracting(WalEvent::type)
                    .contains(WalEvent.EventType.MARK_RETRACTED);
        }

        restoreDataKeepWal(snapshot, dir);

        try (DefaultSpectorMemory reopened = open(dir)) {
            CognitiveRecord fact = reopened.inspect("fact-1");
            assertThat(fact).isNotNull();
            assertThat(EncodingHeaderFields.isRetracted(fact.consolidationFlags())).isTrue();
        }
    }

    private static DefaultSpectorMemory open(Path dir) {
        FakeEmbeddingProvider embedder = new FakeEmbeddingProvider();
        var props = new com.spectrayan.spector.config.properties.MemoryProperties()
                .setDimensions(embedder.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        return (DefaultSpectorMemory) DefaultSpectorMemory.builder(props)
                .embeddingProvider(embedder)
                .namespaceId("flag-wal")
                .persistence(dir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();
    }

    /** CADP routes through {@code partitionIndex}, which inspect fills with the graph slot. */
    private static CognitiveRecord atColocatedPartition(DefaultSpectorMemory memory, CognitiveRecord record) {
        int partition = memory.index().locate(record.id()).colocatedPartition();
        return new CognitiveRecord(
                record.id(), record.text(), record.memoryType(), record.source(), record.tags(),
                record.timestampMs(), record.synapticTags(), record.exactNorm(), record.importance(),
                record.agentRecallCount(), record.spectorRecallCount(), record.centroidId(),
                record.valence(), record.arousal(), record.storageStrength(), record.flags(),
                record.consolidationFlags(), record.quantizedVector(), partition, record.byteOffset(),
                record.metadata() != null ? record.metadata() : Map.of(), record.suppressed());
    }

    /**
     * Replaces every file under {@code live} with the snapshot, then puts the live WAL back.
     * The restored checkpoint high-water mark is the one from before the flag was set.
     */
    private static void restoreDataKeepWal(Path snapshot, Path live) throws IOException {
        Path hold = Files.createTempDirectory("flag-wal-hold");
        try {
            List<Path> walDirs;
            try (var walk = Files.walk(live)) {
                walDirs = walk.filter(p -> Files.isDirectory(p) && "wal".equals(p.getFileName().toString()))
                        .toList();
            }
            for (Path wal : walDirs) {
                Path dest = hold.resolve(live.relativize(wal));
                Files.createDirectories(dest.getParent());
                Files.move(wal, dest);
            }
            try (var walk = Files.walk(live)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    if (!p.equals(live)) {
                        Files.deleteIfExists(p);
                    }
                }
            }
            copyTree(snapshot, live);
            try (var walk = Files.walk(live)) {
                for (Path wal : walk.filter(p -> Files.isDirectory(p) && "wal".equals(p.getFileName().toString()))
                        .toList()) {
                    deleteTree(wal);
                }
            }
            copyTree(hold, live);
        } finally {
            deleteTree(hold);
        }
    }

    private static Path snapshot(Path dir) throws IOException {
        Path snap = Files.createTempDirectory("flag-wal-snap");
        copyTree(dir, snap);
        return snap;
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        try (var walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
