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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AbstractTierStoreTest {

    private CognitiveHeader createHeader() {
        byte flags = SynapticHeaderConstants.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());
        return new CognitiveHeader(12345L, 0L, 1.0f, 0.5f, 0, (short)0, (byte)0, flags, (byte)0, 1.0f);
    }

    @Test
    @DisplayName("Volatile store is not persistent")
    void volatileStoreNotPersistent() {
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100)) {
            assertThat(store.isPersistent()).isFalse();
        }
    }

    @Test
    @DisplayName("Persistent store creates mmap file")
    void persistentStoreCreatesMmapFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.mem");
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100, file)) {
            assertThat(store.isPersistent()).isTrue();
            assertThat(store.filePath()).isEqualTo(file);
            assertThat(file).exists();
        }
    }

    @Test
    @DisplayName("Persistent store restores count on reopen")
    void persistentStoreRestoresCountOnReopen(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.mem");
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100, file)) {
            store.write(createHeader(), new byte[128]);
            store.write(createHeader(), new byte[128]);
            store.force();
        }
        
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100, file)) {
            assertThat(store.size()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Metadata header contains magic and version")
    void metadataHeaderContainsMagicAndVersion(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.mem");
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100, file)) {
            // TIER magic is 0x54494552
            int magic = store.segment().get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
            assertThat(magic).isEqualTo(0x54494552);
            int version = store.segment().get(java.lang.foreign.ValueLayout.JAVA_INT, 4);
            assertThat(version).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("tombstoneCount scans correctly")
    void tombstoneCountScansCorrectly(@TempDir Path tempDir) {
        Path file = tempDir.resolve("test.mem");
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100, file)) {
            long offset1 = store.write(createHeader(), new byte[128]);
            long offset2 = store.write(createHeader(), new byte[128]);
            
            store.layout().tombstone(store.segment(), offset1);
            
            assertThat(store.tombstoneCount()).isEqualTo(1);
            assertThat(store.tombstoneRatio()).isCloseTo(0.5f, within(0.01f));
        }
    }

    @Test
    @DisplayName("tombstoneRatio is zero when empty")
    void tombstoneRatioZeroWhenEmpty() {
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100)) {
            assertThat(store.tombstoneRatio()).isZero();
        }
    }

    @Test
    @DisplayName("force is a no-op for volatile store")
    void forceIsNoOpForVolatile() {
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100)) {
            store.force(); // Should not throw exception
        }
    }

    @Test
    @DisplayName("visibleCount follows publish")
    void visibleCountFollowsPublish() {
        try (SemanticMemoryStore store = new SemanticMemoryStore(128, 100)) {
            assertThat(store.visibleCount()).isZero();
            store.write(createHeader(), new byte[128]);
            assertThat(store.visibleCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("close releases arena")
    void closeReleasesArena() {
        SemanticMemoryStore store = new SemanticMemoryStore(128, 100);
        store.close();
        // After close, writing to the store should throw because the arena is closed
        CognitiveHeader header = createHeader();
        assertThatThrownBy(() -> store.write(header, new byte[128]))
            .isInstanceOf(IllegalStateException.class);
    }
}
