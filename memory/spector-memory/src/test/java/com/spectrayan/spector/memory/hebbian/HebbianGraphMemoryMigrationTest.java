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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for issue #432 — legacy Hebbian graph migration data loss.
 *
 * <p>Before the fix, {@code SpectorMemoryFactory} ran a codec ({@code HgphToCsrStep})
 * that rewrote a legacy HGPH file into an SMKM 64-byte header on disk, but
 * {@link HebbianGraphMemory#load} only recognizes the HCSR (0x48435352) and legacy
 * HGPH (0x48475048) magics. An SMKM magic fell through to the "unknown magic" branch,
 * which silently returned a fresh EMPTY graph — losing the user's entire association
 * graph on upgrade.</p>
 *
 * <p>The fix makes the in-class {@code migrateFromV2} the single migration authority
 * and turns the swallow-and-continue paths into hard failures for present-but-unreadable
 * files.</p>
 */
class HebbianGraphMemoryMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("legacy HGPH file migrates to CSR preserving edges, weights and neighbors")
    void legacyHgphMigratesPreservingEdges() {
        // ── Build a populated legacy graph (writes the true HGPH v2 format) ──
        Path graphFile = tempDir.resolve("hebbian.graph");
        HebbianGraph legacy = new HebbianGraph(100);
        legacy.strengthen(0, 1, 2.0f);  // 0<->1 weight 2.0
        legacy.strengthen(0, 2, 5.0f);  // 0<->2 weight 5.0
        legacy.strengthen(3, 4, 1.0f);  // 3<->4 weight 1.0
        int legacyTotalEdges = legacy.totalEdges(); // 6 (bidirectional)
        legacy.save(graphFile);
        legacy.close();

        // Sanity: the file was written in the legacy HGPH format (magic 0x48475048).
        assertThat(readMagic(graphFile)).isEqualTo(0x48475048);

        // ── Load through the CSR memory: must migrate, not silently drop ──
        HebbianGraphMemory csr = HebbianGraphMemory.load(graphFile, 100);
        try {
            assertThat(csr.totalEdges())
                    .as("migrated graph must not be empty")
                    .isEqualTo(legacyTotalEdges)
                    .isPositive();

            // Node 0 has two neighbors, sorted by descending weight.
            assertThat(csr.degree(0)).isEqualTo(2);
            List<HebbianEdge> n0 = csr.neighbors(0);
            assertThat(n0).hasSize(2);
            assertThat(n0.get(0).neighborIndex()).isEqualTo(2);
            assertThat(n0.get(0).weight()).isEqualTo(5.0f);
            assertThat(n0.get(1).neighborIndex()).isEqualTo(1);
            assertThat(n0.get(1).weight()).isEqualTo(2.0f);

            // Reverse edges preserved.
            assertThat(csr.degree(1)).isEqualTo(1);
            assertThat(csr.neighbors(1).get(0).weight()).isEqualTo(2.0f);

            assertThat(csr.degree(3)).isEqualTo(1);
            assertThat(csr.neighbors(3).get(0).neighborIndex()).isEqualTo(4);
            assertThat(csr.neighbors(3).get(0).weight()).isEqualTo(1.0f);

            // After migration the file is rewritten in HCSR format (magic 0x48435352)
            // and the legacy file is preserved as a .v2.bak backup.
            assertThat(readMagic(graphFile)).isEqualTo(0x48435352);
            assertThat(Files.exists(graphFile.resolveSibling(graphFile.getFileName() + ".v2.bak"))).isTrue();
        } finally {
            csr.close();
        }
    }

    @Test
    @DisplayName("present-but-unreadable file (unknown magic) throws instead of silently returning empty")
    void unknownMagicThrowsInsteadOfSilentEmpty() throws Exception {
        Path corrupt = tempDir.resolve("corrupt.graph");
        // Unknown magic (not HCSR, not HGPH) — the exact case that used to be swallowed.
        Files.write(corrupt, new byte[]{0x53, 0x4D, 0x4B, 0x4D, 1, 2, 3, 4}); // "SMKM" + junk

        assertThatThrownBy(() -> HebbianGraphMemory.load(corrupt, 100))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    @Test
    @DisplayName("truncated HCSR file throws instead of silently returning empty")
    void truncatedCsrFileThrows() throws Exception {
        Path truncated = tempDir.resolve("truncated.graph");
        // Valid HCSR magic but a bogus/short header — loadV3 must reject it.
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt(0x48435352); // HCSR magic
        buf.putInt(99);         // invalid version
        Files.write(truncated, buf.array());

        assertThatThrownBy(() -> HebbianGraphMemory.load(truncated, 100))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    private static int readMagic(Path file) {
        try {
            byte[] head = new byte[4];
            try (var in = Files.newInputStream(file)) {
                int read = in.read(head);
                assertThat(read).isEqualTo(4);
            }
            return ByteBuffer.wrap(head).getInt();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
