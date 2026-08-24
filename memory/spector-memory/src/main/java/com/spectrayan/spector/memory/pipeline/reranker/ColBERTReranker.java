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
package com.spectrayan.spector.memory.pipeline.reranker;

import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.core.spi.MaxSimKernel;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.TokenEmbeddingResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ColBERT v2 late-interaction reranker with HAL-accelerated MaxSim scoring.
 *
 * <h3>MaxSim Scoring</h3>
 * <pre>
 *   score(Q, D) = sum_i  max_j  dot(q_i, d_j)
 * </pre>
 *
 * <h3>Hardware Acceleration</h3>
 * <p>Mathematical scoring is automatically dispatched to the active HAL compute engine
 * (Panama CPU SIMD or CUDA GPU) via {@link MaxSimKernel}.</p>
 */
public final class ColBERTReranker {

    private static final Logger log = LoggerFactory.getLogger(ColBERTReranker.class);

    private final TokenEmbeddingProvider provider;
    private final ColBERTTokenCache cache;

    /**
     * A candidate for reranking.
     *
     * @param id              document identifier
     * @param text            document text (for re-encoding)
     * @param firstStageScore the score from first-stage retrieval
     */
    public record RerankCandidate(String id, String text, float firstStageScore) {}

    /**
     * A reranked result.
     *
     * @param id              document identifier
     * @param maxSimScore     ColBERT MaxSim score
     * @param firstStageScore original first-stage score
     * @param combinedScore   fused score: alpha ·maxSim + (1-alpha) ·firstStage
     */
    public record RerankResult(String id, float maxSimScore,
                               float firstStageScore, float combinedScore)
            implements Comparable<RerankResult> {

        @Override
        public int compareTo(RerankResult other) {
            return Float.compare(other.combinedScore, this.combinedScore); // descending
        }
    }

    /**
     * Creates a ColBERT reranker with the given token embedding provider.
     *
     * @param provider the token embedding provider
     */
    public ColBERTReranker(TokenEmbeddingProvider provider) {
        this(provider, null);
    }

    /**
     * Creates a ColBERT reranker with an off-heap token cache.
     *
     * @param provider the token embedding provider
     * @param cache    optional off-heap token cache (null = no caching)
     */
    public ColBERTReranker(TokenEmbeddingProvider provider, ColBERTTokenCache cache) {
        this.provider = provider;
        this.cache = cache;
    }

    /**
     * Reranks candidates using ColBERT MaxSim scoring.
     *
     * @param query       the search query text
     * @param candidates  first-stage retrieval candidates
     * @param topK        number of results to return
     * @return reranked results sorted by combined score (descending)
     */
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates, int topK) {
        return rerank(query, candidates, topK, 0.7f);
    }

    /**
     * Reranks candidates using ColBERT MaxSim scoring with configurable fusion weight.
     *
     * @param query       the search query text
     * @param candidates  first-stage retrieval candidates
     * @param topK        number of results to return
     * @param alpha       ColBERT weight in combined score (0.0 = all first-stage, 1.0 = all ColBERT)
     * @return reranked results sorted by combined score (descending)
     */
    public List<RerankResult> rerank(String query, List<RerankCandidate> candidates,
                                     int topK, float alpha) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Encode query tokens
        TokenEmbeddingResult queryTokens = provider.encode(query);
        if (queryTokens.tokenCount() == 0) {
            log.warn("ColBERT: query produced 0 tokens, returning first-stage order");
            return candidates.stream()
                    .map(c -> new RerankResult(c.id(), 0f, c.firstStageScore(), c.firstStageScore()))
                    .sorted()
                    .limit(topK)
                    .toList();
        }

        // Score each candidate
        List<RerankResult> results = new ArrayList<>(candidates.size());
        float oneMinusAlpha = 1.0f - alpha;

        for (RerankCandidate candidate : candidates) {
            try {
                float[][] docEmbeddings = null;
                if (cache != null) {
                    docEmbeddings = cache.get(candidate.id());
                }

                if (docEmbeddings == null) {
                    TokenEmbeddingResult docTokens = provider.encode(candidate.text());
                    if (docTokens.tokenCount() == 0) {
                        results.add(new RerankResult(candidate.id(), 0f,
                                candidate.firstStageScore(), oneMinusAlpha * candidate.firstStageScore()));
                        continue;
                    }
                    docEmbeddings = docTokens.embeddings();

                    if (cache != null) {
                        cache.put(candidate.id(), docEmbeddings);
                    }
                }

                float maxSim = computeMaxSim(queryTokens.embeddings(), docEmbeddings);
                float normalizedMaxSim = maxSim / queryTokens.tokenCount();
                float combined = alpha * normalizedMaxSim + oneMinusAlpha * candidate.firstStageScore();

                results.add(new RerankResult(candidate.id(), normalizedMaxSim,
                        candidate.firstStageScore(), combined));
            } catch (Exception e) {
                log.warn("ColBERT: failed to encode candidate '{}', keeping first-stage score",
                        candidate.id(), e);
                results.add(new RerankResult(candidate.id(), 0f,
                        candidate.firstStageScore(), oneMinusAlpha * candidate.firstStageScore()));
            }
        }

        results.sort(Comparator.naturalOrder());
        return results.subList(0, Math.min(topK, results.size()));
    }

    private static float computeMaxSim(float[][] queryTokens, float[][] docTokens) {
        MaxSimKernel kernel = AcceleratorRegistry.getKernel(MaxSimKernel.class);
        if (kernel != null) {
            return kernel.maxSim(queryTokens, docTokens);
        }
        return scalarMaxSim(queryTokens, docTokens);
    }

    private static float scalarMaxSim(float[][] queryTokens, float[][] docTokens) {
        float totalScore = 0f;
        for (float[] qToken : queryTokens) {
            float maxDot = Float.NEGATIVE_INFINITY;
            for (float[] dToken : docTokens) {
                float dot = 0f;
                int n = Math.min(qToken.length, dToken.length);
                for (int i = 0; i < n; i++) {
                    dot += qToken[i] * dToken[i];
                }
                if (dot > maxDot) {
                    maxDot = dot;
                }
            }
            if (maxDot > Float.NEGATIVE_INFINITY) {
                totalScore += maxDot;
            }
        }
        return totalScore;
    }

    /**
     * Returns the token embedding provider used by this reranker.
     */
    public TokenEmbeddingProvider provider() {
        return provider;
    }
}
