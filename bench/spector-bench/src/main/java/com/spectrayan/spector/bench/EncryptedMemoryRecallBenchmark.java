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
package com.spectrayan.spector.bench;

import com.spectrayan.spector.commons.crypto.DekCache;
import com.spectrayan.spector.commons.crypto.DestroyableKeyMaterial;
import com.spectrayan.spector.commons.crypto.EnvelopeCipher;
import com.spectrayan.spector.commons.crypto.KeyHealthStatus;
import com.spectrayan.spector.commons.crypto.KeyProvider;
import com.spectrayan.spector.commons.crypto.WrappedKeyResult;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.core.cognitive.SynapticTag128;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmark suite comparing plaintext ({@link DataEncryptor#NOOP}) against BYOK
 * envelope-encrypted ({@link EnvelopeDataEncryptor}) memory namespaces across:
 * <ul>
 *   <li><b>Warm 6-phase cognitive recall</b> (unfiltered and keyed synaptic tag filtered)</li>
 *   <li><b>KMS-cached DEK recall</b> ({@link DekCache#getOrUnwrap} hit + warm recall)</li>
 *   <li><b>Cold namespace open + first recall</b> (64 KiB {@code AES-256-GCM} page-streamed
 *       decrypt-on-open into 64-byte-aligned off-heap {@code MemorySegment})</li>
 *   <li><b>Write-through {@code remember}</b> (encrypted WAL + keyed synaptic tags + encrypted {@code text.dat})</li>
 *   <li><b>{@link DataEncryptor} crypto primitives</b> ({@code encryptText/decryptText},
 *       {@code encryptPayload/decryptPayload}, {@code encodeTag128})</li>
 * </ul>
 *
 * <p>Validates ADR-0088 §3, §5.3, and §7 Phase 5 (Issue #1074).</p>
 *
 * <p>Run via:</p>
 * <pre>
 *   java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
 *     -cp spector-bench/target/classes:... \
 *     com.spectrayan.spector.bench.BenchmarkSuiteRunner --include ".*EncryptedMemoryRecallBenchmark.*"
 * </pre>
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = {
        "--add-modules", "jdk.incubator.vector",
        "--enable-native-access=ALL-UNNAMED",
        "-Xmx2g"
})
public class EncryptedMemoryRecallBenchmark {

    private static final String TENANT_ID = "tenant-bench-byok";
    private static final String PLAIN_WARM_NS = "ns-plain-warm";
    private static final String ENC_WARM_NS = "ns-enc-warm";
    private static final String PLAIN_COLD_NS = "ns-plain-cold";
    private static final String ENC_COLD_NS = "ns-enc-cold";
    private static final String KEK_KEY_ID = "arn:aws:kms:us-east-1:111122223333:key/bench-kek-1";

    @Param({"64"})
    int dims;

    @Param({"1000"})
    int engramCount;

    @Param({"5"})
    int topK;

    private Path tempDir;
    private EmbeddingProvider embeddingProvider;

    private DestroyableKeyMaterial masterKek;
    private DestroyableKeyMaterial namespaceDek;
    private EnvelopeDataEncryptor envelopeEncryptor;
    private KeyProvider inMemoryKmsProvider;
    private DekCache dekCache;
    private byte[] wrappedDek;
    private byte[] dekAad;

    private SpectorMemory warmPlainMemory;
    private SpectorMemory warmEncryptedMemory;

    private RecallOptions unfilteredOptions;
    private RecallOptions filteredOptions;

    private byte[] sampleTextBytes;
    private byte[] sampleEncryptedText;
    private byte[] sampleVectorPayload;
    private byte[] sampleEncryptedPayload;

    private final AtomicLong writeSeq = new AtomicLong(1_000_000L);

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory("spector-byok-jmh-");
        embeddingProvider = createDeterministicEmbeddingProvider(dims);

        byte[] kekBytes = new byte[32];
        byte[] dekBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            kekBytes[i] = (byte) (0x11 + i);
            dekBytes[i] = (byte) (0x51 + i);
        }
        masterKek = DestroyableKeyMaterial.fromBytes(kekBytes);
        namespaceDek = DestroyableKeyMaterial.fromBytes(dekBytes);

        envelopeEncryptor = new EnvelopeDataEncryptor(
                TENANT_ID,
                ENC_WARM_NS,
                1,
                EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                KEK_KEY_ID,
                namespaceDek.copy());

        inMemoryKmsProvider = createInMemoryKmsProvider(masterKek);
        dekAad = EnvelopeCipher.buildNamespaceDekAad(TENANT_ID, ENC_WARM_NS, 1);
        WrappedKeyResult wrapped = inMemoryKmsProvider.wrapDek(TENANT_ID, KEK_KEY_ID, namespaceDek.bytes(), dekAad);
        wrappedDek = packWrapped(wrapped);
        dekCache = new DekCache(Duration.ofMinutes(5));
        // Prime the DEK cache so KMS-cached benchmark measures steady-state cache hit path
        try (DestroyableKeyMaterial cached = dekCache.getOrUnwrap(
                TENANT_ID, ENC_WARM_NS, 1, KEK_KEY_ID, wrappedDek, dekAad, inMemoryKmsProvider)) {
            // cached
        }

        unfilteredOptions = RecallOptions.builder()
                .topK(topK)
                .recallMode(RecallMode.OBSERVE)
                .build();
        filteredOptions = RecallOptions.builder()
                .topK(topK)
                .recallMode(RecallMode.OBSERVE)
                .synapticFilter("security")
                .hyperfocusMask("urgent")
                .build();

        warmPlainMemory = buildMemory(tempDir, PLAIN_WARM_NS, DataEncryptor.NOOP);
        populateDataset(warmPlainMemory, engramCount);

        warmEncryptedMemory = buildMemory(tempDir, ENC_WARM_NS, envelopeEncryptor);
        populateDataset(warmEncryptedMemory, engramCount);

        // Pre-populate cold-open namespaces on disk and close them
        try (SpectorMemory coldPlain = buildMemory(tempDir, PLAIN_COLD_NS, DataEncryptor.NOOP)) {
            populateDataset(coldPlain, engramCount);
        }
        try (EnvelopeDataEncryptor coldEncEncryptor = new EnvelopeDataEncryptor(
                TENANT_ID,
                ENC_COLD_NS,
                1,
                EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                KEK_KEY_ID,
                namespaceDek.copy());
             SpectorMemory coldEnc = buildMemory(tempDir, ENC_COLD_NS, coldEncEncryptor)) {
            populateDataset(coldEnc, engramCount);
        }

        sampleTextBytes = ("Confidential cognitive memory observation regarding zero-knowledge "
                + "AES-256-GCM envelope encryption and SIMD vector recall latency.")
                .getBytes(StandardCharsets.UTF_8);
        sampleEncryptedText = envelopeEncryptor.encryptText(sampleTextBytes);

        sampleVectorPayload = new byte[dims];
        for (int i = 0; i < dims; i++) {
            sampleVectorPayload[i] = (byte) (i * 3 + 7);
        }
        sampleEncryptedPayload = envelopeEncryptor.encryptPayload(sampleVectorPayload);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (warmPlainMemory != null) {
            warmPlainMemory.close();
        }
        if (warmEncryptedMemory != null) {
            warmEncryptedMemory.close();
        }
        if (dekCache != null) {
            dekCache.close();
        }
        if (envelopeEncryptor != null) {
            envelopeEncryptor.close();
        }
        if (namespaceDek != null && !namespaceDek.isDestroyed()) {
            namespaceDek.close();
        }
        if (masterKek != null && !masterKek.isDestroyed()) {
            masterKek.close();
        }
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

    // ── 1. Warm 6-Phase Recall (Plaintext vs BYOK Encrypted) ─────────────────

    @Benchmark
    public List<CognitiveResult> warmRecall_plaintext() {
        return warmPlainMemory.recall(
                "quantum lattice key rotation security architecture", unfilteredOptions);
    }

    @Benchmark
    public List<CognitiveResult> warmRecall_byokEncrypted() {
        return warmEncryptedMemory.recall(
                "quantum lattice key rotation security architecture", unfilteredOptions);
    }

    // ── 2. Warm Filtered Recall (128-bit Synaptic Tag Filter + Hyperfocus) ───

    @Benchmark
    public List<CognitiveResult> warmRecallFiltered_plaintext() {
        return warmPlainMemory.recall(
                "quantum lattice key rotation security architecture", filteredOptions);
    }

    @Benchmark
    public List<CognitiveResult> warmRecallFiltered_byokEncrypted() {
        return warmEncryptedMemory.recall(
                "quantum lattice key rotation security architecture", filteredOptions);
    }

    // ── 3. Warm Recall with KMS-Cached DEK Lookup (DekCache Hit) ─────────────

    @Benchmark
    public List<CognitiveResult> warmRecallKmsCached_byokEncrypted() {
        try (DestroyableKeyMaterial dek = dekCache.getOrUnwrap(
                TENANT_ID, ENC_WARM_NS, 1, KEK_KEY_ID, wrappedDek, dekAad, inMemoryKmsProvider)) {
            if (dek.isDestroyed()) {
                throw new IllegalStateException("Cached DEK must be active");
            }
            return warmEncryptedMemory.recall(
                    "quantum lattice key rotation security architecture", unfilteredOptions);
        }
    }

    // ── 4. Cold Namespace Open + First Recall ────────────────────────────────

    @Benchmark
    public List<CognitiveResult> coldOpenAndFirstRecall_plaintext() {
        try (SpectorMemory coldMem = buildMemory(tempDir, PLAIN_COLD_NS, DataEncryptor.NOOP)) {
            return coldMem.recall(
                    "quantum lattice key rotation security architecture", unfilteredOptions);
        }
    }

    @Benchmark
    public List<CognitiveResult> coldOpenAndFirstRecall_byokEncrypted() {
        try (DestroyableKeyMaterial dek = dekCache.getOrUnwrap(
                TENANT_ID, ENC_WARM_NS, 1, KEK_KEY_ID, wrappedDek, dekAad, inMemoryKmsProvider);
             EnvelopeDataEncryptor coldEnc = new EnvelopeDataEncryptor(
                     TENANT_ID,
                     ENC_COLD_NS,
                     1,
                     EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                     KEK_KEY_ID,
                     dek);
             SpectorMemory coldMem = buildMemory(tempDir, ENC_COLD_NS, coldEnc)) {
            return coldMem.recall(
                    "quantum lattice key rotation security architecture", unfilteredOptions);
        }
    }

    // ── 5. Write-Through Remember Ingestion ──────────────────────────────────

    @Benchmark
    public String rememberWrite_plaintext() {
        long seq = writeSeq.incrementAndGet();
        String id = "WritePlain-" + (seq & 127);
        warmPlainMemory.remember(
                id,
                "Dynamic operational memory record " + seq + " for plaintext ingestion benchmark",
                MemoryType.WORKING,
                MemorySource.OBSERVED,
                "security",
                "benchmark");
        return id;
    }

    @Benchmark
    public String rememberWrite_byokEncrypted() {
        long seq = writeSeq.incrementAndGet();
        String id = "WriteEnc-" + (seq & 127);
        warmEncryptedMemory.remember(
                id,
                "Dynamic operational memory record " + seq + " for encrypted ingestion benchmark",
                MemoryType.WORKING,
                MemorySource.OBSERVED,
                "security",
                "benchmark");
        return id;
    }

    // ── 6. DataEncryptor Crypto Primitives Microbenchmarks ───────────────────

    @Benchmark
    public byte[] dataEncryptor_encryptDecryptText() {
        byte[] cipher = envelopeEncryptor.encryptText(sampleTextBytes);
        return envelopeEncryptor.decryptText(cipher);
    }

    @Benchmark
    public byte[] dataEncryptor_decryptTextOnly() {
        return envelopeEncryptor.decryptText(sampleEncryptedText);
    }

    @Benchmark
    public byte[] dataEncryptor_encryptDecryptPayload() {
        byte[] cipher = envelopeEncryptor.encryptPayload(sampleVectorPayload);
        return envelopeEncryptor.decryptPayload(cipher);
    }

    @Benchmark
    public SynapticTag128 dataEncryptor_encodeSynapticTag128() {
        return envelopeEncryptor.encodeTag128("security:byok:envelope");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SpectorMemory buildMemory(Path baseDir, String namespaceId, DataEncryptor encryptor) {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(dims);
        memProps.setSemanticCapacity(Math.max(512, engramCount + 256));
        memProps.setProceduralCapacity(256);
        memProps.setWorkingCapacity(256);
        memProps.setEpisodicPartitionCapacity(Math.max(512, engramCount + 256));
        memProps.setEpisodicSegmentSize(256 * 1024L);
        memProps.setTextSegmentSize(512 * 1024L);
        memProps.setCoactivationPairCapacity(256);
        memProps.setCoactivationEdgeCapacity(512);
        memProps.setEntityGraphCapacity(128);
        memProps.setProvenanceCapacity(256);

        Path nsDir = baseDir.resolve("namespaces").resolve(namespaceId);
        return SpectorMemoryBuilder.create(SpectorProperties.of(memProps))
                .persistence(nsDir)
                .persistenceMode(MemoryPersistenceMode.DISK)
                .namespaceId(namespaceId)
                .embeddingProvider(embeddingProvider)
                .dataEncryptor(encryptor)
                .build();
    }

    private static void populateDataset(SpectorMemory memory, int count) {
        for (int i = 0; i < count; i++) {
            String id = "engram-" + i;
            String text = "Engram #" + i + ": quantum lattice cryptography envelope key rotation "
                    + "architecture segment page verification sequence " + (i % 37);
            if (i % 3 == 0) {
                memory.remember(id, text, MemoryType.SEMANTIC, MemorySource.USER_STATED,
                        "security", "urgent", "crypto");
            } else if (i % 3 == 1) {
                memory.remember(id, text, MemoryType.SEMANTIC, MemorySource.OBSERVED,
                        "security", "architecture");
            } else {
                memory.remember(id, text, MemoryType.PROCEDURAL, MemorySource.PROCEDURAL,
                        "runbook", "ops");
            }
        }
    }

    static EmbeddingProvider createDeterministicEmbeddingProvider(int dimensions) {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                float[] v = new float[dimensions];
                byte[] b = text.getBytes(StandardCharsets.UTF_8);
                float normSq = 0f;
                for (int i = 0; i < dimensions; i++) {
                    int bVal = b[i % b.length] & 0xFF;
                    float val = ((bVal + i * 17) % 97) / 97.0f + 0.05f;
                    v[i] = val;
                    normSq += val * val;
                }
                float inv = (float) (1.0 / Math.sqrt(normSq));
                for (int i = 0; i < dimensions; i++) {
                    v[i] *= inv;
                }
                return EmbeddingResult.of(v, "deterministic-" + dimensions + "d");
            }

            @Override
            public List<EmbeddingResult> embedBatch(List<String> texts) {
                return texts.stream().map(this::embed).toList();
            }

            @Override
            public int dimensions() {
                return dimensions;
            }

            @Override
            public String modelName() {
                return "deterministic-" + dimensions + "d";
            }
        };
    }

    static KeyProvider createInMemoryKmsProvider(DestroyableKeyMaterial kek) {
        return new KeyProvider() {
            @Override
            public String providerId() {
                return "bench-kms";
            }

            @Override
            public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad) {
                EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(kek, plaintextDek, aad);
                return new WrappedKeyResult(env.ciphertextWithTag(), env.iv(), kekKeyId, "v1");
            }

            @Override
            public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] packedWrappedDek, byte[] aad) {
                byte[] iv = Arrays.copyOfRange(packedWrappedDek, 0, EnvelopeCipher.GCM_IV_LENGTH_BYTES);
                byte[] ct = Arrays.copyOfRange(packedWrappedDek, EnvelopeCipher.GCM_IV_LENGTH_BYTES, packedWrappedDek.length);
                byte[] raw = EnvelopeCipher.decrypt(kek, iv, ct, aad);
                return new DestroyableKeyMaterial(raw, true);
            }

            @Override
            public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
                return KeyHealthStatus.HEALTHY;
            }
        };
    }

    static byte[] packWrapped(WrappedKeyResult res) {
        byte[] iv = res.wrapIv();
        byte[] ct = res.wrappedDek();
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }
}
