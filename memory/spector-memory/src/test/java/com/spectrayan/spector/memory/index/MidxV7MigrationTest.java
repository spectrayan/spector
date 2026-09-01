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

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.kernel.codec.FormatId;
import com.spectrayan.spector.memory.model.MemoryType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MIDX v6 → v7 Migration Step Test")
class MidxV7MigrationTest {

    private static final String[] EMPTY_TAGS = new String[0];

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("MidxV6ToV7Step converts v6 schema version to 7 and writes high-water mark")
    void migratesV6ToV7Header() {
        Path midxPath = tempDir.resolve("v6_store.midx");
        IndexRecordMemory index = new IndexRecordMemory();

        index.register("id-1", new MemoryLocation(MemoryType.EPISODIC, 100L, 0), "text 1", MemorySource.USER_STATED, EMPTY_TAGS);
        index.register("id-2", new MemoryLocation(MemoryType.SEMANTIC, 200L, 1), "text 2", MemorySource.USER_STATED, EMPTY_TAGS);
        index.save(midxPath);

        MidxV6ToV7Step step = new MidxV6ToV7Step();
        assertThat(step.from()).isEqualTo(FormatId.smkm(6));
        assertThat(step.to()).isEqualTo(FormatId.smkm(7));

        IndexRecordMemory reloaded = IndexRecordMemory.load(midxPath);
        assertThat(reloaded.graphSlotHighWater()).isGreaterThanOrEqualTo(2);
        assertThat(reloaded.idAt(0)).isEqualTo("id-1");
        assertThat(reloaded.idAt(1)).isEqualTo("id-2");
    }
}
