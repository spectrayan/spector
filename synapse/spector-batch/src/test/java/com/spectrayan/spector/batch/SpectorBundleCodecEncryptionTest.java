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
package com.spectrayan.spector.batch;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.memory.error.SpectorEncryptionException;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests ADR-0088 SEC1 container encryption for Spector Memory Bundles (.smb) in {@link SpectorBundleCodec}.
 */
class SpectorBundleCodecEncryptionTest {

    private Path tempDir;
    private SpectorBundleCodec codec;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("spector-smb-sec1-test-");
        codec = new SpectorBundleCodec();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("SEC1 encrypted .smb bundle contains zero plaintext on disk, round-trips cleanly, and fails closed on wrong/missing key or tampering")
    void sec1EncryptedBundleRoundTripAndTamperDetection() throws Exception {
        Path stagingDir = tempDir.resolve("staging");
        Files.createDirectories(stagingDir.resolve("nodes"));
        String secretPayload = "{\"id\":\"m-1\",\"text\":\"TOP_SECRET_DR_BACKUP_PAYLOAD_2026\"}\n";
        Files.writeString(stagingDir.resolve("nodes/chunk-00000.jsonl"), secretPayload);
        Files.writeString(stagingDir.resolve("manifest.json"), """
                {
                  "schemaVersion": "3.0.0",
                  "namespaceId": "ns-dr-vault"
                }
                """);

        Path smbFile = tempDir.resolve("backup.smb");
        Path unpackDir = tempDir.resolve("unpacked");

        try (DestroyableKeyMaterial dek1 = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial dek2 = EnvelopeCipher.generateDek()) {

            EnvelopeDataEncryptor encryptor1 = new EnvelopeDataEncryptor("tenant-dr", "ns-dr-vault", 1, dek1);
            EnvelopeDataEncryptor wrongEncryptor = new EnvelopeDataEncryptor("tenant-dr", "ns-dr-vault", 1, dek2);

            codec.packageBundle(stagingDir, smbFile, encryptor1);

            assertThat(codec.isEncryptedBundle(smbFile)).isTrue();
            byte[] rawSmb = Files.readAllBytes(smbFile);
            assertThat(new String(rawSmb, StandardCharsets.ISO_8859_1))
                    .doesNotContain("TOP_SECRET_DR_BACKUP_PAYLOAD_2026")
                    .doesNotContain("ns-dr-vault");

            // Unpack without key -> MEMORY_ENCRYPTION_KEY_MISSING
            assertThatThrownBy(() -> codec.unpackBundle(smbFile, unpackDir, DataEncryptor.NOOP))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING));

            // Unpack with wrong key -> MEMORY_AUTH_TAG_MISMATCH
            assertThatThrownBy(() -> codec.unpackBundle(smbFile, unpackDir, wrongEncryptor))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

            // Unpack with valid key -> succeeds and matches original files
            codec.unpackBundle(smbFile, unpackDir, encryptor1);
            String unpackedNode = Files.readString(unpackDir.resolve("nodes/chunk-00000.jsonl"));
            assertThat(unpackedNode).isEqualTo(secretPayload);

            // Tamper with ciphertext -> MEMORY_AUTH_TAG_MISMATCH
            byte[] tampered = rawSmb.clone();
            tampered[tampered.length - 5] ^= (byte) 0x7F;
            Files.write(smbFile, tampered);

            Path tamperedUnpack = tempDir.resolve("tampered-unpack");
            assertThatThrownBy(() -> codec.unpackBundle(smbFile, tamperedUnpack, encryptor1))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));
        }
    }
}
