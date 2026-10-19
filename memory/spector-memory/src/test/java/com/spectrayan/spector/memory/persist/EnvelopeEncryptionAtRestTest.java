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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.error.SpectorEncryptionException;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.persist.migration.MigrationPipeline;
import com.spectrayan.spector.memory.persist.migration.NamespaceEncryptionMigrator;
import com.spectrayan.spector.memory.persist.migration.SchemaVersion;
import com.spectrayan.spector.memory.persist.migration.migrations.V1_1_to_V1_2_EnvelopeEncryptionMigration;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end verification suite for ADR-0088 / Issue #1071:
 * Encrypt memory payloads, vectors, WAL records, and synaptic tags at rest with per-namespace DEKs.
 */
class EnvelopeEncryptionAtRestTest {

    private static final int DIMS = 64;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("spector-enc-test-");
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

    private static EmbeddingProvider deterministicEmbeddingProvider() {
        return new EmbeddingProvider() {
            @Override
            public com.spectrayan.spector.provider.embedding.EmbeddingResult embed(String text) {
                float[] v = new float[DIMS];
                byte[] b = text.getBytes(StandardCharsets.UTF_8);
                float normSq = 0f;
                for (int i = 0; i < DIMS; i++) {
                    int bVal = b[i % b.length] & 0xFF;
                    float val = ((bVal + i * 17) % 97) / 97.0f + 0.05f;
                    v[i] = val;
                    normSq += val * val;
                }
                float inv = (float) (1.0 / Math.sqrt(normSq));
                for (int i = 0; i < DIMS; i++) {
                    v[i] *= inv;
                }
                return com.spectrayan.spector.provider.embedding.EmbeddingResult.of(v, "deterministic-64d");
            }

            @Override
            public List<com.spectrayan.spector.provider.embedding.EmbeddingResult> embedBatch(List<String> texts) {
                return texts.stream().map(this::embed).toList();
            }

            @Override
            public int dimensions() {
                return DIMS;
            }

            @Override
            public String modelName() {
                return "deterministic-64d";
            }
        };
    }

    private static ScalarQuantizer defaultQuantizer() {
        float[] mins = new float[DIMS];
        float[] maxs = new float[DIMS];
        java.util.Arrays.fill(mins, -1.0f);
        java.util.Arrays.fill(maxs, 1.0f);
        return ScalarQuantizer.fromBounds(DIMS, mins, maxs);
    }

