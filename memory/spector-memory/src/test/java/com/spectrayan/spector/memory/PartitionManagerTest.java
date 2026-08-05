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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.ProceduralRecordMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests the LIVE directory-level partitioner {@link PartitionManager} — the only
 * production partition implementation (wired by {@code SpectorMemoryFactory} and
 * owned by {@code DefaultSpectorMemory}).
 *
 * <p>This test lives in the {@code com.spectrayan.spector.memory} package because
 * {@link PartitionManager} is package-private.</p>
 *
 * <p>The tier stores, {@link CognitiveMemoryRouter}, and filesystem are REAL. Only
 * {@link CognitiveIngestionTarget} is mocked — {@code PartitionManager} calls just
 * {@code updateCognitiveRouter(...)} on it during a roll, and its full construction
 * is prohibitively heavy. Index / Hebbian / Temporal collaborators are real so the
 * {@code flushGlobalState()} behaviour is exercised end-to-end.</p>
 */
class PartitionManagerTest {

    private static final int VEC_BYTES = 16;
    private static final int SEMANTIC_CAP = 64;
    private static final int EPISODIC_CAP = 4;   // small → easy to drive to tier-full
    private static final int PROCEDURAL_CAP = 64;

    @TempDir
    Path basePath;

    private MemoryIndex index;
    private HebbianGraphMemory hebbian;
    private TemporalChainMemory temporal;
    private CognitiveIngestionTarget cognitiveTarget;

    private final List<CognitiveMemoryRouter> routersToClose = new ArrayList<>();

    @BeforeEach
    void setUp() {
        index = new MemoryIndex();
        hebbian = new HebbianGraphMemory(128);
        // Persistent backing so save() (a copy-on-differ) actually materialises a file.
        temporal = new TemporalChainMemory(basePath.resolve("temporal-backing.chain"), 128);
        cognitiveTarget = mock(CognitiveIngestionTarget.class);
    }

