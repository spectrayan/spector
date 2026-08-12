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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.model.MemoryType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Golden-file + throw-on-unreadable tests for the {@code .midx} v6 on-disk format
 * (issue #443, ADR-0002 D6.7 / Phase 2).
 *
 * <p>Highest-risk part of the epic (on-disk format change), so this test locks down:</p>
 * <ol>
 *   <li><b>v6 round-trip</b> — save/load preserves the per-record {@code colocatedPartition}.</li>
 *   <li><b>v5 back-compat</b> — a synthesized v5 file (64-byte header schemaVersion=5 +
 *       40-byte slots, reusing the real id-pool) loads with {@code colocatedPartition = 0}
 *       and does not crash.</li>
 *   <li><b>throw-on-unreadable</b> — a newer-than-v6 schema and a truncated standard file
 *       both throw {@link SpectorStorageException} rather than silently returning empty.</li>
 * </ol>
 */
@DisplayName("issue #443 — .midx v6 format golden-file + throw-on-unreadable (Phase 2)")
class MemoryIndexV6FormatTest {

    private static final int LAYOUT_ID = 0x4D494458; // 'MIDX'
    private static final int HEADER = MemoryHeader.HEADER_BYTES; // 64
    private static final int V6_STRIDE = 48;
    private static final int V5_STRIDE = 40;

    @TempDir
    Path dir;

    // ── 1. v6 round-trip preserves colocatedPartition ─────────────

    @Test
    @DisplayName("v6 round-trip: save/load preserves colocatedPartition per record")
    void v6RoundTripPreservesColocatedPartition() {
        MemoryIndex original = new MemoryIndex();
        // No text position → text lives inline in the id-pool blob (keeps this test
        // independent of text.dat; it exercises the .midx slot format only).
        original.register("m-a",
                new MemoryLocation(MemoryType.EPISODIC, /*offset*/ 64L, /*graphSlot*/ 3,
                        /*colocatedPartition*/ 0, /*textOffset*/ -1L, /*textLength*/ -1),
                "alpha in partition zero", MemorySource.OBSERVED, new String[]{"p0"});
        original.register("m-b",
                new MemoryLocation(MemoryType.EPISODIC, /*offset*/ 64L, /*graphSlot*/ 7,
                        /*colocatedPartition*/ 2, /*textOffset*/ -1L, /*textLength*/ -1),
                "bravo in partition two", MemorySource.INFERRED, new String[]{"p2"});

        Path midx = dir.resolve("gold_v6.midx");
        original.save(midx);

        MemoryIndex loaded = MemoryIndex.load(midx);
        assertThat(loaded.size()).isEqualTo(2);
        assertThat(loaded.isColocatedPartitionPersisted())
                .as("a v6 file must report colocatedPartition as persisted").isTrue();

        MemoryLocation a = loaded.locate("m-a");
        assertThat(a.colocatedPartition()).isEqualTo(0);
        assertThat(a.graphSlot()).isEqualTo(3);
        assertThat(a.offset()).isEqualTo(64L);
        assertThat(loaded.text("m-a")).isEqualTo("alpha in partition zero");

        MemoryLocation b = loaded.locate("m-b");
        assertThat(b.colocatedPartition()).as("colocatedPartition survives the v6 round-trip").isEqualTo(2);
        assertThat(b.graphSlot()).isEqualTo(7);
        assertThat(loaded.text("m-b")).isEqualTo("bravo in partition two");

        // Reverse key is partition-aware: two records at the SAME offset in different
        // partitions resolve to their own ids (no collision).
        assertThat(loaded.findIdByOffset(0, MemoryType.EPISODIC, 64L)).isEqualTo("m-a");
        assertThat(loaded.findIdByOffset(2, MemoryType.EPISODIC, 64L)).isEqualTo("m-b");
    }

    // ── 2. v5 golden file loads with colocatedPartition = 0 ───────

    @Test
    @DisplayName("v5 golden file: loads clean with colocatedPartition=0 (WARN, no crash)")
    void v5FileLoadsWithZeroColocatedPartition() throws Exception {
        // Build a v6 file first so we can reuse its byte-identical id-pool (.idpl),
        // then synthesize a v5 .midx (40-byte slots, schemaVersion=5) from its slots.
        MemoryIndex src = new MemoryIndex();
        src.register("v5-a",
                new MemoryLocation(MemoryType.SEMANTIC, 128L, 1, 5, -1L, -1),
                "semantic fact one", MemorySource.USER_STATED, new String[]{"k"});
        src.register("v5-b",
                new MemoryLocation(MemoryType.PROCEDURAL, 256L, -1, 9, -1L, -1),
                "procedural step two", MemorySource.PROCEDURAL, new String[]{});
        Path v6midx = dir.resolve("src_v6.midx");
        src.save(v6midx);
        Path v6idpl = dir.resolve("src_v6.idpl");

        // Synthesize the v5 pair.
        Path v5midx = dir.resolve("gold_v5.midx");
        Path v5idpl = dir.resolve("gold_v5.idpl");
        Files.copy(v6idpl, v5idpl);      // id-pool format is identical between v5 and v6
        writeV5MidxFromV6(v6midx, v5midx);

        MemoryIndex loaded = MemoryIndex.load(v5midx);
        assertThat(loaded.size()).isEqualTo(2);
        assertThat(loaded.isColocatedPartitionPersisted())
                .as("a v5 file has no persisted colocated partition").isFalse();

        // v5 has no colocatedPartition dimension → every record defaults to partition 0.
        assertThat(loaded.locate("v5-a").colocatedPartition()).isEqualTo(0);
        assertThat(loaded.locate("v5-b").colocatedPartition()).isEqualTo(0);
        // graphSlot (the former misnamed field at [24:4]) is preserved.
        assertThat(loaded.locate("v5-a").graphSlot()).isEqualTo(1);
        assertThat(loaded.text("v5-a")).isEqualTo("semantic fact one");
        assertThat(loaded.text("v5-b")).isEqualTo("procedural step two");
    }

    // ── 3. throw-on-unreadable: newer version + truncation ────────

    @Test
    @DisplayName("newer-than-v7 schema throws SpectorStorageException")
    void newerSchemaVersionThrows() throws Exception {
        Path midx = dir.resolve("future.midx");
        byte[] file = new byte[HEADER]; // header only, count=0
        MemorySegment seg = MemorySegment.ofArray(file);
        MemoryHeader.write(seg, 0, /*schemaVersion*/ 8, MemoryShape.RECORD, 0x01,
                /*capacity*/ 0, /*count*/ 0, V6_STRIDE, LAYOUT_ID,
                System.currentTimeMillis(), System.currentTimeMillis());
        Files.write(midx, file);

        assertThatThrownBy(() -> MemoryIndex.load(midx))
                .isInstanceOf(SpectorStorageException.class)
                .hasMessageContaining("v8");
    }

    @Test
    @DisplayName("truncated standard file (< 64B header) throws SpectorStorageException")
    void truncatedStandardFileThrows() throws Exception {
        Path midx = dir.resolve("truncated.midx");
        byte[] file = new byte[32]; // shorter than the 64-byte header
        MemorySegment seg = MemorySegment.ofArray(file);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, 0, MemoryHeader.MAGIC); // claims SMKM
        Files.write(midx, file);

        assertThatThrownBy(() -> MemoryIndex.load(midx))
                .isInstanceOf(SpectorStorageException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    @DisplayName("missing file starts fresh (no throw)")
    void missingFileStartsFresh() {
        MemoryIndex loaded = MemoryIndex.load(dir.resolve("does-not-exist.midx"));
        assertThat(loaded.size()).isZero();
    }

    // ── helper: transcode a v6 .midx to a v5 .midx (drop colocatedPartition) ──

    private static void writeV5MidxFromV6(Path v6midx, Path v5midx) throws Exception {
        byte[] v6 = Files.readAllBytes(v6midx);
        MemorySegment v6seg = MemorySegment.ofArray(v6);
        int count = (int) MemoryHeader.readCount(v6seg, 0);

        long dataBytes = (long) count * V5_STRIDE;
        byte[] v5 = new byte[(int) (HEADER + dataBytes)];
        MemorySegment v5seg = MemorySegment.ofArray(v5);
        MemoryHeader.write(v5seg, 0, /*schemaVersion*/ 5, MemoryShape.RECORD, 0x01,
                /*capacity*/ count, /*count*/ count, V5_STRIDE, LAYOUT_ID,
                System.currentTimeMillis(), System.currentTimeMillis());

        ByteBuffer in = ByteBuffer.wrap(v6).order(ByteOrder.nativeOrder());
        ByteBuffer out = ByteBuffer.wrap(v5).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            int base = HEADER + i * V6_STRIDE;
            long poolOffset = in.getLong(base);
            int poolLen = in.getInt(base + 8);
            int typeOrd = in.getInt(base + 12);
            long offset = in.getLong(base + 16);
            int graphSlot = in.getInt(base + 24);
            long textOffset = in.getLong(base + 28);
            int textLength = in.getInt(base + 36);
            // [40:8] colocatedPartition + reserved are intentionally dropped for v5.

            int outBase = HEADER + i * V5_STRIDE;
            out.putLong(outBase, poolOffset);
            out.putInt(outBase + 8, poolLen);
            out.putInt(outBase + 12, typeOrd);
            out.putLong(outBase + 16, offset);
            out.putInt(outBase + 24, graphSlot);
            out.putLong(outBase + 28, textOffset);
            out.putInt(outBase + 36, textLength);
        }
        Files.write(v5midx, v5);
    }
}