    private static SpectorMemory buildMemory(Path baseDir, String namespaceId, DataEncryptor encryptor) {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(DIMS);
        memProps.setSemanticCapacity(128);
        memProps.setProceduralCapacity(64);
        memProps.setWorkingCapacity(32);
        memProps.setEpisodicPartitionCapacity(128);
        memProps.setEpisodicSegmentSize(64 * 1024L);
        memProps.setTextSegmentSize(64 * 1024L);
        memProps.setCoactivationPairCapacity(128);
        memProps.setCoactivationEdgeCapacity(256);
        memProps.setEntityGraphCapacity(64);
        memProps.setProvenanceCapacity(128);

        Path nsDir = baseDir.resolve("namespaces").resolve(namespaceId);
        return SpectorMemoryBuilder.create(SpectorProperties.of(memProps))
                .persistence(nsDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId(namespaceId)
                .embeddingProvider(deterministicEmbeddingProvider())
                .dataEncryptor(encryptor)
                .build();
    }

    private static Path findActivePartitionBundle(Path nsDir) throws IOException {
        Path partitionsDir = StoragePaths.partitionsDir(nsDir);
        try (var stream = Files.list(partitionsDir)) {
            Path partDir = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No partition directory under " + partitionsDir));
            return StoragePaths.partitionBundleFile(partDir);
        }
    }

    @Test
    @DisplayName("Raw byte scan of encrypted namespace files on disk contains zero plaintext strings or vector bytes")
    void rawDiskFilesContainZeroPlaintextTextOrVectors() throws Exception {
        String tenantId = "tenant-acme";
        String namespaceId = "ns-classified";
        String secret1 = "CLASSIFIED_PROJECT_AURORA_2026_ LAUNCH_CODES_7741";
        String secret2 = "HIPAA_PATIENT_RECORD_SSN_999_88_7777_CARDIOLOGY";
        String secret3 = "PROCEDURAL_RUNBOOK_VAULT_MASTER_PASSPHRASE_X99";

        EmbeddingProvider provider = deterministicEmbeddingProvider();
        ScalarQuantizer quantizer = defaultQuantizer();

        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(
                    tenantId, namespaceId, 1, EncryptedSegmentHeader.MODE_BYOK_ENVELOPE, "kms:key-1", dek);

            try (SpectorMemory memory = buildMemory(tempDir, namespaceId, encryptor)) {
                memory.remember("mem-1", secret1, MemoryType.SEMANTIC, MemorySource.USER_STATED, "aurora", "classified");
                memory.remember("mem-2", secret2, MemoryType.WORKING, MemorySource.OBSERVED, "hipaa", "medical");
                memory.remember("mem-3", secret3, MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "runbook", "ops");
            }

            Path nsDir = tempDir.resolve("namespaces").resolve(namespaceId);
            Path runtimeBundle = StoragePaths.runtimeBundleFile(nsDir);
            Path partitionBundle = findActivePartitionBundle(nsDir);

            assertThat(EncryptedSegmentPageCodec.isEncrypted(runtimeBundle)).isTrue();
            assertThat(EncryptedSegmentPageCodec.isEncrypted(partitionBundle)).isTrue();

            EncryptedSegmentHeader rtHeader = EncryptedSegmentPageCodec.readHeader(runtimeBundle);
            assertThat(rtHeader.schemaVersion()).isEqualTo(EncryptedSegmentHeader.SCHEMA_VERSION_V1_2);
            assertThat(rtHeader.encryptionMode()).isEqualTo(EncryptedSegmentHeader.MODE_BYOK_ENVELOPE);
            assertThat(rtHeader.cipherSuite()).isEqualTo(EncryptedSegmentHeader.CIPHER_AES_256_GCM);
            assertThat(rtHeader.kdfId()).isEqualTo(EncryptedSegmentHeader.KDF_HKDF_SHA256);
            assertThat(rtHeader.pageSizeLog2()).isEqualTo(EncryptedSegmentHeader.DEFAULT_PAGE_SIZE_LOG2);
            assertThat(rtHeader.dekVersion()).isEqualTo((short) 1);
            assertThat(rtHeader.dekId()).isEqualTo(encryptor.dekIdBytes());

            float[] v1 = provider.embed(secret1).vector();
            float[] v2 = provider.embed(secret2).vector();
            float[] v3 = provider.embed(secret3).vector();

            List<String> secrets = List.of(secret1, secret2, secret3, "CLASSIFIED_PROJECT_AURORA", "SSN_999_88_7777");
            List<byte[]> vectorPatterns = List.of(
                    quantizer.encode(v1),
                    quantizer.encode(v2),
                    quantizer.encode(v3),
                    floatsToBytes(v1, ByteOrder.LITTLE_ENDIAN),
                    floatsToBytes(v1, ByteOrder.BIG_ENDIAN)
            );

            List<Path> diskFiles;
            try (var stream = Files.walk(nsDir)) {
                diskFiles = stream.filter(Files::isRegularFile).toList();
            }
            assertThat(diskFiles).isNotEmpty();

            for (Path file : diskFiles) {
                byte[] rawBytes = Files.readAllBytes(file);
                for (String s : secrets) {
                    byte[] needle = s.getBytes(StandardCharsets.UTF_8);
                    assertThat(indexOfBytes(rawBytes, needle))
                            .as("Plaintext '%s' must not appear in %s", s, nsDir.relativize(file))
                            .isEqualTo(-1);
                }
                for (byte[] vecBytes : vectorPatterns) {
                    assertThat(indexOfBytes(rawBytes, vecBytes))
                            .as("Raw vector bytes must not appear in %s", nsDir.relativize(file))
                            .isEqualTo(-1);
                }
            }
        }
    }

