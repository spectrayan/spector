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
import com.spectrayan.spector.memory.kernel.MemoryHeader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Golden-file and migration tests for {@link EntityGraphMemory} persistence (#435).
 *
 * <p>#435 migrates the entity graph onto the kernel SMKM container (64-byte {@link MemoryHeader}
 * + 16-byte Entity sub-header) and makes an in-class {@code migrateFromEgmm/egph} authority the
 * single migration path (per the CEO decision — not the codec). This suite proves that:</p>
 * <ul>
 *   <li>legacy EGMM ({@code 0x45474D4D}, 32-byte mmap header) and legacy EGPH ({@code 0x45475048},
 *       heap-serialized) files load through {@link EntityGraphMemory#load} preserving entities,
 *       edges, adjacency and weights;</li>
 *   <li>the original bytes are preserved verbatim in a {@code .bak} (golden-file fidelity);</li>
 *   <li>the rewritten file is a native SMKM container; and</li>
 *   <li>present-but-unreadable files fail loud instead of silently returning an empty graph
 *       (#432/#433).</li>
 * </ul>
 */
class EntityGraphMemoryMigrationTest {

    @TempDir
    Path tempDir;

    // ── Canonical fixture ─────────────────────────────────────────
    // alice(0, PERSON) --MANAGES(w2.0)--> project(1, PROJECT)
    // project(1)      --OWNED_BY(w1.0)--> alice(0)
    // alice   -> memories {42, 7}    project -> memory {99}
    private static EntityGraphMemory buildFixture() {
        EntityGraphMemory g = new EntityGraphMemory(16, 64);
        int alice = g.addEntity("Alice", "PERSON");
        int project = g.addEntity("Project Alpha", "PROJECT");
        g.addRelation(alice, project, "MANAGES");
        g.addRelation(alice, project, "MANAGES"); // strengthen -> weight 2.0
        g.addRelation(project, alice, "OWNED_BY");
        g.linkEntityToMemory(alice, 42);
        g.linkEntityToMemory(alice, 7);
        g.linkEntityToMemory(project, 99);
        return g;
    }

    private static void assertFixtureGraph(EntityGraphMemory g) {
        assertThat(g.entityCount()).isEqualTo(2);
        assertThat(g.findEntity("alice")).isEqualTo(0);
        assertThat(g.findEntity("project alpha")).isEqualTo(1);
        assertThat(g.entityType(0)).isEqualTo("PERSON");
        assertThat(g.entityType(1)).isEqualTo("PROJECT");

        List<EntityGraphMemory.EntityEdge> aliceEdges = g.edges(0);
        assertThat(aliceEdges).hasSize(1);
        assertThat(aliceEdges.get(0).targetEntityId()).isEqualTo(1);
        assertThat(aliceEdges.get(0).relationType()).isEqualTo("MANAGES");
        assertThat(aliceEdges.get(0).weight()).isEqualTo(2.0f);

        List<EntityGraphMemory.EntityEdge> projectEdges = g.edges(1);
        assertThat(projectEdges).hasSize(1);
        assertThat(projectEdges.get(0).targetEntityId()).isEqualTo(0);
        assertThat(projectEdges.get(0).relationType()).isEqualTo("OWNED_BY");

        assertThat(g.memoriesForEntity(0)).containsExactly(42, 7);
        assertThat(g.memoriesForEntity(1)).containsExactly(99);
    }

    @Test
    @DisplayName("legacy EGMM migrates to SMKM preserving entities, edges, adjacency and weights")
    void legacyEgmmMigratesToSmkm() throws Exception {
        Path graphFile = tempDir.resolve("entity.graph");
        writeLegacyEgmmFixture(graphFile);

        // Golden: the fixture is a true legacy EGMM container (big-endian magic).
        assertThat(readMagicBE(graphFile)).isEqualTo(0x45474D4D);
        byte[] originalBytes = Files.readAllBytes(graphFile);

        EntityGraphMemory migrated = EntityGraphMemory.load(graphFile, 16, 64);
        try {
            // The file is now a native SMKM container.
            assertThat(readMagicLE(graphFile)).isEqualTo(MemoryHeader.MAGIC);
            // The original EGMM bytes are preserved verbatim as a backup (golden-file fidelity).
            Path bak = graphFile.resolveSibling(graphFile.getFileName() + ".bak.egmm");
            assertThat(Files.exists(bak)).isTrue();
            assertThat(Files.readAllBytes(bak)).isEqualTo(originalBytes);
            assertFixtureGraph(migrated);
        } finally {
            migrated.close();
        }
    }

    @Test
    @DisplayName("legacy EGPH migrates to SMKM preserving entities, edges, adjacency and weights")
    void legacyEgphMigratesToSmkm() throws Exception {
        Path graphFile = tempDir.resolve("entity_egph.graph");
        EntityGraphMemory fixture = buildFixture();
        try {
            EntityGraphSerializer.save(fixture, graphFile, null); // legacy EGPH writer
        } finally {
            fixture.close();
        }

        assertThat(readMagicBE(graphFile)).isEqualTo(0x45475048); // EGPH
        byte[] originalBytes = Files.readAllBytes(graphFile);

        EntityGraphMemory migrated = EntityGraphMemory.load(graphFile, 16, 64);
        try {
            assertThat(readMagicLE(graphFile)).isEqualTo(MemoryHeader.MAGIC);
            Path bak = graphFile.resolveSibling(graphFile.getFileName() + ".bak.egph");
            assertThat(Files.exists(bak)).isTrue();
            assertThat(Files.readAllBytes(bak)).isEqualTo(originalBytes);
            assertFixtureGraph(migrated);
        } finally {
            migrated.close();
        }
    }

    @Test
    @DisplayName("native SMKM file round-trips through save + load unchanged")
    void nativeSmkmRoundTrips() throws Exception {
        Path graphFile = tempDir.resolve("entity_smkm.graph");
        EntityGraphMemory fixture = buildFixture();
        try {
            fixture.save(graphFile);
        } finally {
            fixture.close();
        }
        assertThat(readMagicLE(graphFile)).isEqualTo(MemoryHeader.MAGIC);

        EntityGraphMemory loaded = EntityGraphMemory.load(graphFile, 16, 64);
        try {
            assertFixtureGraph(loaded);
        } finally {
            loaded.close();
        }
    }

    @Test
    @DisplayName("unknown magic throws instead of silently returning empty")
    void unknownMagicThrows() throws Exception {
        Path corrupt = tempDir.resolve("corrupt.graph");
        Files.write(corrupt, new byte[]{'Z', 'Z', 'Z', 'Z', 1, 2, 3, 4, 5, 6, 7, 8});
        assertThatThrownBy(() -> EntityGraphMemory.load(corrupt, 16, 64))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    @Test
    @DisplayName("truncated SMKM file throws instead of silently returning empty")
    void truncatedSmkmThrows() throws Exception {
        Path truncated = tempDir.resolve("truncated.graph");
        // Valid SMKM magic (native order) but far too short to hold the header.
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        buf.putInt(MemoryHeader.MAGIC);
        buf.putInt(1);
        Files.write(truncated, buf.array());

        assertThatThrownBy(() -> EntityGraphMemory.load(truncated, 16, 64))
                .isInstanceOf(SpectorGraphPersistenceException.class);
    }

    // ── Fixture helpers ───────────────────────────────────────────

    /**
     * Writes a legacy EGMM container by hand: a 32-byte big-endian header followed by the
     * entity / edge / adjacency slabs extracted from a populated heap fixture, plus the
     * {@code entity-names.idx} + type-registry sidecars the EGMM format relied on.
     */
    private void writeLegacyEgmmFixture(Path dest) throws IOException {
        EntityGraphMemory fixture = buildFixture();
        try {
            int entityCap = fixture.entityCapacity();
            int edgeCap = fixture.edgeCapacity();
            int entityCount = fixture.entityCount();
            int edgeCount = fixture.edgeCount();
            int adjCap = fixture.adjSegmentCapacity();
            int adjHwm = fixture.adjHighWaterMark();

            byte[] entityBytes = fixture.entitySegment().toArray(ValueLayout.JAVA_BYTE);
            byte[] edgeBytes = fixture.edgeSegment().toArray(ValueLayout.JAVA_BYTE);
            byte[] adjBytes = fixture.adjacencySegment().toArray(ValueLayout.JAVA_BYTE);

            ByteBuffer hdr = ByteBuffer.allocate(32); // big-endian (matches the legacy writer)
            hdr.putInt(0x45474D4D); // EGMM
            hdr.putInt(2);          // version
            hdr.putInt(entityCap);
            hdr.putInt(edgeCap);
            hdr.putInt(entityCount);
            hdr.putInt(edgeCount);
            hdr.putInt(adjCap);
            hdr.putInt(adjHwm);

            try (OutputStream os = Files.newOutputStream(dest)) {
                os.write(hdr.array());
                os.write(entityBytes);
                os.write(edgeBytes);
                os.write(adjBytes);
            }
            // The name index and type registries lived in sidecar files for the EGMM format.
            EntityGraphSerializer.saveNameIndexAndRegistries(fixture, dest, null);
        } finally {
            fixture.close();
        }
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