    @AfterEach
    void tearDown() {
        for (CognitiveMemoryRouter router : routersToClose) {
            try {
                router.close();
            } catch (RuntimeException ignored) {
                // best-effort cleanup; shared working store may be double-closed
            }
        }
        try {
            temporal.close();
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    /** Builds a real router with fresh tier stores rooted in the given partition dir. */
    private CognitiveMemoryRouter newRouter(Path partitionDir) {
        WorkingRecordMemory working = new WorkingRecordMemory(VEC_BYTES, 64);
        EpisodicRecordMemory episodic = new EpisodicRecordMemory(
                StorageLayout.episodicMem(partitionDir), VEC_BYTES, EPISODIC_CAP);
        SemanticRecordMemory semantic = new SemanticRecordMemory(
                VEC_BYTES, SEMANTIC_CAP, StorageLayout.semanticMem(partitionDir));
        ProceduralRecordMemory procedural = new ProceduralRecordMemory(
                VEC_BYTES, PROCEDURAL_CAP, StorageLayout.proceduralMem(partitionDir));
        CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, episodic, semantic, procedural);
        routersToClose.add(router);
        return router;
    }

    private PartitionManager newManager(CognitiveMemoryRouter router, Path activeDir) {
        int seq = StorageLayout.parsePartitionSeqNo(activeDir.getFileName().toString());
        return new PartitionManager(
                basePath, VEC_BYTES, SEMANTIC_CAP, EPISODIC_CAP, PROCEDURAL_CAP,
                router, activeDir, /* initialText */ null, seq,
                /* initialFrozen */ java.util.List.of(),
                index, hebbian, temporal, cognitiveTarget, DataEncryptor.NOOP,
                /* useBundleMode */ false, /* activePartitionBundle */ null);
    }

    private static CognitiveHeader episodicHeader(long timestampMs) {
        return CognitiveHeader.create(timestampMs, 0L, 1.0f, 0.5f, (short) 0, MemoryType.EPISODIC);
    }

    private static byte[] vec() {
        return new byte[VEC_BYTES];
    }

    // ──────────────────────────────────────────────────────────────
    // (a) Load-time discovery
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("discovery: fresh base creates partition 000 as active")
    void discoveryCreatesInitialPartitionWhenNoneExist() throws Exception {
        Path active = PartitionManager.discoverOrCreatePartition(basePath);

        assertThat(Files.isDirectory(active)).isTrue();
        assertThat(active.getParent()).isEqualTo(StorageLayout.partitionsDir(basePath));
        assertThat(StorageLayout.isPartitionDir(active.getFileName().toString())).isTrue();
        assertThat(StorageLayout.parsePartitionSeqNo(active.getFileName().toString())).isZero();
    }

    @Test
    @DisplayName("discovery: newest NNN_EPOCH selected as active, prior dirs left frozen (not written)")
    void discoverySelectsNewestPartitionAsActive() throws Exception {
        // Pre-seed three partition dirs; the highest seq (002) must become active.
        Path p0 = StorageLayout.partitionDir(basePath, 0, 1_717_430_400L);
        Path p1 = StorageLayout.partitionDir(basePath, 1, 1_717_516_800L);
        Path p2 = StorageLayout.partitionDir(basePath, 2, 1_717_603_200L);
        Files.createDirectories(p0);
        Files.createDirectories(p1);
        Files.createDirectories(p2);
        // A non-partition directory must be ignored.
        Files.createDirectories(StorageLayout.partitionsDir(basePath).resolve("not-a-partition"));

        Path active = PartitionManager.discoverOrCreatePartition(basePath);

        assertThat(active).isEqualTo(p2);
        assertThat(StorageLayout.parsePartitionSeqNo(active.getFileName().toString())).isEqualTo(2);
        // No new partition dir was created — the prior partitions are simply frozen (never re-selected).
        try (var stream = Files.newDirectoryStream(StorageLayout.partitionsDir(basePath))) {
            long partitionDirs = 0;
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && StorageLayout.isPartitionDir(entry.getFileName().toString())) {
                    partitionDirs++;
                }
            }
            assertThat(partitionDirs).isEqualTo(3);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // (b) Roll on capacity
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("roll: tier-full drives a roll — new dir, seq++, router swap, empty new stores")
    void rollOnCapacityCreatesNewPartitionAndSwapsRouter() throws Exception {
        Path p0 = PartitionManager.discoverOrCreatePartition(basePath);
        CognitiveMemoryRouter router0 = newRouter(p0);
        PartitionManager pm = newManager(router0, p0);

        // Fill the active episodic store to capacity.
        EpisodicRecordMemory episodic0 = router0.episodic();
        for (int i = 0; i < EPISODIC_CAP; i++) {
            episodic0.append(episodicHeader(1_000L + i), vec());
        }
        assertThat(episodic0.totalRecords()).isEqualTo(EPISODIC_CAP);

        // The next write must overflow the tier — this is what the ingestion pipeline
        // catches to trigger PartitionManager::rollPartition.
        assertThatThrownBy(() -> episodic0.append(episodicHeader(9_999L), vec()))
                .isInstanceOf(SpectorMemoryTierFullException.class);

        pm.rollPartition();

        // A new partition dir with the next sequence number exists and is now active.
        Path active = pm.activePartitionDir();
        assertThat(active).isNotEqualTo(p0);
        assertThat(StorageLayout.parsePartitionSeqNo(active.getFileName().toString())).isEqualTo(1);
        assertThat(Files.isDirectory(active)).isTrue();

        // Router was swapped to a brand-new instance backed by empty stores.
        CognitiveMemoryRouter rolled = pm.cognitiveRouter();
        routersToClose.add(rolled);
        assertThat(rolled).isNotSameAs(router0);
        assertThat(rolled.episodic().totalRecords()).isZero();
        assertThat(rolled.semantic().size()).isZero();
        assertThat(rolled.procedural().size()).isZero();

        // The new active episodic store accepts writes again.
        rolled.episodic().append(episodicHeader(2_000L), vec());
        assertThat(rolled.episodic().totalRecords()).isEqualTo(1);
    }

    // ──────────────────────────────────────────────────────────────
    // (c) Concurrency — reader on pre-roll router is consistent; old Arena stays mapped
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrency: pre-roll reader reads consistently and the old Arena is not unmapped by the roll")
    void preRollReaderStaysConsistentAcrossRoll() throws Exception {
        Path p0 = PartitionManager.discoverOrCreatePartition(basePath);
        CognitiveMemoryRouter router0 = newRouter(p0);
        PartitionManager pm = newManager(router0, p0);

        EpisodicRecordMemory episodic0 = router0.episodic();
        for (int i = 0; i < EPISODIC_CAP; i++) {
            episodic0.append(episodicHeader(1_000L + i), vec());
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        AtomicBoolean sawData = new AtomicBoolean(false);

        // Reader holds the PRE-ROLL router reference and keeps reading during the roll.
        Thread reader = new Thread(() -> {
            try {
                while (!stop.get()) {
                    int visible = episodic0.visibleCount();
                    assertThat(visible).isEqualTo(EPISODIC_CAP);
                    // Read record 0 — must remain the value written before the roll.
                    CognitiveHeader h = episodic0.readHeader(0);
                    assertThat(h.timestampMs()).isEqualTo(1_000L);
                    sawData.set(true);
                }
            } catch (Throwable t) {
                readerError.set(t);
            }
        }, "pre-roll-reader");

        reader.start();
        // Perform the roll while the reader is live.
        for (int i = 0; i < 25; i++) {
            pm.rollPartition();
            CognitiveMemoryRouter rolled = pm.cognitiveRouter();
            routersToClose.add(rolled);
        }
        stop.set(true);
        reader.join(5_000);

        assertThat(readerError.get()).as("reader must not observe torn/invalid reads").isNull();
        assertThat(sawData.get()).isTrue();

        // Router was swapped away from router0 …
        assertThat(pm.cognitiveRouter()).isNotSameAs(router0);
        // … yet the OLD partition's Arena was NOT unmapped: reads on router0 still succeed.
        assertThat(episodic0.totalRecords()).isEqualTo(EPISODIC_CAP);
        assertThat(episodic0.readHeader(EPISODIC_CAP - 1).timestampMs())
                .isEqualTo(1_000L + (EPISODIC_CAP - 1));
    }

    // ──────────────────────────────────────────────────────────────
    // (d) flushGlobalState writes global structures to runtime/ on roll
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("roll: flushGlobalState writes index/hebbian/temporal into runtime/")
    void rollFlushesGlobalStateToRuntimeDir() throws Exception {
        Path p0 = PartitionManager.discoverOrCreatePartition(basePath);
        CognitiveMemoryRouter router0 = newRouter(p0);
        PartitionManager pm = newManager(router0, p0);

        // Link two nodes so the persistent backing has content to be copied to runtime/.
        temporal.link(0, 1);

        assertThat(Files.exists(StorageLayout.indexMidxRuntime(basePath))).isFalse();

        pm.rollPartition();
        routersToClose.add(pm.cognitiveRouter());

        assertThat(Files.exists(StorageLayout.indexMidxRuntime(basePath)))
                .as("MemoryIndex flushed to runtime/").isTrue();
        assertThat(Files.exists(StorageLayout.hebbianGraphRuntime(basePath)))
                .as("Hebbian graph flushed to runtime/").isTrue();
        assertThat(Files.exists(StorageLayout.temporalChainRuntime(basePath)))
                .as("Temporal chain flushed to runtime/").isTrue();
    }

    // ──────────────────────────────────────────────────────────────
    // (e) Leak / lifecycle (issue #443, D6 test 6)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("leak: after N rolls frozen handles stay OPEN/readable, then close exactly once at close()")
    void frozenHandlesStayOpenThenCloseOnce() throws Exception {
        Path p0 = PartitionManager.discoverOrCreatePartition(basePath);
        CognitiveMemoryRouter router0 = newRouter(p0);
        PartitionManager pm = newManager(router0, p0);

        // Seed the initial (soon-to-be-frozen) partition with readable records.
        EpisodicRecordMemory episodic0 = router0.episodic();
        for (int i = 0; i < EPISODIC_CAP; i++) {
            episodic0.append(episodicHeader(1_000L + i), vec());
        }

        final int rolls = 5;
        for (int i = 0; i < rolls; i++) {
            pm.rollPartition();
        }

        // Registry now holds the initial partition + one handle per roll; last = active.
        List<PartitionHandle> snapshot = pm.snapshot();
        assertThat(snapshot).hasSize(rolls + 1);
        assertThat(snapshot.get(snapshot.size() - 1).writable()).isTrue();
        for (int i = 0; i < snapshot.size() - 1; i++) {
            assertThat(snapshot.get(i).writable()).as("earlier handles are frozen").isFalse();
        }

        // FROZEN handles remain OPEN and readable (this is the leak fix — old Arenas kept mapped).
        assertThat(episodic0.totalRecords()).isEqualTo(EPISODIC_CAP);
        assertThat(episodic0.readHeader(0).timestampMs()).isEqualTo(1_000L);
        assertThat(episodic0.readHeader(EPISODIC_CAP - 1).timestampMs())
                .isEqualTo(1_000L + (EPISODIC_CAP - 1));

        // Ensure the active roll-created router is closed by tearDown.
        routersToClose.add(pm.cognitiveRouter());

        // Close once: frozen handles' stores are released (reads now throw on closed Arena).
        pm.close();
        assertThatThrownBy(() -> episodic0.readHeader(0))
                .as("frozen store's arena is closed after component close()")
                .isInstanceOf(RuntimeException.class);

        // Idempotent: a second close() is a no-op (each handle closed exactly once).
        pm.close();
    }
}
