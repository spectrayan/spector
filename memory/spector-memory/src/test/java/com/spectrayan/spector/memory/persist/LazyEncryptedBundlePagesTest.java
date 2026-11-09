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
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.storage.EncryptedSegmentHeader;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies lazy per-page decryption and dirty-page tracking for encrypted bundles (#1177).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class LazyEncryptedBundlePagesTest {

    private static final int DIMS = 64;
    private static final int PAGE_OVERHEAD_BYTES = EnvelopeCipher.GCM_NONCE_BYTES + EnvelopeCipher.GCM_TAG_BYTES;

    @TempDir
    Path tempDir;

    private static EmbeddingProvider deterministicEmbeddingProvider() {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
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
                return EmbeddingResult.of(v, "deterministic-64d");
            }

            @Override
            public List<EmbeddingResult> embedBatch(List<String> texts) {
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

    private static int countChangedPages(byte[] before, byte[] after) {
        int pageFrameBytes = EncryptedSegmentHeader.DEFAULT_PAGE_SIZE + PAGE_OVERHEAD_BYTES;
        int totalFrames = (before.length - EncryptedSegmentHeader.HEADER_BYTES + pageFrameBytes - 1) / pageFrameBytes;
        int changed = 0;
        for (int p = 0; p < totalFrames; p++) {
            int start = EncryptedSegmentHeader.HEADER_BYTES + p * pageFrameBytes;
            int end = Math.min(before.length, start + pageFrameBytes);
            if (!Arrays.equals(before, start, end, after, start, end)) {
                changed++;
            }
        }
        return changed;
    }

    @Test
    @DisplayName("Opening an encrypted RuntimeBundle decrypts only the directory page and defers region pages until accessed")
    void lazyDecryptionDefersUntouchedRegionUntilAccessed() throws Exception {
        Path bundlePath = tempDir.resolve("lazy-runtime.bundle");
        List<RegionSizeSpec> specs = List.of(
                new RegionSizeSpec(RegionId.WORKING, 64 * 1024L, 128, 128, 0x574F524B, 1, false),
                new RegionSizeSpec(RegionId.INSULA, 64 * 1024L, 64, 64, 0x494E5355, 1, false),
                new RegionSizeSpec(RegionId.PROVENANCE, 64 * 1024L, 256, 64, 0x50524F56, 1, false)
        );

        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor("tenant-lazy", "ns-lazy", 1, dek);

            long provenanceOffset;
            try (RuntimeBundle bundle = RuntimeBundle.Init.mmap(bundlePath, specs, encryptor)) {
                MemorySegment working = bundle.currentSlice(RegionId.WORKING);
                long now = System.currentTimeMillis();
                RegionPreamble.write(working, 0L, 1, MemoryShape.RECORD, 1, 128, 1, 128, 0x574F524B, now, now);
                working.set(ValueLayout.JAVA_LONG_UNALIGNED, RegionPreamble.PREAMBLE_BYTES, 0xCAFEBABE12345678L);

                MemorySegment provenance = bundle.currentSlice(RegionId.PROVENANCE);
                RegionPreamble.write(provenance, 0L, 1, MemoryShape.RECORD, 1, 256, 1, 64, 0x50524F56, now, now);
                provenance.set(ValueLayout.JAVA_LONG_UNALIGNED, RegionPreamble.PREAMBLE_BYTES, 0xDEADBEEF87654321L);

                RegionEntry provEntry = bundle.directory().findRegion(RegionId.PROVENANCE);
                provenanceOffset = provEntry.offset();
                bundle.flush();
            }

            // Corrupt 1 byte inside PROVENANCE's encrypted page on disk, leaving page 0 and WORKING intact.
            int pageSize = EncryptedSegmentHeader.DEFAULT_PAGE_SIZE;
            int pageFrameBytes = pageSize + PAGE_OVERHEAD_BYTES;
            int provPageIndex = (int) (provenanceOffset / pageSize);
            assertThat(provPageIndex).isGreaterThan(1);

            byte[] diskBytes = Files.readAllBytes(bundlePath);
            int corruptPos = EncryptedSegmentHeader.HEADER_BYTES + provPageIndex * pageFrameBytes + 32;
            diskBytes[corruptPos] ^= (byte) 0x5A;
            Files.write(bundlePath, diskBytes);

            // Opening the bundle must succeed because only page 0 (BundleDirectory) is decrypted on open.
            try (RuntimeBundle reopened = RuntimeBundle.Init.open(bundlePath, encryptor)) {
                assertThat(reopened.decryptedPageCount()).isEqualTo(1);

                // Reading WORKING must succeed without touching PROVENANCE's corrupted page.
                MemorySegment working = reopened.currentSlice(RegionId.WORKING);
                assertThat(working.get(ValueLayout.JAVA_LONG_UNALIGNED, RegionPreamble.PREAMBLE_BYTES))
                        .isEqualTo(0xCAFEBABE12345678L);

                // Accessing PROVENANCE must now decrypt its page and fail closed on the tampered auth tag.
                assertThatThrownBy(() -> reopened.currentSlice(RegionId.PROVENANCE))
                        .isInstanceOf(SpectorMemoryException.class)
                        .satisfies(ex -> assertThat(((SpectorMemoryException) ex).errorCode())
                                .isEqualTo(ErrorCode.MEMORY_AUTH_TAG_MISMATCH));
            }
        }
    }

    @Test
    @DisplayName("Clean open, read-only recall, and close does not rewrite encrypted bundle pages; partial mutation flushes only dirty pages")
    void cleanCloseDoesNotRewriteAndPartialMutationFlushesOnlyDirtyPages() throws Exception {
        String namespaceId = "ns-clean-close";
        try (DestroyableKeyMaterial dek = EnvelopeCipher.generateDek()) {
            EnvelopeDataEncryptor encryptor = new EnvelopeDataEncryptor("tenant-clean", namespaceId, 1, dek);

            try (SpectorMemory memory = buildMemory(tempDir, namespaceId, encryptor)) {
                memory.remember("m-1", "Quantum lattice key exchange protocol specification",
                        MemoryType.SEMANTIC, MemorySource.USER_STATED, "crypto", "quantum");
                memory.remember("m-2", "Active incident triage checklist for shard rebalance",
                        MemoryType.WORKING, MemorySource.OBSERVED, "ops", "incident");
                memory.remember("m-3", "Runbook: verify envelope DEK version before rotation",
                        MemoryType.PROCEDURAL, MemorySource.PROCEDURAL, "runbook", "security");
            }

            Path nsDir = tempDir.resolve("namespaces").resolve(namespaceId);
            Path runtimeBundlePath = StoragePaths.runtimeBundleFile(nsDir);
            Path partitionBundlePath = findActivePartitionBundle(nsDir);

            byte[] runtimeBefore = Files.readAllBytes(runtimeBundlePath);
            byte[] partitionBefore = Files.readAllBytes(partitionBundlePath);

            RecallOptions observeOpts = RecallOptions.builder()
                    .topK(5)
                    .recallMode(RecallMode.OBSERVE)
                    .build();

            long t0 = System.nanoTime();
            int iterations = 5;
            for (int i = 0; i < iterations; i++) {
                try (SpectorMemory reopened = buildMemory(tempDir, namespaceId, encryptor)) {
                    List<CognitiveResult> results = reopened.recall("quantum lattice key exchange", observeOpts);
                    assertThat(results).isNotEmpty();
                }
            }
            double totalMs = (System.nanoTime() - t0) / 1_000_000.0;

            byte[] runtimeAfterCleanClose = Files.readAllBytes(runtimeBundlePath);
            byte[] partitionAfterCleanClose = Files.readAllBytes(partitionBundlePath);

            int runtimeChangedOnCleanClose = countChangedPages(runtimeBefore, runtimeAfterCleanClose);
            int partitionChangedOnCleanClose = countChangedPages(partitionBefore, partitionAfterCleanClose);

            assertThat(runtimeChangedOnCleanClose)
                    .as("Clean open + read + close must not rewrite any encrypted pages in runtime.bundle")
                    .isZero();
            assertThat(partitionChangedOnCleanClose)
                    .as("Clean open + read + close must not rewrite any encrypted pages in partition.bundle")
                    .isZero();
            assertThat(runtimeAfterCleanClose).isEqualTo(runtimeBefore);
            assertThat(partitionAfterCleanClose).isEqualTo(partitionBefore);

            // Now mutate a single region in RuntimeBundle and verify flush() re-encrypts only that dirty page.
            try (RuntimeBundle rb = RuntimeBundle.Init.open(runtimeBundlePath, encryptor)) {
                MemorySegment insula = rb.currentSlice(RegionId.INSULA);
                long prev = insula.get(ValueLayout.JAVA_LONG_UNALIGNED, RegionPreamble.PREAMBLE_BYTES);
                insula.set(ValueLayout.JAVA_LONG_UNALIGNED, RegionPreamble.PREAMBLE_BYTES, prev ^ 0x1122334455667788L);
                rb.flush();

                assertThat(rb.lastFlushDirtyPages()).isEqualTo(1);
            }

            byte[] runtimeAfterSinglePageEdit = Files.readAllBytes(runtimeBundlePath);
            int changedAfterSingleEdit = countChangedPages(runtimeBefore, runtimeAfterSinglePageEdit);
            assertThat(changedAfterSingleEdit)
                    .as("Mutating one region in RuntimeBundle should re-encrypt only that region's dirty page")
                    .isEqualTo(1);
        }
    }
}
