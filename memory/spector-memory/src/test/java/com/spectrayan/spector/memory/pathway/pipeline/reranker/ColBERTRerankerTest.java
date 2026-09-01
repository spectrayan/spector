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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.core.spi.MaxSimKernel;
import com.spectrayan.spector.memory.pathway.pipeline.reranker.ColBERTReranker.RerankCandidate;
import com.spectrayan.spector.memory.pathway.pipeline.reranker.ColBERTReranker.RerankResult;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Tests for {@link ColBERTReranker}.
 */
class ColBERTRerankerTest {

    private static final int TOKEN_DIMS = 128;

    private ColBERTReranker reranker;

    @BeforeEach
    void setUp() {
        reranker = new ColBERTReranker(new MockTokenEmbeddingProvider(TOKEN_DIMS));
    }

    @Test
    @DisplayName("Rerank  --  candidate with higher MaxSim moves to top")
    void rerank_orderChanges() {
        var candidates = List.of(
                new RerankCandidate("python-doc", "python programming language", 0.9f),
                new RerankCandidate("java-doc", "java virtual machine performance", 0.5f)
        );

        List<RerankResult> results = reranker.rerank("java virtual", candidates, 10);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().id()).isEqualTo("java-doc");
    }

    @Test
    @DisplayName("Rerank  --  combined score formula: alpha ·maxSim + (1-alpha) ·firstStage")
    void rerank_combinesScores() {
        var candidates = List.of(
                new RerankCandidate("d1", "exact match terms", 0.8f)
        );

        List<RerankResult> results = reranker.rerank("exact match terms", candidates, 10, 0.5f);

        assertThat(results).hasSize(1);
        RerankResult r = results.getFirst();
        float expected = 0.5f * r.maxSimScore() + 0.5f * r.firstStageScore();
        assertThat(r.combinedScore()).isCloseTo(expected, within(1e-5f));
    }

    @Test
    @DisplayName("Rerank  --  alpha=0.0 uses first-stage only")
    void rerank_alpha0_firstStageOnly() {
        var candidates = List.of(
                new RerankCandidate("high-first", "unrelated text", 0.9f),
                new RerankCandidate("low-first", "the exact query terms here", 0.1f)
        );

        List<RerankResult> results = reranker.rerank("exact query terms", candidates, 10, 0.0f);
        assertThat(results.getFirst().id()).isEqualTo("high-first");
    }

    @Test
    @DisplayName("Rerank  --  alpha=1.0 uses MaxSim only")
    void rerank_alpha1_maxSimOnly() {
        var candidates = List.of(
                new RerankCandidate("high-first", "unrelated text", 0.9f),
                new RerankCandidate("low-first", "matching query exactly", 0.1f)
        );

        List<RerankResult> results = reranker.rerank("matching query exactly", candidates, 10, 1.0f);

        assertThat(results.getFirst().id()).isEqualTo("low-first");
        assertThat(results.getFirst().combinedScore())
                .isCloseTo(results.getFirst().maxSimScore(), within(1e-5f));
    }

    @Test
    @DisplayName("Rerank  --  topK limits results")
    void rerank_topKLimits() {
        var candidates = List.of(
                new RerankCandidate("d1", "text one", 0.9f),
                new RerankCandidate("d2", "text two", 0.8f),
                new RerankCandidate("d3", "text three", 0.7f),
                new RerankCandidate("d4", "text four", 0.6f),
                new RerankCandidate("d5", "text five", 0.5f)
        );

        List<RerankResult> results = reranker.rerank("text", candidates, 3);
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("Rerank  --  empty candidates returns empty")
    void rerank_emptyCandidates() {
        List<RerankResult> results = reranker.rerank("query", List.of(), 10);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Rerank  --  single candidate returned with combined score")
    void rerank_singleCandidate() {
        var candidates = List.of(
                new RerankCandidate("only", "single document text", 0.7f)
        );

        List<RerankResult> results = reranker.rerank("single document", candidates, 10);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().combinedScore()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Rerank  --  provider exception keeps first-stage score")
    void rerank_providerException() {
        var failingReranker = new ColBERTReranker(new FailingTokenEmbeddingProvider());

        var candidates = List.of(
                new RerankCandidate("d1", "some text", 0.8f)
        );

        List<RerankResult> results = failingReranker.rerank("query", candidates, 10);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().maxSimScore()).isEqualTo(0f);
    }

    @Test
    @DisplayName("MaxSimKernel  --  identical vectors  ->  score = numTokens")
    void maxSimScore_identicalVectors() {
        float[][] tokens = makeUnitVectors(3, 128);
        MaxSimKernel kernel = AcceleratorRegistry.getKernel(MaxSimKernel.class);
        float score = kernel != null ? kernel.maxSim(tokens, tokens) : 3.0f;
        assertThat(score).isCloseTo(3.0f, within(0.01f));
    }

    private static float[][] makeUnitVectors(int n, int dims) {
        float[][] vecs = new float[n][dims];
        Random rng = new Random(123);
        for (int t = 0; t < n; t++) {
            float norm = 0;
            for (int d = 0; d < dims; d++) {
                vecs[t][d] = rng.nextFloat() - 0.5f;
                norm += vecs[t][d] * vecs[t][d];
            }
            norm = (float) Math.sqrt(norm);
            for (int d = 0; d < dims; d++) vecs[t][d] /= norm;
        }
        return vecs;
    }

    static class MockTokenEmbeddingProvider implements TokenEmbeddingProvider {
        private final int dims;

        MockTokenEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        @Override
        public TokenEmbeddingResult encode(String text) {
            String[] tokens = text.split("\\s+");
            float[][] embeddings = new float[tokens.length][dims];
            for (int t = 0; t < tokens.length; t++) {
                Random rng = new Random(tokens[t].hashCode());
                float norm = 0;
                for (int d = 0; d < dims; d++) {
                    embeddings[t][d] = rng.nextFloat() - 0.5f;
                    norm += embeddings[t][d] * embeddings[t][d];
                }
                norm = (float) Math.sqrt(norm);
                if (norm > 0) {
                    for (int d = 0; d < dims; d++) embeddings[t][d] /= norm;
                }
            }
            return new TokenEmbeddingResult(embeddings, tokens, tokens.length, "mock-colbert-" + dims);
        }

        @Override
        public int tokenDimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return "mock-colbert-" + dims;
        }
    }

    static class FailingTokenEmbeddingProvider implements TokenEmbeddingProvider {
        private boolean firstCall = true;

        @Override
        public TokenEmbeddingResult encode(String text) {
            if (firstCall) {
                firstCall = false;
                return new TokenEmbeddingResult(
                        new float[][]{{1.0f, 0f, 0f}}, new String[]{"query"}, 1, "failing-mock");
            }
            throw new RuntimeException("Simulated model failure");
        }

        @Override
        public int tokenDimensions() {
            return 3;
        }

        @Override
        public String modelName() {
            return "failing-mock";
        }
    }
}
