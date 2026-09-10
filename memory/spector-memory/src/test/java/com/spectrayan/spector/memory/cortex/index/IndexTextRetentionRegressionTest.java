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
package com.spectrayan.spector.memory.cortex.index;

import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.MemorySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the offsets-versus-inline texts branch and legacy bundle migration (R13.4).
 *
 * <p>Validates that:
 * <ul>
 *   <li>DISK ingest with a valid text position ({@code hasTextPosition() == true}) never populates
 *       the on-heap {@code texts} map.</li>
 *   <li>In-memory ingest without companion text positions ({@code hasTextPosition() == false})
 *       retains text inline in {@code texts}.</li>
 *   <li>The legacy v1-v5 {@code texts.putAll} migration path into bundle segments preserves
 *       inline fallback texts.</li>
 * </ul>
 * </p>
 */
@DisplayName("IndexTextRetentionRegressionTest — Offsets vs Inline Texts (R13.4)")
class IndexTextRetentionRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("DISK ingest with text position must NOT populate on-heap texts map")
    void diskIngestWithTextPositionDoesNotPopulateTextsMap() {
        IndexEntryMemory index = new MemoryIndex();

        // Location with valid textOffset and textLength (points to companion text store)
        MemoryLocation diskLocation = new MemoryLocation(
                MemoryType.SEMANTIC, 1024L, 0, 0, 500L, 42);
        assertThat(diskLocation.hasTextPosition()).isTrue();

        index.register("mem-disk-1", diskLocation, "Companion text payload",
                MemorySource.OBSERVED, new String[]{"disk", "cold"});

        // Offsets branch verification: text map must NOT contain the entry on heap
        assertThat(index.hasInlineText("mem-disk-1")).isFalse();
        assertThat(index.inlineTextCount()).isEqualTo(0);

        // Location is faithfully preserved with offsets
        MemoryLocation loc = index.locate("mem-disk-1");
        assertThat(loc).isNotNull();
        assertThat(loc.hasTextPosition()).isTrue();
        assertThat(loc.textOffset()).isEqualTo(500L);
        assertThat(loc.textLength()).isEqualTo(42);
    }

    @Test
    @DisplayName("In-memory ingest without text position MUST populate on-heap texts map")
    void inMemoryIngestWithoutTextPositionPopulatesTextsMap() {
        IndexEntryMemory index = new MemoryIndex();

        // Location without companion text position (default -1 offset and length)
        MemoryLocation inMemoryLocation = new MemoryLocation(
                MemoryType.WORKING, 2048L, -1);
        assertThat(inMemoryLocation.hasTextPosition()).isFalse();

        index.register("mem-heap-1", inMemoryLocation, "Ephemeral in-memory text",
                MemorySource.OBSERVED, new String[]{"working", "hot"});

        // Inline branch verification: text map MUST contain the entry on heap
        assertThat(index.hasInlineText("mem-heap-1")).isTrue();
        assertThat(index.inlineTextCount()).isEqualTo(1);
        assertThat(index.text("mem-heap-1")).isEqualTo("Ephemeral in-memory text");

        // Null text should not be retained in texts map
        MemoryLocation nullTextLoc = new MemoryLocation(MemoryType.WORKING, 4096L, -1);
        index.register("mem-heap-null", nullTextLoc, null,
                MemorySource.PROCEDURAL, new String[]{"system"});
        assertThat(index.hasInlineText("mem-heap-null")).isFalse();
        assertThat(index.inlineTextCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Legacy v1-v5 index.midx to bundle migration path preserves inline texts via texts.putAll")
    void legacyV1ToBundleMigrationPreservesInlineTexts() throws Exception {
        Path legacyMidx = tempDir.resolve("index.midx");
        Path legacyIdpl = tempDir.resolve("index.idpl");
        Path bundlePath = tempDir.resolve("storage.bundle");

        // 1. Create a legacy standalone index with both disk and inline text entries
        IndexEntryMemory legacyIndex = new MemoryIndex();

        MemoryLocation inMemoryLoc = new MemoryLocation(
                MemoryType.EPISODIC, 100L, 1, -1L, -1);
        legacyIndex.register("mem-legacy-inline", inMemoryLoc, "Legacy inline text to migrate",
                MemorySource.REFLECTED, new String[]{"legacy"}, Map.of("origin", "v1"));

        MemoryLocation diskLoc = new MemoryLocation(
                MemoryType.SEMANTIC, 200L, 2, 800L, 50);
        legacyIndex.register("mem-legacy-disk", diskLoc, "Disk text to bypass heap",
                MemorySource.OBSERVED, new String[]{"disk"});

        assertThat(legacyIndex.hasInlineText("mem-legacy-inline")).isTrue();
        assertThat(legacyIndex.hasInlineText("mem-legacy-disk")).isFalse();
        assertThat(legacyIndex.inlineTextCount()).isEqualTo(1);

        // Save legacy standalone midx + idpl files
        legacyIndex.save(legacyMidx);
        assertThat(Files.exists(legacyMidx)).isTrue();
        assertThat(Files.exists(legacyIdpl)).isTrue();

        // 2. Simulate opening a new bundle where legacy index.midx is migrated
        Path bundleFile = tempDir.resolve("storage.bundle");
        try (java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(bundleFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.READ,
                java.nio.file.StandardOpenOption.WRITE);
             Arena arena = Arena.ofConfined()) {
            fc.write(java.nio.ByteBuffer.allocate(262144), 0);
            MemorySegment bundleSeg = fc.map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, 262144, arena);
            MemorySegment midxSlice = bundleSeg.asSlice(0, 131072);
            MemorySegment idplSlice = bundleSeg.asSlice(131072, 131072);

            com.spectrayan.spector.kernel.store.IndexEntryMemory migrated =
                    com.spectrayan.spector.kernel.store.IndexEntryMemory.fromBundle(
                            arena, midxSlice, idplSlice, bundleFile, true);

            // 3. Verify texts.putAll path populated inline texts
            assertThat(migrated.size()).isEqualTo(2);
            assertThat(migrated.hasInlineText("mem-legacy-inline")).isTrue();
            assertThat(migrated.text("mem-legacy-inline")).isEqualTo("Legacy inline text to migrate");
            assertThat(migrated.hasInlineText("mem-legacy-disk")).isFalse();
            assertThat(migrated.inlineTextCount()).isEqualTo(1);

            // Verify legacy standalone files were cleaned up after migration
            assertThat(Files.exists(legacyMidx)).isFalse();
            assertThat(Files.exists(legacyIdpl)).isFalse();
        }
    }

    @Test
    @DisplayName("Removal cleans up on-heap text map for inline memories")
    void removalCleansUpInlineTexts() {
        IndexEntryMemory index = new MemoryIndex();

        MemoryLocation inMemoryLocation = new MemoryLocation(
                MemoryType.WORKING, 1024L, -1);
        index.register("mem-removable", inMemoryLocation, "Transient text",
                MemorySource.OBSERVED, new String[]{"temp"});

        assertThat(index.hasInlineText("mem-removable")).isTrue();
        assertThat(index.inlineTextCount()).isEqualTo(1);

        index.remove("mem-removable");

        assertThat(index.hasInlineText("mem-removable")).isFalse();
        assertThat(index.inlineTextCount()).isEqualTo(0);
        assertThat(index.locate("mem-removable")).isNull();
        assertThat(index.text("mem-removable")).isEmpty();
    }
}
