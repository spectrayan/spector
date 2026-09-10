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
package com.spectrayan.spector.memory.pathway.pipeline.reranker;

import com.spectrayan.spector.kernel.api.KernelSpec;
import com.spectrayan.spector.kernel.api.NamespaceKernel;
import com.spectrayan.spector.kernel.api.NamespaceKernels;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verification test for Task 7.5: Cache-bleed test across multi-tenant namespaces (R13.6).
 *
 * <p>Validates that when two distinct namespaces cache the same {@code docId}, each namespace's
 * {@link ColBERTTokenCache} is backed by its own kernel {@code ScratchMemory}, ensuring zero cross-tenant
 * cache leakage even when accessed through shared pathway components.</p>
 */
@DisplayName("Task 7.5 Cache-bleed verification test (R13.6)")
class CacheBleedVerificationTest {

    @Test
    @DisplayName("Same docId in two namespaces must never bleed token vectors across namespaces")
    void testNoCacheBleedAcrossNamespaces(@TempDir Path tempDir) {
        Path dirA = tempDir.resolve("ns-alpha");
        Path dirB = tempDir.resolve("ns-beta");

        KernelSpec spec = KernelSpec.standard(4);

        try (NamespaceKernel kernelA = NamespaceKernels.open(dirA, spec);
             NamespaceKernel kernelB = NamespaceKernels.open(dirB, spec)) {

            ColBERTTokenCache cacheA = new ColBERTTokenCache(kernelA.scratch().tokenTable(100, 16, 4));
            ColBERTTokenCache cacheB = new ColBERTTokenCache(kernelB.scratch().tokenTable(100, 16, 4));

            final String sharedDocId = "doc-alpha-beta-collision";

            // Vectors for Namespace A: orthogonal to Namespace B
            float[][] vectorsA = new float[][]{
                    {1.0f, 0.0f, 0.0f, 0.0f},
                    {0.0f, 1.0f, 0.0f, 0.0f}
            };

            // Vectors for Namespace B: orthogonal to Namespace A
            float[][] vectorsB = new float[][]{
                    {0.0f, 0.0f, 1.0f, 0.0f},
                    {0.0f, 0.0f, 0.0f, 1.0f}
            };

            // Put vectors into respective caches
            cacheA.put(sharedDocId, vectorsA);
            cacheB.put(sharedDocId, vectorsB);

            // Verify Cache A returns vectorsA exactly
            float[][] retrievedA = cacheA.get(sharedDocId);
            assertThat(retrievedA).isNotNull();
            assertThat(retrievedA).hasDimensions(2, 4);
            assertThat(retrievedA[0][0]).isCloseTo(1.0f, within(1e-6f));
            assertThat(retrievedA[0][2]).isCloseTo(0.0f, within(1e-6f));
            assertThat(retrievedA[1][1]).isCloseTo(1.0f, within(1e-6f));
            assertThat(retrievedA[1][3]).isCloseTo(0.0f, within(1e-6f));

            // Verify Cache B returns vectorsB exactly
            float[][] retrievedB = cacheB.get(sharedDocId);
            assertThat(retrievedB).isNotNull();
            assertThat(retrievedB).hasDimensions(2, 4);
            assertThat(retrievedB[0][0]).isCloseTo(0.0f, within(1e-6f));
            assertThat(retrievedB[0][2]).isCloseTo(1.0f, within(1e-6f));
            assertThat(retrievedB[1][1]).isCloseTo(0.0f, within(1e-6f));
            assertThat(retrievedB[1][3]).isCloseTo(1.0f, within(1e-6f));

            // Verify with a shared reranker instance
            TokenEmbeddingProvider mockProvider = new TokenEmbeddingProvider() {
                @Override
                public String modelName() { return "mock"; }
                @Override
                public int tokenDimensions() { return 4; }
                @Override
                public TokenEmbeddingResult encode(String text) {
                    // Query text "query-A" aligns with vectorsA
                    if ("query-A".equals(text)) {
                        return new TokenEmbeddingResult(new float[][]{{1.0f, 0.0f, 0.0f, 0.0f}}, new String[]{"query-A"}, 1, "mock");
                    }
                    return new TokenEmbeddingResult(new float[][]{{0.0f, 0.0f, 0.0f, 1.0f}}, new String[]{text}, 1, "mock");
                }
            };

            ColBERTReranker rerankerA = new ColBERTReranker(mockProvider, cacheA);
            ColBERTReranker rerankerB = new ColBERTReranker(mockProvider, cacheB);

            List<ColBERTReranker.RerankCandidate> candidates = List.of(
                    new ColBERTReranker.RerankCandidate(sharedDocId, "candidate content", 0.5f)
            );

            // Reranking in namespace A: "query-A" matches vectorsA[0] perfectly -> MaxSim = 1.0
            List<ColBERTReranker.RerankResult> resultsA = rerankerA.rerank("query-A", candidates, 1, 1.0f);
            assertThat(resultsA).hasSize(1);
            assertThat(resultsA.get(0).maxSimScore()).isCloseTo(1.0f, within(1e-5f));

            // Reranking in namespace B with the exact same query and docId:
            // vectorsB has {0, 0, 1, 0} and {0, 0, 0, 1}, dot product with {1, 0, 0, 0} is 0.0!
            List<ColBERTReranker.RerankResult> resultsB = rerankerB.rerank("query-A", candidates, 1, 1.0f);
            assertThat(resultsB).hasSize(1);
            assertThat(resultsB.get(0).maxSimScore()).isCloseTo(0.0f, within(1e-5f));

            // Mutating / clearing cacheA does not impact cacheB
            cacheA.clear();
            assertThat(cacheA.get(sharedDocId)).isNull();
            assertThat(cacheB.get(sharedDocId)).isNotNull();
            assertThat(cacheB.size()).isEqualTo(1);
        }
    }
}
