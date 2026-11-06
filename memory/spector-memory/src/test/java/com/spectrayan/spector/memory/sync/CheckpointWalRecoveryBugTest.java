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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegistryLayout;
import com.spectrayan.spector.kernel.shape.DefaultRegistryMemory;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.store.CoActivationMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.HebbianGraphMemory;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.index.IndexContext;
import com.spectrayan.spector.memory.index.IndexKind;
import com.spectrayan.spector.memory.index.IndexPlaneCoordinator;
import com.spectrayan.spector.memory.index.IndexStats;
import com.spectrayan.spector.memory.index.ManagedIndex;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.test.FakeEmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckpointWalRecoveryBugTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotMarkRecoveryUsesPayloadHwmFenceInsteadOfMarkEventSequence() throws Exception {
        Path walDir = tempDir.resolve("wal-fence");
        Path registryFile = tempDir.resolve("registry-fence.bin");
        Path registryBackup = tempDir.resolve("registry-fence.backup.bin");

        long capturedFenceHwm;
        try (MemoryWal wal = new MemoryWal(walDir);
             DefaultRegistryMemory registryMem = new DefaultRegistryMemory(
                     MemoryId.of("test", "registry"), new RegistryLayout(), 16, 1024, registryFile)) {
            registryMem.bindWal(wal);

            // Event 1: part of the checkpointed state
            registryMem.intern("PRE_CHECKPOINT_KEY");
            registryMem.flush();
            capturedFenceHwm = wal.highWaterMark();
            assertThat(capturedFenceHwm).isEqualTo(1L);

            Files.copy(registryFile, registryBackup, StandardCopyOption.REPLACE_EXISTING);

            // Event 2: lands after the quiesce snapshot fence is captured, before SNAPSHOT_MARK is appended
            registryMem.intern("POST_QUIESCE_KEY");
            assertThat(wal.highWaterMark()).isEqualTo(2L);

            // Event 3: SNAPSHOT_MARK carrying payload = capturedFenceHwm (1L), but sequence = 3L
            wal.appendSnapshotMark("system/checkpoint", capturedFenceHwm);
            wal.flush();
        }

        // Simulate crash where disk state only has the snapshot at capturedFenceHwm (1L)
        Files.copy(registryBackup, registryFile, StandardCopyOption.REPLACE_EXISTING);

        try (MemoryWal wal = new MemoryWal(walDir);
             DefaultRegistryMemory registryMem = new DefaultRegistryMemory(
                     MemoryId.of("test", "registry"), new RegistryLayout(), 16, 1024, registryFile)) {
            assertThat(registryMem.idOf("PRE_CHECKPOINT_KEY")).isNotEqualTo(-1);
            assertThat(registryMem.idOf("POST_QUIESCE_KEY")).isEqualTo(-1);

            Map<MemoryId, Memory<?>> memories = new HashMap<>();
            memories.put(registryMem.id(), registryMem);
            WalRecoveryDispatcher.recover(wal, memories);

            // Event 2 (> capturedFenceHwm=1L) must be replayed even though its sequence (2) < SNAPSHOT_MARK sequence (3)
            assertThat(registryMem.idOf("POST_QUIESCE_KEY")).isNotEqualTo(-1);
        }
    }

    @Test
    void checkpointCapturesHwmInsideQuiesceAndRecoversRememberAndEpisodicAfterCrash() throws Exception {
        Path storeDir = tempDir.resolve("crash-store");
        Files.createDirectories(storeDir);
        long sessionId = 42L;

        try (DefaultSpectorMemory memory = openMemory(storeDir)) {
            CheckpointEngine engine = extractCheckpointEngine(memory);
            memory.remember("mem-1", "Alpha checkpointed memory", MemoryType.SEMANTIC, "tag-a");
            memory.rememberEpisodic(
                    ConversationRole.USER, 1, 1_000L, sessionId,
                    "First episodic turn before checkpoint".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 10, 0, 5, 100L, (short) 1, SourceModality.TEXT);

            // Run checkpoint so mem-1 and turn-1 are part of the checkpoint baseline
            engine.checkpoint();

            // Take a snapshot of the checkpointed disk state
            Path diskSnapshot = tempDir.resolve("disk-snapshot");
            copyTree(storeDir, diskSnapshot);

            // Acknowledge new writes after the checkpoint (without another checkpoint)
            memory.remember("mem-2", "Beta post-checkpoint memory", MemoryType.SEMANTIC, "tag-b");
            memory.rememberEpisodic(
                    ConversationRole.ASSISTANT, 2, 2_000L, sessionId,
                    "Second episodic turn after checkpoint".getBytes(StandardCharsets.UTF_8),
                    (short) 1, 0, 12, 8, 100L, (short) 1, SourceModality.TEXT);
            memory.wal().flush();

            // Simulate crash before close(): restore non-WAL files from diskSnapshot while keeping live WAL
            restoreDataKeepWal(diskSnapshot, storeDir);
        } catch (Exception ignored) {
            // Ignore any close exceptions after simulated crash file replacement
        }
        // Release directory lock if still held and reopen from disk + WAL
        com.spectrayan.spector.memory.persist.StoreDirectoryLock.release(storeDir);

        try (DefaultSpectorMemory reopened = openMemory(storeDir)) {
            assertThat(reopened.inspect("mem-1")).isNotNull();
            assertThat(reopened.inspect("mem-2"))
                    .describedAs("Post-checkpoint acknowledged remember() write must survive crash via WAL recovery")
                    .isNotNull();
            assertThat(reopened.inspect("mem-2").text()).isEqualTo("Beta post-checkpoint memory");

            var turns = reopened.tailEpisodic(sessionId, 10);
            assertThat(turns)
                    .describedAs("Both pre-checkpoint and post-checkpoint episodic turns must survive crash")
                    .extracting(r -> new String(r.body(), StandardCharsets.UTF_8))
                    .contains("First episodic turn before checkpoint", "Second episodic turn after checkpoint");
        }
    }

    @Test
    void checkpointCapturesDurabilityFenceBeforeReleasingQuiesceWindow() throws Exception {
        Path walDir = tempDir.resolve("wal-quiesce-order");
        Path metaPath = tempDir.resolve("checkpoint.meta");
        Path basePath = tempDir.resolve("base");
        Files.createDirectories(basePath);

        QuiesceGuard guard = new QuiesceGuard();
        try (MemoryWal wal = new MemoryWal(walDir);
             HebbianGraphMemory hebbian = new HebbianGraphMemory(16);
             TemporalChainMemory chain = new TemporalChainMemory(basePath.resolve("chain.bin"), 16);
             CoActivationMemory coAct = new CoActivationMemory(16)) {

            wal.appendRemember("m-1", new byte[]{1, 2, 3});
            assertThat(wal.highWaterMark()).isEqualTo(1L);

            AtomicLong hwmRecordedInMeta = new AtomicLong(-1L);
            // Simulate a writer that appends immediately after the quiesce window releases
            // (or during post-quiesce steps before checkpoint() finishes).
            CountDownLatch insideQuiesce = new CountDownLatch(1);
            CountDownLatch writerDone = new CountDownLatch(1);

            HebbianGraphBase spyHebbian = Mockito.spy(hebbian);
            Mockito.doAnswer(inv -> {
                insideQuiesce.countDown();
                return null;
            }).when(spyHebbian).save(Mockito.any());

            Thread postQuiesceWriter = Thread.ofVirtual().start(() -> {
                try {
                    insideQuiesce.await(5, TimeUnit.SECONDS);
                    // Wait until quiesce lock is released by acquiring write permit
                    try (var permit = guard.acquireWritePermit()) {
                        wal.appendRemember("m-after-quiesce", new byte[]{4, 5, 6});
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    writerDone.countDown();
                }
            });

            MemoryWal spyWal = Mockito.spy(wal);
            Mockito.doAnswer(inv -> {
                // Ensure the post-quiesce writer has appended if quiesce was already released
                writerDone.await(200, TimeUnit.MILLISECONDS);
                return inv.callRealMethod();
            }).when(spyWal).highWaterMark();

            CheckpointEngine engine = new CheckpointEngine(
                    null, spyWal, metaPath, null, null,
                    spyHebbian, chain, null, null, coAct, null, null, basePath);
            engine.setQuiesceGuard(guard);

            engine.checkpoint();
            postQuiesceWriter.join(5000);

            hwmRecordedInMeta.set(CheckpointEngine.readCheckpointHwm(metaPath));
            // The durability fence must be 1L (captured inside quiesce), NOT 2L (the post-quiesce write)
            assertThat(hwmRecordedInMeta.get()).isEqualTo(1L);
            assertThat(wal.replay(1L))
                    .extracting(WalEvent::memoryId)
                    .contains("m-after-quiesce");
        }
    }

    @Test
    void rememberEpisodicAndAllMutatingEntryPointsRespectQuiesceGuard() throws Exception {
        Path storeDir = tempDir.resolve("quiesce-entry-points");
        Files.createDirectories(storeDir);

        try (DefaultSpectorMemory memory = openMemory(storeDir)) {
            memory.remember("seed-1", "Seed memory for mutation tests", MemoryType.SEMANTIC, "seed");

            QuiesceGuard guard = memory.quiesceGuard();
            AtomicBoolean episodicCompletedWhileQuiesced = new AtomicBoolean(false);
            AtomicBoolean suppressCompletedWhileQuiesced = new AtomicBoolean(false);
            AtomicBoolean assertFactCompletedWhileQuiesced = new AtomicBoolean(false);
            CountDownLatch threadsStarted = new CountDownLatch(3);

            Thread t1;
            Thread t2;
            Thread t3;
            try (var quiesce = guard.acquireQuiesce(5, TimeUnit.SECONDS)) {
                t1 = Thread.ofVirtual().start(() -> {
                    threadsStarted.countDown();
                    memory.rememberEpisodic(
                            ConversationRole.USER, 1, 1_000L, 99L,
                            "turn during quiesce".getBytes(StandardCharsets.UTF_8),
                            (short) 1, 5, 0, 1, 1L, (short) 1, SourceModality.TEXT);
                    episodicCompletedWhileQuiesced.set(true);
                });
                t2 = Thread.ofVirtual().start(() -> {
                    threadsStarted.countDown();
                    memory.suppress("seed-1");
                    suppressCompletedWhileQuiesced.set(true);
                });
                t3 = Thread.ofVirtual().start(() -> {
                    threadsStarted.countDown();
                    memory.assertFact("NodeA", "connectsTo", "NodeB", 0L, 10_000L, 0.9f);
                    assertFactCompletedWhileQuiesced.set(true);
                });

                assertThat(threadsStarted.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(100);

                assertThat(episodicCompletedWhileQuiesced.get())
                        .describedAs("rememberEpisodic must block while quiesce lock is held")
                        .isFalse();
                assertThat(suppressCompletedWhileQuiesced.get())
                        .describedAs("suppress must block while quiesce lock is held")
                        .isFalse();
                assertThat(assertFactCompletedWhileQuiesced.get())
                        .describedAs("assertFact must block while quiesce lock is held")
                        .isFalse();
            }

            t1.join(5000);
            t2.join(5000);
            t3.join(5000);

            assertThat(episodicCompletedWhileQuiesced.get()).isTrue();
            assertThat(suppressCompletedWhileQuiesced.get()).isTrue();
            assertThat(assertFactCompletedWhileQuiesced.get()).isTrue();
        }
    }

    @Test
    void checkpointAbortsAndPreservesWalWhenIndexOrGraphOrCoordinatorOrFlushFails() throws Exception {
        // 1. MemoryIndex.save failure
        assertCheckpointFailureAbortsWatermarkAndTruncation("index-save-failure", (wal, metaPath, basePath) -> {
            MemoryIndex failingIndex = Mockito.mock(MemoryIndex.class);
            Mockito.when(failingIndex.size()).thenReturn(5);
            Mockito.doThrow(new RuntimeException("Simulated MemoryIndex save I/O failure"))
                    .when(failingIndex).save(Mockito.any());
            return new CheckpointEngine(
                    null, wal, metaPath, failingIndex, basePath.resolve("index.midx"),
                    Mockito.mock(HebbianGraphBase.class),
                    Mockito.mock(TemporalChainMemory.class),
                    null, null,
                    Mockito.mock(CoActivationMemory.class),
                    null, null, basePath);
        });

        // 2. HebbianGraph.save failure
        assertCheckpointFailureAbortsWatermarkAndTruncation("hebbian-save-failure", (wal, metaPath, basePath) -> {
            HebbianGraphBase failingHebbian = Mockito.mock(HebbianGraphBase.class);
            Mockito.doThrow(new RuntimeException("Simulated HebbianGraph save I/O failure"))
                    .when(failingHebbian).save(Mockito.any());
            return new CheckpointEngine(
                    null, wal, metaPath, new MemoryIndex(), null,
                    failingHebbian,
                    Mockito.mock(TemporalChainMemory.class),
                    null, null,
                    Mockito.mock(CoActivationMemory.class),
                    null, null, basePath);
        });

        // 3. IndexPlaneCoordinator.checkpointAll failure
        assertCheckpointFailureAbortsWatermarkAndTruncation("coordinator-failure", (wal, metaPath, basePath) -> {
            IndexPlaneCoordinator coordinator = new IndexPlaneCoordinator(null);
            coordinator.register(new ManagedIndex() {
                @Override public String name() { return "FailingDerivedIndex"; }
                @Override public IndexKind kind() { return IndexKind.DERIVED_EXPENSIVE; }
                @Override public Set<String> dependsOn() { return Set.of(); }
                @Override public void attach(IndexContext context) {}
                @Override public CompletionStage<Void> hydrate() { return CompletableFuture.completedFuture(null); }
                @Override public void checkpoint() { throw new RuntimeException("Simulated BM25 checkpoint failure"); }
                @Override public IndexStats stats() { return new IndexStats(0, 0, 0, 0, 0); }
                @Override public void close() {}
            });
            CheckpointEngine engine = new CheckpointEngine(
                    null, wal, metaPath, new MemoryIndex(), null,
                    Mockito.mock(HebbianGraphBase.class),
                    Mockito.mock(TemporalChainMemory.class),
                    null, null,
                    Mockito.mock(CoActivationMemory.class),
                    null, null, basePath);
            engine.setIndexPlaneCoordinator(coordinator);
            return engine;
        });

        // 4. EncryptedBundleFlush callback failure
        assertCheckpointFailureAbortsWatermarkAndTruncation("bundle-flush-failure", (wal, metaPath, basePath) -> {
            CheckpointEngine engine = new CheckpointEngine(
                    null, wal, metaPath, new MemoryIndex(), null,
                    Mockito.mock(HebbianGraphBase.class),
                    Mockito.mock(TemporalChainMemory.class),
                    null, null,
                    Mockito.mock(CoActivationMemory.class),
                    null, null, basePath);
            engine.setBundleFlushCallback(() -> {
                throw new RuntimeException("Simulated EncryptedBundleFlush failure");
            });
            return engine;
        });
    }

    @FunctionalInterface
    private interface EngineFactory {
        CheckpointEngine create(MemoryWal wal, Path metaPath, Path basePath) throws Exception;
    }

    private void assertCheckpointFailureAbortsWatermarkAndTruncation(String caseName, EngineFactory factory) throws Exception {
        Path caseDir = tempDir.resolve(caseName);
        Path walDir = caseDir.resolve("wal");
        Path metaPath = caseDir.resolve("checkpoint.meta");
        Path basePath = caseDir.resolve("base");
        Files.createDirectories(basePath);

        // Use tiny chunk size so truncation would delete chunks if called
        try (MemoryWal wal = new MemoryWal(walDir, 128L)) {
            for (int i = 0; i < 10; i++) {
                wal.appendRemember("mem-" + i, ("payload-for-truncation-test-" + i).getBytes());
            }
            long hwmBefore = wal.highWaterMark();
            assertThat(hwmBefore).isEqualTo(10L);

            CheckpointEngine engine = factory.create(wal, metaPath, basePath);
            engine.setQuiesceGuard(new QuiesceGuard());

            assertThatThrownBy(engine::checkpoint)
                    .describedAs("Case [%s] must throw a typed SpectorException when a checkpoint save fails", caseName)
                    .isInstanceOf(SpectorException.class);

            assertThat(engine.failureCount())
                    .describedAs("Case [%s] must increment failureCount on checkpoint failure", caseName)
                    .isEqualTo(1L);
            assertThat(engine.lastFailure()).isNotNull();

            // Watermark metadata must NOT advance
            assertThat(CheckpointEngine.readCheckpointHwm(metaPath))
                    .describedAs("Case [%s] must not write checkpoint.meta on failure", caseName)
                    .isEqualTo(-1L);

            // SNAPSHOT_MARK must NOT be appended
            assertThat(wal.replay(0))
                    .describedAs("Case [%s] must not append SNAPSHOT_MARK on failure", caseName)
                    .noneMatch(e -> e.type() == WalEvent.EventType.SNAPSHOT_MARK);

            // WAL must NOT be truncated; all 10 events must remain available for replay
            assertThat(wal.replay(0))
                    .describedAs("Case [%s] must not truncate WAL events on failure", caseName)
                    .hasSize(10);
        }
    }

    private static DefaultSpectorMemory openMemory(Path dir) {
        FakeEmbeddingProvider embedder = new FakeEmbeddingProvider();
        MemoryProperties props = new MemoryProperties()
                .setDimensions(embedder.dimensions())
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        return (DefaultSpectorMemory) DefaultSpectorMemory.builder(props)
                .embeddingProvider(embedder)
                .namespaceId("ckpt-wal-test")
                .persistence(dir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .build();
    }

    private static CheckpointEngine extractCheckpointEngine(DefaultSpectorMemory memory) throws Exception {
        Field f = DefaultSpectorMemory.class.getDeclaredField("checkpointEngine");
        f.setAccessible(true);
        return (CheckpointEngine) f.get(memory);
    }

    private static void restoreDataKeepWal(Path snapshot, Path live) throws IOException {
        Path hold = Files.createTempDirectory("ckpt-wal-hold");
        try {
            List<Path> walDirs;
            try (var walk = Files.walk(live)) {
                walDirs = walk.filter(p -> Files.isDirectory(p) && "wal".equals(p.getFileName().toString()))
                        .toList();
            }
            for (Path wal : walDirs) {
                Path dest = hold.resolve(live.relativize(wal));
                Files.createDirectories(dest.getParent());
                copyTree(wal, dest);
            }
            copyTree(snapshot, live);
            copyTree(hold, live);
        } finally {
            deleteTree(hold);
        }
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
