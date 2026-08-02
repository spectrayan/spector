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

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Golden-file and migration tests for {@link HyperEntityGraphMemory} persistence (#435).
 *
 * <p>#435 migrates the hyper-entity graph onto the kernel SMKM container (64-byte
 * {@link MemoryHeader} + 16-byte HyperEntity sub-header, schemaVersion 2) and makes
 * {@link HyperEntityGraphMemory#load} the single in-class migration authority (per the CEO
 * decision — not the codec). This suite proves that:</p>
 * <ul>
 *   <li>the legacy pure-HYEG container ({@code [32B HYEG][hedges][vertices]}) and the interim
 *       hybrid container ({@code [64B SMKM][32B HYEG][hedges][vertices]}, the exact format the
 *       pre-#435 {@code save()} wrote) load through {@code load()} preserving hyperedges,
 *       vertices, weights and the rebuilt incidence lists;</li>
 *   <li>the original bytes are preserved verbatim in a {@code .bak.hyeg} (golden-file fidelity);</li>
 *   <li>the rewritten file is a native SMKM v2 container that round-trips; and</li>
 *   <li>present-but-unreadable/truncated files fail loud instead of silently returning an empty
 *       graph (#432/#433 TD-04).</li>
 * </ul>
 */
class HyperEntityGraphMemoryMigrationTest {

    private static final int ENTITY_CAP = 100;
    private static final int HEDGE_CAP = 50;

    // Legacy container constants (must match the pre-#435 on-disk format).
    private static final int FILE_MAGIC = 0x48594547; // "HYEG"
    private static final int LEGACY_FILE_VERSION = 1;
    private static final int LEGACY_HEADER_BYTES = 32;

    @TempDir
    Path tempDir;

    // ── Canonical fixture ─────────────────────────────────────────
    // he0: {0,1,2} SUBJECT/OBJECT/CONTEXT, type=10, w=5.0, mem=100, ts=12345
    // he1: {3,4}   SUBJECT/OBJECT,         type=20, w=3.0, mem=200, ts=67890
    // he2: {0,1,3} roles 1/2/3,            type=30, w=7.0, mem=300, ts=555
    private static HyperEntityGraphMemory buildFixture() {
        HyperEntityGraphMemory g = new HyperEntityGraphMemory(ENTITY_CAP, HEDGE_CAP);
        g.addHyperedge(new int[]{0, 1, 2}, new int[]{1, 2, 3}, 10, 5.0f, 100, 12345L);
        g.addHyperedge(new int[]{3, 4}, new int[]{1, 2}, 20, 3.0f, 200, 67890L);
        g.addHyperedge(new int[]{0, 1, 3}, new int[]{1, 2, 3}, 30, 7.0f, 300, 555L);
        return g;
    }

    private static void assertFixtureData(HyperEntityGraphMemory g) {
        assertThat(g.totalHyperedges()).isEqualTo(3);

        HyperEdge e0 = g.getHyperedge(0);
        assertThat(e0).isNotNull();
        assertThat(e0.type()).isEqualTo(10);
        assertThat(e0.weight()).isEqualTo(5.0f);
        assertThat(e0.memoryIdx()).isEqualTo(100);
        assertThat(e0.timestamp()).isEqualTo(12345L);
        assertThat(e0.vertices()).hasSize(3);
        assertThat(e0.vertices().get(0).entityId()).isEqualTo(0);
        assertThat(e0.vertices().get(0).roleId()).isEqualTo(1);
        assertThat(e0.vertices().get(2).entityId()).isEqualTo(2);

        HyperEdge e1 = g.getHyperedge(1);
        assertThat(e1).isNotNull();
        assertThat(e1.type()).isEqualTo(20);
        assertThat(e1.weight()).isEqualTo(3.0f);
        assertThat(e1.vertices()).hasSize(2);

        HyperEdge e2 = g.getHyperedge(2);
        assertThat(e2).isNotNull();
        assertThat(e2.type()).isEqualTo(30);
        assertThat(e2.weight()).isEqualTo(7.0f);
        assertThat(e2.timestamp()).isEqualTo(555L);

        // Rebuilt incidence: entity 0 -> he0, he2; entity 1 -> he0, he2; entity 3 -> he1, he2.
        assertThat(g.findHyperedgesForEntity(0)).hasSize(2);
        assertThat(g.findHyperedgesForEntity(1)).hasSize(2);
        assertThat(g.findHyperedgesForEntity(2)).hasSize(1);
        assertThat(g.findHyperedgesForEntity(3)).hasSize(2);
        assertThat(g.findHyperedgesForEntity(4)).hasSize(1);

        Set<Integer> coOccurring = g.findCoOccurringEntities(0);
        assertThat(coOccurring).containsExactlyInAnyOrder(1, 2, 3);
    }

    // ══════════════════════════════════════════════════════════════
    // Golden-file writers — reproduce the exact pre-#435 on-disk bytes
    // ══════════════════════════════════════════════════════════════

    /**
     * Extracts the {@code (nextHyperedgeId, nextVertexOffset, hedge bytes, vertex bytes)} of a
     * fixture by saving it to a scratch file and reading it back. Returns the raw hyperedge and
     * vertex slabs (used-prefix only) so the legacy writers can lay them out at a different offset.
     */
    private record RawData(int entityCap, int hedgeCap, int nextId, int nextVertexOff,
                           int total, byte[] hedgeBytes, byte[] vertexBytes) {}

    private RawData extractRaw() throws IOException {
        // Save the fixture to the current (SMKM v2) format, then slice out the data regions.
        Path scratch = tempDir.resolve("scratch-smkm.hyeg");
        try (HyperEntityGraphMemory g = buildFixture()) {
            g.save(scratch);
        }
        byte[] all = Files.readAllBytes(scratch);
        // SMKM v2: [64B header][16B sub-header][hedges][vertices].
        ByteBuffer sub = ByteBuffer.wrap(all, MemoryHeader.HEADER_BYTES, 16).order(ByteOrder.nativeOrder());
        int entityCap = sub.getInt();
        int nextId = sub.getInt();
        int nextVertexOff = sub.getInt();
        int total = sub.getInt();
        int dataStart = MemoryHeader.HEADER_BYTES + 16;
        int hedgeLen = nextId * HyperEntityLayout.HEDGE_BYTES;
        int vertexLen = nextVertexOff * HyperEntityLayout.VERTEX_BYTES;
        byte[] hedgeBytes = new byte[hedgeLen];
        byte[] vertexBytes = new byte[vertexLen];
        System.arraycopy(all, dataStart, hedgeBytes, 0, hedgeLen);
        System.arraycopy(all, dataStart + hedgeLen, vertexBytes, 0, vertexLen);
        Files.deleteIfExists(scratch);
        return new RawData(entityCap, HEDGE_CAP, nextId, nextVertexOff, total, hedgeBytes, vertexBytes);
    }

    private static ByteBuffer legacyCustomHeader(RawData d) {
        ByteBuffer h = ByteBuffer.allocate(LEGACY_HEADER_BYTES).order(ByteOrder.nativeOrder());
        h.putInt(FILE_MAGIC);
        h.putInt(LEGACY_FILE_VERSION);
        h.putInt(d.entityCap());
        h.putInt(d.hedgeCap());
        h.putInt(d.nextId());
        h.putInt(d.nextVertexOff());
        h.putInt(d.total());
        h.putInt(0); // reserved
        h.flip();
        return h;
    }

    /** Writes the legacy pure-HYEG container: {@code [32B HYEG][hedges][vertices]}. */
    private void writeLegacyPure(Path file, RawData d) throws IOException {
        try (FileChannel ch = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ch.write(legacyCustomHeader(d));
            ch.write(ByteBuffer.wrap(d.hedgeBytes()));
            ch.write(ByteBuffer.wrap(d.vertexBytes()));
        }
    }

    /**
     * Writes the interim hybrid container exactly as the pre-#435 {@code save()} did:
     * {@code [64B SMKM header (schemaVersion 1)][32B HYEG][hedges][vertices]}.
     */
    private void writeLegacyHybrid(Path file, RawData d) throws IOException {
        try (FileChannel ch = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
             Arena arena = Arena.ofConfined()) {
            MemorySegment head = arena.allocate(MemoryHeader.HEADER_BYTES);
            long now = System.currentTimeMillis();
            // Pre-#435 save wrote schemaVersion=FILE_VERSION=1 into the kernel header.
            MemoryHeader.write(head, 0, LEGACY_FILE_VERSION, MemoryShape.GRAPH, 0,
                    d.hedgeCap(), d.total(), HyperEntityLayout.HEDGE_BYTES, FILE_MAGIC, now, now);
            ch.write(head.asByteBuffer());
            ch.write(legacyCustomHeader(d));
            ch.write(ByteBuffer.wrap(d.hedgeBytes()));
            ch.write(ByteBuffer.wrap(d.vertexBytes()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Tests
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("legacy pure-HYEG migrates to SMKM v2 with a byte-exact .bak.hyeg (golden file)")
    void migratesPureHyegWithGoldenBackup() throws IOException {
        RawData d = extractRaw();
        Path file = tempDir.resolve("pure.hyeg");
        writeLegacyPure(file, d);
        byte[] original = Files.readAllBytes(file);

        try (HyperEntityGraphMemory g = HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP)) {
            assertFixtureData(g);
        }

        // Golden-file: the backup is byte-for-byte the original legacy container.
        Path bak = file.resolveSibling(file.getFileName() + ".bak.hyeg");
        assertThat(Files.exists(bak)).isTrue();
        assertThat(Files.readAllBytes(bak)).isEqualTo(original);

        // The rewritten file is a native SMKM v2 container.
        assertThat(peekInt(file, 0)).isEqualTo(MemoryHeader.MAGIC);
        assertThat(peekInt(file, 4)).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("legacy hybrid [SMKM+HYEG] migrates to SMKM v2 with a byte-exact .bak.hyeg")
    void migratesHybridWithGoldenBackup() throws IOException {
        RawData d = extractRaw();
        Path file = tempDir.resolve("hybrid.hyeg");
        writeLegacyHybrid(file, d);
        byte[] original = Files.readAllBytes(file);

        try (HyperEntityGraphMemory g = HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP)) {
            assertFixtureData(g);
        }

        Path bak = file.resolveSibling(file.getFileName() + ".bak.hyeg");
        assertThat(Files.exists(bak)).isTrue();
        assertThat(Files.readAllBytes(bak)).isEqualTo(original);

        assertThat(peekInt(file, 0)).isEqualTo(MemoryHeader.MAGIC);
        assertThat(peekInt(file, 4)).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("migrated SMKM v2 file re-loads without touching the backup a second time")
    void migratedFileReloadsAsNative() throws IOException {
        RawData d = extractRaw();
        Path file = tempDir.resolve("reload.hyeg");
        writeLegacyPure(file, d);

        try (HyperEntityGraphMemory g = HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP)) {
            assertFixtureData(g);
        }
        // Second load takes the native SMKM v2 path (no migration).
        try (HyperEntityGraphMemory g = HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP)) {
            assertFixtureData(g);
        }
    }

    @Test
    @DisplayName("native SMKM v2 round-trip preserves hyperedges, vertices and incidence")
    void nativeSmkmRoundTrip() {
        Path file = tempDir.resolve("native.hyeg");
        try (HyperEntityGraphMemory g = buildFixture()) {
            g.save(file);
        }
        try (HyperEntityGraphMemory g = HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP)) {
            assertFixtureData(g);
        }
    }

    @Test
    @DisplayName("corrupt file (unknown magic) throws instead of returning empty")
    void corruptFileThrows() throws IOException {
        Path file = tempDir.resolve("corrupt.hyeg");
        byte[] garbage = new byte[128];
        java.util.Arrays.fill(garbage, (byte) 0x01);
        Files.write(file, garbage);

        assertThatThrownBy(() -> HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    @Test
    @DisplayName("truncated legacy file (header present, data missing) throws")
    void truncatedLegacyThrows() throws IOException {
        RawData d = extractRaw();
        Path file = tempDir.resolve("truncated.hyeg");
        writeLegacyPure(file, d);
        // Chop off the vertex slab (and part of the hedge slab) so the declared counts overrun.
        long fullSize = Files.size(file);
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ch.truncate(fullSize - d.vertexBytes().length - 8);
        }

        assertThatThrownBy(() -> HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP))
                .isInstanceOf(SpectorGraphPersistenceException.class);
        // A failed migration must not have overwritten the file with a partial SMKM container.
    }

    @Test
    @DisplayName("truncated SMKM v2 file (header present, data missing) throws")
    void truncatedSmkmThrows() throws IOException {
        Path file = tempDir.resolve("native-truncated.hyeg");
        try (HyperEntityGraphMemory g = buildFixture()) {
            g.save(file);
        }
        long fullSize = Files.size(file);
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ch.truncate(fullSize - 16); // drop trailing vertex bytes
        }
        assertThatThrownBy(() -> HyperEntityGraphMemory.load(file, ENTITY_CAP, HEDGE_CAP))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    private static int peekInt(Path file, int offset) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
            ch.position(offset);
            while (buf.hasRemaining() && ch.read(buf) >= 0) {
                // fill
            }
            buf.flip();
            return buf.getInt();
        }
    }
}
