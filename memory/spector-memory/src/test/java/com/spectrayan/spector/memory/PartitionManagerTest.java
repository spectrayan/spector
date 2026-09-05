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

import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.PartitionManager;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.ProceduralMemory;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.WorkingMemory;
import com.spectrayan.spector.memory.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.memory.graph.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.graph.temporal.TemporalChainMemory;

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
 * {@link RememberPathway} is mocked — {@code PartitionManager} calls just
 * {@code updateCognitiveRouter(...)} on it during a roll, and its full construction
 * is prohibitively heavy. Index / Hebbian / Temporal collaborators are real so the
 * {@code flushGlobalState()} behaviour is exercised end-to-end.</p>
 */
class PartitionManagerTest {

    private static final int VEC_BYTES = 16;
    private static final int SEMANTIC_CAP = 4;   // small → easy to drive to tier-full
    private static final int EPISODIC_CAP = 64;
    private static final int PROCEDURAL_CAP = 64;

    @TempDir
    Path basePath;

    private MemoryIndex index;
    private HebbianGraphMemory hebbian;
    private TemporalChainMemory temporal;
    private RememberPathway cognitiveTarget;

    private final List<CognitiveMemoryRouter> routersToClose = new ArrayList<>();

    @BeforeEach
    void setUp() {
        index = new MemoryIndex();
        hebbian = new HebbianGraphMemory(128);
        // Persistent backing so save() (a copy-on-differ) actually materialises a file.
        temporal = new TemporalChainMemory(basePath.resolve("temporal-backing.chain"), 128);
        cognitiveTarget = mock(RememberPathway.class);
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
        WorkingMemory working = new WorkingMemory(VEC_BYTES, 64);
        SemanticMemory semantic = new SemanticMemory(
                VEC_BYTES, SEMANTIC_CAP, StorageLayout.semanticMem(partitionDir));
        ProceduralMemory procedural = new ProceduralMemory(
                VEC_BYTES, PROCEDURAL_CAP, StorageLayout.proceduralMem(partitionDir));
        EpisodicMemory episodicLog = EpisodicMemory.heap();
        CognitiveMemoryRouter router = new CognitiveMemoryRouter(working, semantic, procedural, episodicLog);
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

    private static EncodingHeader semanticHeader(long timestampMs) {
        return EncodingHeader.create(timestampMs, 0L, 1.0f, 0.5f, (short) 0, MemoryType.SEMANTIC);
    }

    private static byte[] vec() {
        return new byte[VEC_BYTES];
    }

    // ──────────────────────────────────────────────────────────────
    // (a) Load-time discovery
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("discovery: empty base dir creates p000 partition on load")
    void emptyBaseDirDiscoversAndCreatesPartitionZero() throws Exception {
        List<Path> discovered = PartitionManager.discoverAllPartitions(basePath);
        assertThat(discovered).hasSize(1);
        Path p0 = discovered.get(0);
        assertThat(p0.getFileName().toString()).startsWith("000_");
        assertThat(Files.isDirectory(p0)).isTrue();
    }

    @Test
    @DisplayName("discovery: existing partition dirs are sorted ascending by seq on load")
    void existingPartitionDirsAreDiscoveredAscendingBySeq() throws Exception {
        Files.createDirectories(StorageLayout.partitionDir(basePath, 0, 1_000L));
        Files.createDirectories(StorageLayout.partitionDir(basePath, 2, 3_000L));
        Files.createDirectories(StorageLayout.partitionDir(basePath, 1, 2_000L));

        List<Path> discovered = PartitionManager.discoverAllPartitions(basePath);
        assertThat(discovered).hasSize(3);
        assertThat(discovered.get(0).getFileName().toString()).startsWith("000_");
        assertThat(discovered.get(1).getFileName().toString()).startsWith("001_");
        assertThat(discovered.get(2).getFileName().toString()).startsWith("002_");
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

        // Fill the active semantic store to capacity.
        SemanticMemory semantic0 = router0.semantic();
        for (int i = 0; i < SEMANTIC_CAP; i++) {
            semantic0.append(semanticHeader(1_000L + i), vec());
        }
        assertThat(semantic0.size()).isEqualTo(SEMANTIC_CAP);

        // The next write must overflow the tier — this is what the ingestion pipeline
        // catches to trigger PartitionManager::rollPartition.
        assertThatThrownBy(() -> semantic0.append(semanticHeader(9_999L), vec()))
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
        assertThat(rolled.semantic().size()).isZero();
        assertThat(rolled.procedural().size()).isZero();

        // The new active semantic store accepts writes again.
        rolled.semantic().append(semanticHeader(2_000L), vec());
        assertThat(rolled.semantic().size()).isEqualTo(1);
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

        SemanticMemory semantic0 = router0.semantic();
        for (int i = 0; i < SEMANTIC_CAP; i++) {
            semantic0.append(semanticHeader(1_000L + i), vec());
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        AtomicBoolean sawData = new AtomicBoolean(false);

        // Reader holds the PRE-ROLL router reference and keeps reading during the roll.
        Thread reader = new Thread(() -> {
            try {
                while (!stop.get()) {
                    int visible = semantic0.visibleCount();
                    assertThat(visible).isEqualTo(SEMANTIC_CAP);
                    // Read record 0 — must remain the value written before the roll.
                    EncodingHeader h = semantic0.readHeader(0);
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
        assertThat(semantic0.size()).isEqualTo(SEMANTIC_CAP);
        assertThat(semantic0.readHeader(SEMANTIC_CAP - 1).timestampMs())
                .isEqualTo(1_000L + (SEMANTIC_CAP - 1));
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
        SemanticMemory semantic0 = router0.semantic();
        for (int i = 0; i < SEMANTIC_CAP; i++) {
            semantic0.append(semanticHeader(1_000L + i), vec());
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
        assertThat(semantic0.size()).isEqualTo(SEMANTIC_CAP);
        assertThat(semantic0.readHeader(0).timestampMs()).isEqualTo(1_000L);
        assertThat(semantic0.readHeader(SEMANTIC_CAP - 1).timestampMs())
                .isEqualTo(1_000L + (SEMANTIC_CAP - 1));

        // Ensure the active roll-created router is closed by tearDown.
        routersToClose.add(pm.cognitiveRouter());

        // Close once: frozen handles' stores are released (reads now throw on closed Arena).
        pm.close();
        assertThatThrownBy(() -> semantic0.readHeader(0))
                .as("frozen store's arena is closed after component close()")
                .isInstanceOf(RuntimeException.class);

        // Idempotent: a second close() is a no-op (each handle closed exactly once).
        pm.close();
    }
}
