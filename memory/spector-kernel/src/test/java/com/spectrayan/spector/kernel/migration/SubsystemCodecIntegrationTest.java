/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.migration;

import com.spectrayan.spector.kernel.migration.TextAppendCodec;
import com.spectrayan.spector.kernel.migration.TypeRegistryCodec;
import com.spectrayan.spector.kernel.migration.HebbianGraphCodec;
import com.spectrayan.spector.kernel.migration.IndexRecordCodec;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.migration.TemporalChainCodec;
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