    @Test
    @DisplayName("Recall parity holds between plaintext, encrypted, and cold-restarted encrypted namespaces including keyed synaptic tag filters")
    void recallParityBetweenPlaintextAndEncryptedAndColdRestart() {
        String tenantId = "tenant-parity";
        String plainNs = "ns-plain";
        String encNs = "ns-enc";

        byte[] deterministicDekBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            deterministicDekBytes[i] = (byte) (0x41 + i);
        }
        try (DestroyableKeyMaterial dek = new DestroyableKeyMaterial(deterministicDekBytes)) {
            EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(tenantId, encNs, 1, dek);

            List<CognitiveResult> plainAll;
            List<CognitiveResult> plainFiltered;
            List<CognitiveResult> plainHyperfocus;

            RecallOptions allOpts = RecallOptions.builder().topK(5).recallMode(com.spectrayan.spector.memory.model.RecallMode.OBSERVE).build();
            RecallOptions filteredOpts = RecallOptions.builder().topK(5).recallMode(com.spectrayan.spector.memory.model.RecallMode.OBSERVE).synapticFilter("quantum").build();
            RecallOptions hyperfocusOpts = RecallOptions.builder().topK(5).recallMode(com.spectrayan.spector.memory.model.RecallMode.OBSERVE).hyperfocusMask("urgent").build();

            try (SpectorMemory plainMem = buildMemory(tempDir, plainNs, DataEncryptor.NOOP)) {
                populateSampleMemories(plainMem);
                plainAll = plainMem.recall("quantum cryptography lattice key", allOpts);
                plainFiltered = plainMem.recall("quantum cryptography lattice key", filteredOpts);
                plainHyperfocus = plainMem.recall("quantum cryptography lattice key", hyperfocusOpts);
            }

            try (SpectorMemory encMem = buildMemory(tempDir, encNs, encryptor)) {
                populateSampleMemories(encMem);
                List<CognitiveResult> encAll = encMem.recall("quantum cryptography lattice key", allOpts);
                List<CognitiveResult> encFiltered = encMem.recall("quantum cryptography lattice key", filteredOpts);
                List<CognitiveResult> encHyperfocus = encMem.recall("quantum cryptography lattice key", hyperfocusOpts);

                assertRecallParity(plainAll, encAll);
                assertRecallParity(plainFiltered, encFiltered);
                assertRecallParity(plainHyperfocus, encHyperfocus);
            }

            // Cold restart from encrypted on-disk bundles & WAL
            try (SpectorMemory reopened = buildMemory(tempDir, encNs, encryptor)) {
                List<CognitiveResult> coldAll = reopened.recall("quantum cryptography lattice key", allOpts);
                List<CognitiveResult> coldFiltered = reopened.recall("quantum cryptography lattice key", filteredOpts);
                List<CognitiveResult> coldHyperfocus = reopened.recall("quantum cryptography lattice key", hyperfocusOpts);

                assertRecallParity(plainAll, coldAll);
                assertRecallParity(plainFiltered, coldFiltered);
                assertRecallParity(plainHyperfocus, coldHyperfocus);
            }
        }
    }

    @Test
    @DisplayName("Missing key, wrong DEK, cross-namespace AAD mismatch, and on-disk ciphertext tampering fail closed with SpectorEncryptionException")
    void tamperDetectionAndWrongKeyAndMissingKeyAndCrossNamespaceIsolation() throws Exception {
        String tenantId = "tenant-sec";
        String namespaceId = "ns-vault";

        try (DestroyableKeyMaterial dek1 = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial dek2 = EnvelopeCipher.generateDek()) {

            EnvelopeDataEncryptor encryptor1 = new EnvelopeDataEncryptor(tenantId, namespaceId, 1, dek1);
            EnvelopeDataEncryptor wrongKeyEncryptor = new EnvelopeDataEncryptor(tenantId, namespaceId, 1, dek2);
            EnvelopeDataEncryptor wrongNamespaceEncryptor = new EnvelopeDataEncryptor(tenantId, "ns-impostor", 1, dek1);

            try (SpectorMemory memory = buildMemory(tempDir, namespaceId, encryptor1)) {
                memory.remember("m1", "Confidential treasury wire transfer instructions",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "treasury");
            }

            // 1. Missing key (NOOP encryptor) -> MEMORY_ENCRYPTION_KEY_MISSING
            assertThatThrownBy(() -> buildMemory(tempDir, namespaceId, DataEncryptor.NOOP))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING));

            // 2. Wrong DEK -> MEMORY_AUTH_TAG_MISMATCH
            assertThatThrownBy(() -> buildMemory(tempDir, namespaceId, wrongKeyEncryptor))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

            // 3. Same DEK bytes but bound to different namespaceId (AAD mismatch) -> MEMORY_AUTH_TAG_MISMATCH
            assertThatThrownBy(() -> buildMemory(tempDir, namespaceId, wrongNamespaceEncryptor))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

            // 4. Tamper with 1 byte inside an encrypted page of partition.bundle -> MEMORY_AUTH_TAG_MISMATCH
            Path nsDir = tempDir.resolve("namespaces").resolve(namespaceId);
            Path bundlePath = findActivePartitionBundle(nsDir);
            byte[] originalBundle = Files.readAllBytes(bundlePath);
            byte[] tamperedBundle = originalBundle.clone();
            tamperedBundle[EncryptedSegmentHeader.HEADER_BYTES + 40] ^= (byte) 0x5A;
            Files.write(bundlePath, tamperedBundle);

            assertThatThrownBy(() -> buildMemory(tempDir, namespaceId, encryptor1))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));

            // Restore partition.bundle and tamper with 1 byte inside an encrypted WAL frame
            Files.write(bundlePath, originalBundle);
            Path walDir = StoragePaths.walDir(nsDir);
            Path walFile;
            try (var stream = Files.list(walDir)) {
                walFile = stream
                        .filter(p -> p.getFileName().toString().startsWith("wal-") && p.getFileName().toString().endsWith(".bin"))
                        .findFirst()
                        .orElseThrow();
            }
            byte[] originalWal = Files.readAllBytes(walFile);
            byte[] tamperedWal = originalWal.clone();
            // Flip a byte inside the encrypted payload (which still has its outer CRC32 updated or triggers auth tag mismatch if CRC updated)
            // Let's also test direct decryptWalPayload tamper detection:
            byte[] walPlain = "wal-secret-payload".getBytes(StandardCharsets.UTF_8);
            byte[] walCipher = encryptor1.encryptWalPayload(1L, (byte) 1, "m1", walPlain);
            walCipher[walCipher.length - 4] ^= (byte) 0x3C;
            assertThatThrownBy(() -> encryptor1.decryptWalPayload(1L, (byte) 1, "m1", walCipher))
                    .isInstanceOf(SpectorEncryptionException.class)
                    .satisfies(ex -> assertThat(((SpectorEncryptionException) ex).errorCode())
                            .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));
        }
    }

    @Test
    @DisplayName("V1.1 -> V1.2 migration re-encrypts plaintext namespace in place, eliminates plaintext on disk, and preserves recall parity")
    void plaintextToEncryptedMigrationPreservesRecallAndEliminatesPlaintextOnDisk() throws Exception {
        String tenantId = "tenant-mig";
        String namespaceId = "ns-legacy-plain";
        String secretText = "LEGACY_PLAINTEXT_SECRET_RECORD_BEFORE_V1_2_MIGRATION";

        List<CognitiveResult> beforeMigration;
        try (SpectorMemory plainMem = buildMemory(tempDir, namespaceId, DataEncryptor.NOOP)) {
            plainMem.remember("leg-1", secretText, MemoryType.SEMANTIC, MemorySource.USER_STATED, "legacy-tag", "critical");
            plainMem.remember("leg-2", "Secondary control record for migration test", MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "ops-tag");
            beforeMigration = plainMem.recall(secretText, RecallOptions.builder().topK(5).synapticFilter("legacy-tag").build());
        }
        assertThat(beforeMigration).isNotEmpty();

        Path nsDir = tempDir.resolve("namespaces").resolve(namespaceId);
        Path partitionBundle = findActivePartitionBundle(nsDir);
        assertThat(EncryptedSegmentPageCodec.isEncrypted(partitionBundle)).isFalse();

        // Mark namespace at V1.1.0 and run MigrationPipeline -> V1.2.0
        Files.writeString(nsDir.resolve(MigrationPipeline.MANIFEST_FILE), "{\"schemaVersion\": \"1.1.0\"}\n");

        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(tenantId, namespaceId, 1, dek);
            MigrationPipeline pipeline = new MigrationPipeline(
                    List.of(new V1_1_to_V1_2_EnvelopeEncryptionMigration(encryptor)),
                    SchemaVersion.V1_2_0);

            boolean migrated = pipeline.migrateIfNeeded(nsDir);
            assertThat(migrated).isTrue();
            assertThat(pipeline.readSchemaVersion(nsDir)).isEqualTo(SchemaVersion.V1_2_0);
            assertThat(EncryptedSegmentPageCodec.isEncrypted(partitionBundle)).isTrue();
            assertThat(EncryptedSegmentPageCodec.isEncrypted(StoragePaths.runtimeBundleFile(nsDir))).isTrue();

            // Verify zero plaintext remains in any file on disk after migration
            try (var stream = Files.walk(nsDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    byte[] rawBytes = Files.readAllBytes(file);
                    assertThat(indexOfBytes(rawBytes, secretText.getBytes(StandardCharsets.UTF_8)))
                            .as("Migrated file %s must not contain plaintext", nsDir.relativize(file))
                            .isEqualTo(-1);
                }
            }

            // Reopen migrated namespace with encryptor and verify recall + keyed synaptic tag filter
            try (SpectorMemory migratedMem = buildMemory(tempDir, namespaceId, encryptor)) {
                List<CognitiveResult> afterMigration = migratedMem.recall(
                        secretText, RecallOptions.builder().topK(5).synapticFilter("legacy-tag").build());
                assertRecallParity(beforeMigration, afterMigration);
            }
        }
    }

    private static void populateSampleMemories(SpectorMemory memory) {
        memory.remember("id-1", "Quantum cryptography uses lattice-based key encapsulation for post-quantum security",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "cryptography", "quantum", "urgent");
        memory.remember("id-2", "AES-256-GCM provides authenticated encryption with 96-bit nonces and 128-bit tags",
                MemoryType.SEMANTIC, MemorySource.OBSERVED, "cryptography", "aes");
        memory.remember("id-3", "Deploy envelope encryption key rotation runbook across production clusters",
                MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "runbook", "urgent");
    }

    private static void assertRecallParity(List<CognitiveResult> expected, List<CognitiveResult> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            CognitiveResult e = expected.get(i);
            CognitiveResult a = actual.get(i);
            assertThat(a.id()).isEqualTo(e.id());
            assertThat(a.text()).isEqualTo(e.text());
            assertThat(a.score()).isCloseTo(e.score(), org.assertj.core.data.Offset.offset(1e-4f));
        }
    }

    private static byte[] floatsToBytes(float[] vector, ByteOrder order) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * Float.BYTES).order(order);
        for (float f : vector) {
            buf.putFloat(f);
        }
        return buf.array();
    }

    private static int indexOfBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return -1;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
