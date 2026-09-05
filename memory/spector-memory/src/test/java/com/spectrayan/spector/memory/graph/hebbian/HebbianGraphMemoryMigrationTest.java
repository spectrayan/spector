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
package com.spectrayan.spector.memory.graph.hebbian;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.graph.hebbian.HebbianEdge;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.codec.Codecs;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for Hebbian graph persistence and legacy migration.
 *
 * <p>#435 migrates {@code HebbianGraphMemory} onto the kernel SMKM CSR container and makes
 * the codec framework the single migration authority. This suite verifies that all three
 * containers — legacy HGPH ({@code 0x48475048}), interim HCSR ({@code 0x48435352}, #432),
 * and native SMKM ({@code 0x534D4B4D}) — load through the full path
 * ({@code Codecs.ensureCurrent} + {@code load}) preserving edge count, weights, and neighbour
 * ordering, and that present-but-unreadable files fail loud instead of silently returning an
 * empty graph (#432/#433).</p>
 */
class HebbianGraphMemoryMigrationTest {

    @TempDir
    Path tempDir;

    // ── Shared assertions on the canonical fixture graph ──
    // 0<->1 w2.0, 0<->2 w5.0, 3<->4 w1.0  =>  6 bidirectional edges.
    private static void assertFixtureGraph(HebbianGraphMemory csr) {
        assertThat(csr.totalEdges()).as("edges preserved").isEqualTo(6).isPositive();

        assertThat(csr.degree(0)).isEqualTo(2);
        List<HebbianEdge> n0 = csr.neighbors(0); // sorted by descending weight
        assertThat(n0).hasSize(2);
        assertThat(n0.get(0).neighborIndex()).isEqualTo(2);
        assertThat(n0.get(0).weight()).isEqualTo(5.0f);
        assertThat(n0.get(1).neighborIndex()).isEqualTo(1);
        assertThat(n0.get(1).weight()).isEqualTo(2.0f);

        assertThat(csr.degree(1)).isEqualTo(1);
        assertThat(csr.neighbors(1).get(0).weight()).isEqualTo(2.0f);

        assertThat(csr.degree(3)).isEqualTo(1);
        assertThat(csr.neighbors(3).get(0).neighborIndex()).isEqualTo(4);
        assertThat(csr.neighbors(3).get(0).weight()).isEqualTo(1.0f);
    }

    private static HebbianGraphMemory buildFixtureCsr() {
        HebbianGraphMemory csr = new HebbianGraphMemory(100);
        csr.strengthen(0, 1, 2.0f);
        csr.strengthen(0, 2, 5.0f);
        csr.strengthen(3, 4, 1.0f);
        return csr;
    }

    private static void ensureCurrent(Path file) throws java.io.IOException {
        // Mirrors the SpectorMemoryFactory up-front migration step.
        Codecs.ensureCurrent(Codecs.defaultRegistry(),
                MemoryId.of("graph", "hebbian-csr"), new HebbianLayout(), file, null, null);
    }

    @Test
    @DisplayName("legacy HGPH migrates to SMKM preserving edges, weights and neighbours")
    void legacyHgphMigratesToSmkm() throws Exception {
        Path graphFile = tempDir.resolve("hebbian.graph");
        HebbianGraph legacy = new HebbianGraph(100);
        legacy.strengthen(0, 1, 2.0f);
        legacy.strengthen(0, 2, 5.0f);
        legacy.strengthen(3, 4, 1.0f);
        legacy.save(graphFile);
        legacy.close();

        // Fixture is the true legacy HGPH container (big-endian magic).
        assertThat(readMagicBE(graphFile)).isEqualTo(0x48475048);

        // Full path: codec migration up front, then load.
        ensureCurrent(graphFile);
        // The codec rewrote the file in place to the SMKM container.
        assertThat(readMagicLE(graphFile)).isEqualTo(RegionPreamble.MAGIC);
        // The original was preserved as a versioned backup.
        assertThat(Files.exists(graphFile.resolveSibling(graphFile.getFileName() + ".bak.v1"))).isTrue();

        HebbianGraphMemory csr = HebbianGraphMemory.load(graphFile, 100);
        try {
            assertFixtureGraph(csr);
        } finally {
            csr.close();
        }
    }

    @Test
    @DisplayName("interim HCSR migrates to SMKM preserving edges, weights and neighbours")
    void interimHcsrMigratesToSmkm() throws Exception {
        Path graphFile = tempDir.resolve("hebbian_hcsr.graph");
        writeInterimHcsrFixture(graphFile);
        assertThat(readMagicBE(graphFile)).isEqualTo(0x48435352);

        ensureCurrent(graphFile);
        assertThat(readMagicLE(graphFile)).isEqualTo(RegionPreamble.MAGIC);

        HebbianGraphMemory csr = HebbianGraphMemory.load(graphFile, 100);
        try {
            assertFixtureGraph(csr);
        } finally {
            csr.close();
        }
    }

    @Test
    @DisplayName("native SMKM file round-trips through ensureCurrent + load unchanged")
    void nativeSmkmRoundTrips() throws Exception {
        Path graphFile = tempDir.resolve("hebbian_smkm.graph");
        HebbianGraphMemory src = buildFixtureCsr();
        try {
            src.save(graphFile);
        } finally {
            src.close();
        }
        assertThat(readMagicLE(graphFile)).isEqualTo(RegionPreamble.MAGIC);

        // ensureCurrent is a no-op for an already-current SMKM file.
        ensureCurrent(graphFile);
        assertThat(readMagicLE(graphFile)).isEqualTo(RegionPreamble.MAGIC);

        HebbianGraphMemory csr = HebbianGraphMemory.load(graphFile, 100);
        try {
            assertFixtureGraph(csr);
        } finally {
            csr.close();
        }
    }

    @Test
    @DisplayName("unknown magic throws instead of silently returning empty")
    void unknownMagicThrowsInsteadOfSilentEmpty() throws Exception {
        Path corrupt = tempDir.resolve("corrupt.graph");
        Files.write(corrupt, new byte[]{'X', 'X', 'X', 'X', 1, 2, 3, 4});
        assertThatThrownBy(() -> HebbianGraphMemory.load(corrupt, 100))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    @Test
    @DisplayName("truncated SMKM file throws instead of silently returning empty")
    void truncatedSmkmFileThrows() throws Exception {
        Path truncated = tempDir.resolve("truncated.graph");
        // Valid SMKM magic (native order) but far too short to hold the header.
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        buf.putInt(RegionPreamble.MAGIC);
        buf.putInt(1);
        Files.write(truncated, buf.array());

        assertThatThrownBy(() -> HebbianGraphMemory.load(truncated, 100))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    // ── Fixture helpers ──

    /**
     * Writes an interim HCSR container (#432 format): a 24-byte big-endian header followed
     * by the CSR offset + edge slabs. Derives the slab bytes from an SMKM save of the same
     * fixture graph (the slabs are byte-identical between the two containers).
     */
    private void writeInterimHcsrFixture(Path dest) throws Exception {
        Path smkm = tempDir.resolve("_smkm_source.tmp");
        HebbianGraphMemory g = buildFixtureCsr();
        try {
            g.save(smkm);
        } finally {
            g.close();
        }
        byte[] all = Files.readAllBytes(smkm);
        ByteBuffer le = ByteBuffer.wrap(all).order(ByteOrder.nativeOrder());
        int capacity = (int) le.getLong(16);   // RegionPreamble capacity (offset 16)
        int totalEdges = (int) le.getLong(24);  // RegionPreamble count (offset 24)
        int edgeCap = le.getInt(64);             // sub-header edgeCapacity (offset 64)
        int cycle = le.getInt(68);               // sub-header currentCycle (offset 68)
        // DATA_START now lives solely on HebbianLayout — single source consumed by impl + test.
        int dataStart = (int) com.spectrayan.spector.memory.kernel.layout.HebbianLayout.DATA_START; // 80
        byte[] slab = Arrays.copyOfRange(all, dataStart, all.length);

        ByteBuffer hdr = ByteBuffer.allocate(HebbianGraphMemory.HCSR_HEADER_BYTES); // big-endian
        hdr.putInt(0x48435352); // HCSR
        hdr.putInt(3);          // interim version
        hdr.putInt(capacity);
        hdr.putInt(edgeCap);
        hdr.putInt(totalEdges);
        hdr.putInt(cycle);

        try (OutputStream os = Files.newOutputStream(dest)) {
            os.write(hdr.array());
            os.write(slab);
        }
        Files.deleteIfExists(smkm);
    }

    private static int readMagicBE(Path file) {
        return readInt(file, ByteOrder.BIG_ENDIAN);
    }

    private static int readMagicLE(Path file) {
        return readInt(file, ByteOrder.nativeOrder());
    }

    private static int readInt(Path file, ByteOrder order) {
        try {
            byte[] head = new byte[4];
            try (var in = Files.newInputStream(file)) {
                int read = in.read(head);
                assertThat(read).isEqualTo(4);
            }
            return ByteBuffer.wrap(head).order(order).getInt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
