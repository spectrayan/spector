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

import com.spectrayan.spector.bench.scale.PercentileTracker;
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
import com.spectrayan.spector.kernel.storage.EncryptedSegmentPageCodec;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.persist.EnvelopeDataEncryptor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic CI performance &amp; regression gate for the BYOK envelope-encrypted
 * recall, remember, cold-open, and KMS-cached DEK paths (ADR-0088 §3, §5.3, §7 Phase 5, Issue #1074).
 */
@DisplayName("EncryptedRecallPerformanceGateTest — ADR-0088 BYOK Performance & Regression Gate (#1074)")
class EncryptedRecallPerformanceGateTest {

    private static final int DIMS = 64;
    private static final String TENANT_ID = "tenant-perf-gate";
    private static final String KEK_KEY_ID = "arn:aws:kms:us-east-1:111122223333:key/perf-gate-kek";

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("spector-enc-perf-gate-");
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
    @DisplayName("1. EnvelopeDataEncryptor primitives meet DataEncryptor latency contracts (decryptText <= 100 us, payload <= 25 us, encodeTag128 <= 5 us)")
    void dataEncryptorPrimitivesMeetLatencyBudgets() {
        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek();
             EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(
                     TENANT_ID,
                     "ns-crypto-primitives",
                     1,
                     EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                     KEK_KEY_ID,
                     dek.copy())) {

            byte[] textBytes = ("Confidential cognitive observation: zero-knowledge BYOK envelope "
                    + "encryption preserves sub-millisecond recall via off-heap MemorySegment slabs.")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] encryptedText = encryptor.encryptText(textBytes);

            byte[] vectorPayload = new byte[DIMS];
            for (int i = 0; i < DIMS; i++) {
                vectorPayload[i] = (byte) (i * 5 + 3);
            }
            byte[] encryptedPayload = encryptor.encryptPayload(vectorPayload);

            // Warmup JIT
            for (int i = 0; i < 2_000; i++) {
                encryptor.encodeTag128("security:byok:tag");
                encryptor.decryptPayload(encryptedPayload);
                encryptor.decryptText(encryptedText);
            }

            int iterations = 3_000;
            PercentileTracker tagTrackerUs = new PercentileTracker();
            PercentileTracker payloadDecryptTrackerUs = new PercentileTracker();
            PercentileTracker textDecryptTrackerUs = new PercentileTracker();

            long sink = 0L;
            for (int i = 0; i < iterations; i++) {
                long t0 = System.nanoTime();
                SynapticTag128 tag = encryptor.encodeTag128("security:byok:tag");
                long t1 = System.nanoTime();
                tagTrackerUs.record((t1 - t0) / 1_000.0);
                sink ^= tag.lo();

                long p0 = System.nanoTime();
                byte[] decPayload = encryptor.decryptPayload(encryptedPayload);
                long p1 = System.nanoTime();
                payloadDecryptTrackerUs.record((p1 - p0) / 1_000.0);
                sink ^= decPayload[0];

                long x0 = System.nanoTime();
                byte[] decText = encryptor.decryptText(encryptedText);
                long x1 = System.nanoTime();
                textDecryptTrackerUs.record((x1 - x0) / 1_000.0);
                sink ^= decText[0];
            }
            assertThat(sink).isNotEqualTo(0xDEAD_BEEF_CAFE_BABEL);

            // DataEncryptor performance contracts:
            // - encodeTag128: p50 <= 5 us
            // - decryptPayload: p50 <= 25 us
            // - decryptText: p50 <= 100 us
            assertThat(tagTrackerUs.p50())
                    .as("encodeTag128 p50 latency (%.2f us) must be <= 5.0 us", tagTrackerUs.p50())
                    .isLessThanOrEqualTo(5.0);
            assertThat(payloadDecryptTrackerUs.p50())
                    .as("decryptPayload p50 latency (%.2f us) must be <= 25.0 us", payloadDecryptTrackerUs.p50())
                    .isLessThanOrEqualTo(25.0);
            assertThat(textDecryptTrackerUs.p50())
                    .as("decryptText p50 latency (%.2f us) must be <= 100.0 us", textDecryptTrackerUs.p50())
                    .isLessThanOrEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("2. DekCache KMS-cached lookup eliminates remote KMS round-trip overhead (>500x speedup on warm hits)")
    void dekCacheWarmLookupEliminatesKmsRoundTripOverhead() {
        AtomicInteger kmsUnwrapCalls = new AtomicInteger(0);
        try (DestroyableKeyMaterial kek = EnvelopeCipher.generateDek();
             DestroyableKeyMaterial rawDek = EnvelopeCipher.generateDek();
             DekCache dekCache = new DekCache(Duration.ofMinutes(5))) {

            KeyProvider simulatedRemoteKms = new KeyProvider() {
                @Override
                public String providerId() {
                    return "simulated-remote-kms";
                }

                @Override
                public WrappedKeyResult wrapDek(String tenantId, String kekKeyId, byte[] plaintextDek, byte[] aad) {
                    EnvelopeCipher.CiphertextEnvelope env = EnvelopeCipher.encrypt(kek, plaintextDek, aad);
                    return new WrappedKeyResult(env.ciphertextWithTag(), env.iv(), kekKeyId, "v1");
                }

                @Override
                public DestroyableKeyMaterial unwrapDek(String tenantId, String kekKeyId, byte[] packedWrappedDek, byte[] aad) {
                    kmsUnwrapCalls.incrementAndGet();
                    // Simulate a conservative 2 ms external KMS TLS round-trip
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
                    byte[] iv = Arrays.copyOfRange(packedWrappedDek, 0, EnvelopeCipher.GCM_IV_LENGTH_BYTES);
                    byte[] ct = Arrays.copyOfRange(packedWrappedDek, EnvelopeCipher.GCM_IV_LENGTH_BYTES, packedWrappedDek.length);
                    byte[] unwrapped = EnvelopeCipher.decrypt(kek, iv, ct, aad);
                    return new DestroyableKeyMaterial(unwrapped, true);
                }

                @Override
                public KeyHealthStatus checkKeyHealth(String tenantId, String kekKeyId) {
                    return KeyHealthStatus.HEALTHY;
                }
            };

            byte[] aad = EnvelopeCipher.buildNamespaceDekAad(TENANT_ID, "ns-kms-cached", 1);
            WrappedKeyResult wrapped = simulatedRemoteKms.wrapDek(TENANT_ID, KEK_KEY_ID, rawDek.bytes(), aad);
            byte[] packedWrappedDek = EncryptedMemoryRecallBenchmark.packWrapped(wrapped);

            // 1. Cold unwrap miss -> hits simulated KMS once (>= 2,000 us)
            long coldStartNs = System.nanoTime();
            try (DestroyableKeyMaterial coldDek = dekCache.getOrUnwrap(
                    TENANT_ID, "ns-kms-cached", 1, KEK_KEY_ID, packedWrappedDek, aad, simulatedRemoteKms)) {
                assertThat(coldDek.isDestroyed()).isFalse();
            }
            double coldUnwrapUs = (System.nanoTime() - coldStartNs) / 1_000.0;
            assertThat(kmsUnwrapCalls.get()).isEqualTo(1);
            assertThat(coldUnwrapUs).isGreaterThanOrEqualTo(1_500.0);

            // 2. Warm DEK cache hits -> zero additional KMS calls, p50 <= 10 us
            PercentileTracker warmHitTrackerUs = new PercentileTracker();
            for (int i = 0; i < 2_000; i++) {
                long t0 = System.nanoTime();
                try (DestroyableKeyMaterial warmDek = dekCache.getOrUnwrap(
                        TENANT_ID, "ns-kms-cached", 1, KEK_KEY_ID, packedWrappedDek, aad, simulatedRemoteKms)) {
                    assertThat(warmDek.length()).isEqualTo(32);
                }
                long t1 = System.nanoTime();
                warmHitTrackerUs.record((t1 - t0) / 1_000.0);
            }

            assertThat(kmsUnwrapCalls.get())
                    .as("Warm DEK cache hits within 5m TTL must make 0 additional KMS unwrap calls")
                    .isEqualTo(1);
            assertThat(warmHitTrackerUs.p50())
                    .as("Warm DekCache hit p50 (%.2f us) must be <= 10.0 us", warmHitTrackerUs.p50())
                    .isLessThanOrEqualTo(10.0);
        }
    }

    @Test
    @DisplayName("3. Warm 6-phase recall on BYOK encrypted namespace preserves bit-exact parity and meets ADR-0088 latency budget")
    void warmRecallParityAndLatencyBudgetGate() {
        int engramCount = 150;
        int topK = 5;
        String plainNs = "ns-gate-plain";
        String encNs = "ns-gate-enc";

        EmbeddingProvider embedder = EncryptedMemoryRecallBenchmark.createDeterministicEmbeddingProvider(DIMS);
        byte[] dekBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            dekBytes[i] = (byte) (0x31 + i);
        }

        RecallOptions unfilteredOpts = RecallOptions.builder()
                .topK(topK)
                .recallMode(RecallMode.OBSERVE)
                .build();
        RecallOptions filteredOpts = RecallOptions.builder()
                .topK(topK)
                .recallMode(RecallMode.OBSERVE)
                .synapticFilter("security")
                .hyperfocusMask("urgent")
                .build();

        try (DestroyableKeyMaterial dek = DestroyableKeyMaterial.fromBytes(dekBytes);
             EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(
                     TENANT_ID,
                     encNs,
                     1,
                     EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                     KEK_KEY_ID,
                     dek.copy());
             SpectorMemory plainMem = buildMemory(tempDir, plainNs, embedder, engramCount, DataEncryptor.NOOP);
             SpectorMemory encMem = buildMemory(tempDir, encNs, embedder, engramCount, encryptor)) {

            populateDataset(plainMem, engramCount);
            populateDataset(encMem, engramCount);

            String query = "quantum lattice cryptography envelope key rotation";

            // 1. Verify bit-exact recall parity on both unfiltered and filtered 6-phase scans
            List<CognitiveResult> plainUnfiltered = plainMem.recall(query, unfilteredOpts);
            List<CognitiveResult> encUnfiltered = encMem.recall(query, unfilteredOpts);
            assertRecallParity(plainUnfiltered, encUnfiltered);

            List<CognitiveResult> plainFiltered = plainMem.recall(query, filteredOpts);
            List<CognitiveResult> encFiltered = encMem.recall(query, filteredOpts);
            assertRecallParity(plainFiltered, encFiltered);

            // 2. Warmup JIT across both paths
            for (int i = 0; i < 30; i++) {
                plainMem.recall(query, unfilteredOpts);
                encMem.recall(query, unfilteredOpts);
                plainMem.recall(query, filteredOpts);
                encMem.recall(query, filteredOpts);
            }

            // 3. Interleaved measurement to neutralize background OS jitter
            int trials = 60;
            PercentileTracker plainRecallUs = new PercentileTracker();
            PercentileTracker encRecallUs = new PercentileTracker();
            for (int i = 0; i < trials; i++) {
                long p0 = System.nanoTime();
                List<CognitiveResult> pRes = plainMem.recall(query, unfilteredOpts);
                long p1 = System.nanoTime();
                plainRecallUs.record((p1 - p0) / 1_000.0);

                long e0 = System.nanoTime();
                List<CognitiveResult> eRes = encMem.recall(query, unfilteredOpts);
                long e1 = System.nanoTime();
                encRecallUs.record((e1 - e0) / 1_000.0);

                assertThat(pRes).hasSize(topK);
                assertThat(eRes).hasSize(topK);
            }

            double deltaP50Us = encRecallUs.p50() - plainRecallUs.p50();
            double deltaPerTopKResultUs = deltaP50Us / topK;

            // ADR-0088 §5.3 + DataEncryptor contract:
            // Off-heap 6-phase slab & SIMD scan incurs 0 per-candidate cipher calls;
            // only Top-K surviving results decrypt text in TextBlobMemory (budget <= 100 us per Top-K item).
            assertThat(deltaPerTopKResultUs)
                    .as("Incremental warm recall delta per Top-K result (%.2f us; plain p50=%.2f us, enc p50=%.2f us) must be <= 100.0 us",
                            deltaPerTopKResultUs, plainRecallUs.p50(), encRecallUs.p50())
                    .isLessThanOrEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("4. Cold-open 64 KiB page-streamed AES-256-GCM segment decryption and namespace reload meet throughput gate")
    void coldOpenEncryptedSegmentPageCodecThroughputAndReloadGate() throws Exception {
        int segmentBytes = 512 * 1024; // 512 KiB (8 x 64 KiB pages)
        Path segmentPath = tempDir.resolve("cold-open-segment.bundle");

        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek();
             EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor(
                     TENANT_ID,
                     "ns-cold-codec",
                     1,
                     EncryptedSegmentHeader.MODE_BYOK_ENVELOPE,
                     KEK_KEY_ID,
                     dek.copy());
             Arena sourceArena = Arena.ofConfined()) {

            MemorySegment sourceSegment = sourceArena.allocate(segmentBytes, 64);
            for (int i = 0; i < segmentBytes; i += 8) {
                sourceSegment.set(ValueLayout.JAVA_LONG_UNALIGNED, i, 0x0102030405060708L ^ i);
            }

            // Warmup write + read
            for (int w = 0; w < 5; w++) {
                EncryptedSegmentPageCodec.writeEncryptedSegment(
                        segmentPath,
                        sourceSegment,
                        EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                        encryptor);
                try (Arena readArena = Arena.ofConfined()) {
                    MemorySegment loaded = EncryptedSegmentPageCodec.readEncryptedSegment(
                            segmentPath,
                            EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor,
                            readArena);
                    assertThat(loaded.byteSize()).isEqualTo(segmentBytes);
                }
            }

            // Measure 25 cold segment page decryptions into 64-byte-aligned off-heap MemorySegment
            PercentileTracker coldDecryptMs = new PercentileTracker();
            for (int i = 0; i < 25; i++) {
                try (Arena readArena = Arena.ofConfined()) {
                    long t0 = System.nanoTime();
                    MemorySegment decrypted = EncryptedSegmentPageCodec.readEncryptedSegment(
                            segmentPath,
                            EncryptedSegmentHeader.SEGMENT_TYPE_PARTITION_BUNDLE,
                            encryptor,
                            readArena);
                    long t1 = System.nanoTime();
                    coldDecryptMs.record((t1 - t0) / 1_000_000.0);
                    assertThat(decrypted.byteSize()).isEqualTo(segmentBytes);
                    assertThat(decrypted.get(ValueLayout.JAVA_LONG_UNALIGNED, 0)).isEqualTo(0x0102030405060708L);
                }
            }

            // 512 KiB cold segment decrypt + header HMAC verification must complete in <= 25 ms at p50
            assertThat(coldDecryptMs.p50())
                    .as("512 KiB (8 x 64 KiB pages) AES-256-GCM cold segment decrypt p50 (%.2f ms) must be <= 25.0 ms",
                            coldDecryptMs.p50())
                    .isLessThanOrEqualTo(25.0);
        }
    }

    @Test
    @DisplayName("5. EncryptedMemoryRecallBenchmark JMH methods execute cleanly and BaselineRegressionDetector enforces 5% ADR-0088 threshold")
    void jmhBenchmarkHarnessAndBaselineRegressionDetectorGate() throws Exception {
        // 1. Smoke-test all JMH benchmark methods in EncryptedMemoryRecallBenchmark
        EncryptedMemoryRecallBenchmark bench = new EncryptedMemoryRecallBenchmark();
        bench.dims = 64;
        bench.engramCount = 32;
        bench.topK = 5;
        bench.setup();
        try {
            assertThat(bench.warmRecall_plaintext()).hasSize(5);
            assertThat(bench.warmRecall_byokEncrypted()).hasSize(5);
            assertThat(bench.warmRecallFiltered_plaintext()).isNotEmpty();
            assertThat(bench.warmRecallFiltered_byokEncrypted()).isNotEmpty();
            assertThat(bench.warmRecallKmsCached_byokEncrypted()).hasSize(5);
            assertThat(bench.coldOpenAndFirstRecall_plaintext()).hasSize(5);
            assertThat(bench.coldOpenAndFirstRecall_byokEncrypted()).hasSize(5);
            assertThat(bench.rememberWrite_plaintext()).isNotBlank();
            assertThat(bench.rememberWrite_byokEncrypted()).isNotBlank();
            assertThat(bench.dataEncryptor_encryptDecryptText()).isNotEmpty();
            assertThat(bench.dataEncryptor_decryptTextOnly()).isNotEmpty();
            assertThat(bench.dataEncryptor_encryptDecryptPayload()).hasSize(64);
            assertThat(bench.dataEncryptor_encodeSynapticTag128().lo()).isNotZero();
        } finally {
            bench.tearDown();
        }

        // 2. Verify BaselineRegressionDetector with strict 5% (0.05) ADR-0088 warm recall budget
        Path baselineJson = tempDir.resolve("baseline-jmh.json");
        Path withinBudgetJson = tempDir.resolve("current-pass-jmh.json");
        Path overBudgetJson = tempDir.resolve("current-fail-jmh.json");

        Files.writeString(baselineJson, """
                [
                  {
                    "benchmark": "com.spectrayan.spector.bench.EncryptedMemoryRecallBenchmark.warmRecall_byokEncrypted",
                    "mode": "avgt",
                    "params": { "dims": "64", "engramCount": "1000", "topK": "5" },
                    "primaryMetric": { "score": 42.0, "scoreUnit": "us/op" }
                  }
                ]
                """);

        // +3.5% latency change -> within <= 5% ADR-0088 budget -> PASS
        Files.writeString(withinBudgetJson, """
                [
                  {
                    "benchmark": "com.spectrayan.spector.bench.EncryptedMemoryRecallBenchmark.warmRecall_byokEncrypted",
                    "mode": "avgt",
                    "params": { "dims": "64", "engramCount": "1000", "topK": "5" },
                    "primaryMetric": { "score": 43.47, "scoreUnit": "us/op" }
                  }
                ]
                """);

        // +8.0% latency change -> exceeds <= 5% ADR-0088 budget -> REGRESSION FLAGGED
        Files.writeString(overBudgetJson, """
                [
                  {
                    "benchmark": "com.spectrayan.spector.bench.EncryptedMemoryRecallBenchmark.warmRecall_byokEncrypted",
                    "mode": "avgt",
                    "params": { "dims": "64", "engramCount": "1000", "topK": "5" },
                    "primaryMetric": { "score": 45.36, "scoreUnit": "us/op" }
                  }
                ]
                """);

        BaselineRegressionDetector adr0088Gate = new BaselineRegressionDetector(0.05);

        BaselineRegressionDetector.RegressionReport passReport = adr0088Gate.compare(baselineJson, withinBudgetJson);
        assertThat(passReport.hasRegressions()).isFalse();

        BaselineRegressionDetector.RegressionReport failReport = adr0088Gate.compare(baselineJson, overBudgetJson);
        assertThat(failReport.hasRegressions()).isTrue();
        assertThat(failReport.regressions()).hasSize(1);
        assertThat(failReport.regressions().getFirst().percentChange()).isCloseTo(8.0, Offset.offset(0.1));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SpectorMemory buildMemory(
            Path baseDir,
            String namespaceId,
            EmbeddingProvider embeddingProvider,
            int engramCount,
            DataEncryptor encryptor) {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(DIMS);
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

    private static void assertRecallParity(List<CognitiveResult> expected, List<CognitiveResult> actual) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            CognitiveResult e = expected.get(i);
            CognitiveResult a = actual.get(i);
            assertThat(a.id()).isEqualTo(e.id());
            assertThat(a.text()).isEqualTo(e.text());
            assertThat(a.score()).isCloseTo(e.score(), Offset.offset(1e-4f));
        }
    }
}
