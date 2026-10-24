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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.persist.StoreEmbeddingIdentity;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies startup embedding fingerprint validation and first-write persistence (#1159).
 */
class EmbeddingFingerprintStartupTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("First write records embedding fingerprint in namespace.json and reopening with same model succeeds")
    void sameModelOpensAndPreservesFingerprint() {
        Path storeDir = tempDir.resolve("same-model");
        EmbeddingProvider embedder = new FixedEmbedder("nomic-embed-text", 64);

        try (SpectorMemory memory = openStore(storeDir, embedder, false)) {
            assertThat(StoreEmbeddingIdentity.read(storeDir)).isEmpty();
            memory.remember("mem-1", "Spector cognitive memory", MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
            assertThat(StoreEmbeddingIdentity.read(storeDir))
                    .isPresent()
                    .get()
                    .satisfies(rec -> {
                        assertThat(rec.model()).isEqualTo("nomic-embed-text");
                        assertThat(rec.dimensions()).isEqualTo(64);
                    });
        }

        try (SpectorMemory reopened = openStore(storeDir, new FixedEmbedder("nomic-embed-text", 64), false)) {
            assertThat(reopened.inspect("mem-1")).isNotNull();
        }
    }

    @Test
    @DisplayName("Reopening with different embedding dimensions fails startup with EMBEDDING_FINGERPRINT_MISMATCH")
    void differentDimensionFailsWithNewErrorCode() {
        Path storeDir = tempDir.resolve("diff-dims");

        try (SpectorMemory memory = openStore(storeDir, new FixedEmbedder("nomic-embed-text", 64), false)) {
            memory.remember("mem-1", "vector at 64 dims", MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
        }

        assertThatThrownBy(() -> openStore(storeDir, new FixedEmbedder("text-embedding-3-small", 128), false))
                .isInstanceOf(SpectorConfigException.class)
                .satisfies(ex -> {
                    SpectorConfigException cfgEx = (SpectorConfigException) ex;
                    assertThat(cfgEx.errorCode()).isEqualTo(ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH);
                })
                .hasMessageContaining("nomic-embed-text (64d)")
                .hasMessageContaining("text-embedding-3-small (128d)")
                .hasMessageContaining("spector migrate reembed");

        // Store lock must have been released after failed open so the original config can reopen cleanly.
        try (SpectorMemory reopened = openStore(storeDir, new FixedEmbedder("nomic-embed-text", 64), false)) {
            assertThat(reopened.inspect("mem-1")).isNotNull();
        }
    }

    @Test
    @DisplayName("Reopening with same dimensions but different model fails startup unless migration flag is set")
    void sameDimensionDifferentModelFailsUnlessMigrationFlagSet() {
        Path storeDir = tempDir.resolve("same-dims-diff-model");

        try (SpectorMemory memory = openStore(storeDir, new FixedEmbedder("all-MiniLM-L6-v2", 64), false)) {
            memory.remember("mem-1", "mini-lm vector", MemoryType.SEMANTIC, MemorySource.OBSERVED, "test");
        }

        assertThatThrownBy(() -> openStore(storeDir, new FixedEmbedder("bge-small-en-v1.5", 64), false))
                .isInstanceOf(SpectorConfigException.class)
                .satisfies(ex -> {
                    SpectorConfigException cfgEx = (SpectorConfigException) ex;
                    assertThat(cfgEx.errorCode()).isEqualTo(ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH);
                })
                .hasMessageContaining("all-MiniLM-L6-v2 (64d)")
                .hasMessageContaining("bge-small-en-v1.5 (64d)")
                .hasMessageContaining("spector migrate reembed");

        // Explicit migration flag allows opening across a same-dimension model change.
        try (SpectorMemory migratedOpen = openStore(storeDir, new FixedEmbedder("bge-small-en-v1.5", 64), true)) {
            assertThat(migratedOpen.inspect("mem-1")).isNotNull();
        }
    }

    @Test
    @DisplayName("Legacy store without fingerprint checks inferred vector dimensions only")
    void legacyStoreWithoutFingerprintPerformsDimensionCheckOnly() throws Exception {
        Path storeDir = tempDir.resolve("legacy-store");

        try (SpectorMemory memory = openStore(storeDir, new FixedEmbedder("legacy-model-v1", 64), false)) {
            memory.remember("mem-1", "pre-fingerprint record", MemoryType.SEMANTIC, MemorySource.OBSERVED, "legacy");
        }

        // Simulate a legacy store created before embedding fingerprint fields existed in namespace.json
        Path markerFile = storeDir.resolve(StoragePaths.FILE_NAMESPACE);
        Files.writeString(markerFile, "{\n  \"id\": \"default\"\n}\n", StandardCharsets.UTF_8);

        // 1. Different dimensions on legacy store must be refused using inferred stride (stride - 64).
        assertThatThrownBy(() -> openStore(storeDir, new FixedEmbedder("new-model-v2", 128), false))
                .isInstanceOf(SpectorConfigException.class)
                .satisfies(ex -> {
                    SpectorConfigException cfgEx = (SpectorConfigException) ex;
                    assertThat(cfgEx.errorCode()).isEqualTo(ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH);
                })
                .hasMessageContaining("unknown (64d)")
                .hasMessageContaining("new-model-v2 (128d)")
                .hasMessageContaining("spector migrate reembed");

        // 2. Same dimensions on legacy store opens even when the model name differs, and preserves existing marker keys.
        try (SpectorMemory reopened = openStore(storeDir, new FixedEmbedder("new-model-v2", 64), false)) {
            assertThat(reopened.inspect("mem-1")).isNotNull();
            // Next write backfills the fingerprint while preserving existing namespace.json fields.
            reopened.remember("mem-2", "post-upgrade record", MemoryType.SEMANTIC, MemorySource.OBSERVED, "legacy");
        }

        String updatedMarker = Files.readString(markerFile, StandardCharsets.UTF_8);
        assertThat(updatedMarker).contains("\"id\": \"default\"");
        assertThat(StoreEmbeddingIdentity.read(storeDir))
                .isPresent()
                .get()
                .satisfies(rec -> {
                    assertThat(rec.model()).isEqualTo("new-model-v2");
                    assertThat(rec.dimensions()).isEqualTo(64);
                });
    }

    private static SpectorMemory openStore(Path dir, EmbeddingProvider embedder, boolean allowMismatch) {
        return SpectorMemory.builder()
                .persistenceMode(MemoryPersistenceMode.DISK)
                .persistence(dir)
                .embeddingProvider(embedder)
                .allowEmbeddingMismatch(allowMismatch)
                .build();
    }

    private static final class FixedEmbedder implements EmbeddingProvider {
        private final String model;
        private final int dims;

        FixedEmbedder(String model, int dims) {
            this.model = model;
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            float[] v = new float[dims];
            int hash = text != null ? text.hashCode() : 1;
            for (int i = 0; i < dims; i++) {
                v[i] = ((hash + i) % 19) / 19.0f;
            }
            return new EmbeddingResult(v, 4, model);
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return model;
        }
    }
}
