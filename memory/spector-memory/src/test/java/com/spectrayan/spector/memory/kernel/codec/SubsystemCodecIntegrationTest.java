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
package com.spectrayan.spector.memory.kernel.codec;

import com.spectrayan.spector.memory.cortex.TextAppendCodec;
import com.spectrayan.spector.memory.cortex.TypeRegistryCodec;
import com.spectrayan.spector.memory.hebbian.HebbianGraphCodec;
import com.spectrayan.spector.memory.index.IndexRecordCodec;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.temporal.TemporalChainCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SubsystemCodecIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("legacyTpchAutoMigration")
    void legacyTpchAutoMigration() throws Exception {
        Path legacyFile = tempDir.resolve("temporal.dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(legacyFile.toFile()))) {
            out.writeInt(Integer.reverseBytes(0x54504348)); // TPCH magic (Little-Endian)
            out.writeInt(Integer.reverseBytes(1));          // version 1
            out.writeLong(0L);
            out.write(new byte[16]);   // dummy record data
        }

        TemporalChainCodec codec = new TemporalChainCodec();
        MigrationResult result = Codecs.ensureCurrent(
                Codecs.defaultRegistry(), MemoryId.of("temporal", "test"),
                codec.layout(), legacyFile, null, null
        );

        assertThat(result.migrated()).isTrue();
        assertThat(FormatDetector.detect(legacyFile, Codecs.defaultRegistry()))
                .isPresent()
                .contains(FormatId.smkm(2));
    }

    @Test
    @DisplayName("legacyTextAutoMigration")
    void legacyTextAutoMigration() throws Exception {
        Path legacyFile = tempDir.resolve("text.dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(legacyFile.toFile()))) {
            out.writeInt(Integer.reverseBytes(0x54585442)); // TXT Blob magic (Little-Endian)
            out.writeInt(Integer.reverseBytes(1));          // version 1
            out.writeLong(0L);
            out.write(new byte[32]);
        }

        TextAppendCodec codec = new TextAppendCodec();
        MigrationResult result = Codecs.ensureCurrent(
                Codecs.defaultRegistry(), MemoryId.of("text", "test"),
                codec.layout(), legacyFile, null, null
        );

        assertThat(result.migrated()).isTrue();
        assertThat(FormatDetector.detect(legacyFile, Codecs.defaultRegistry()))
                .isPresent()
                .contains(FormatId.smkm(1));
    }
}
