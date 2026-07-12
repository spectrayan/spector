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
package com.spectrayan.spector.index;

import com.spectrayan.spector.embed.TokenEmbeddingProvider;
import com.spectrayan.spector.embed.TokenEmbeddingResult;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ColBERT v2 late-interaction reranker with SIMD-accelerated MaxSim scoring.
 *
 * <h3>MaxSim Scoring</h3>
 * <pre>
 *   score(Q, D) = Σ_i  max_j  dot(q_i, d_j)
 *
 *   For each query token q_i, find the document token d_j with maximum
 *   dot-product similarity, then sum across all query tokens. This provides
 *   token-level grounding — the model verifies that specific query terms
 *   are actually present in the document.
 * </pre>
 *
 * <h3>SIMD Acceleration</h3>
 * <p>The inner dot-product loop uses {@link FloatVector} for SIMD acceleration.
 * With 128-dim ColBERT embeddings and AVX-512 (16 floats/lane), each dot product
 * takes ~8 SIMD iterations instead of 128 scalar multiplies + adds. The max-finding
 * across document tokens also benefits from vectorized comparison.</p>
 *
 * <h3>Architecture Position</h3>
 * <p>ColBERT is a <b>reranker</b>, not a first-stage retriever. It operates on
 * pre-fetched candidates from BM25/SPLADE/vector search. Typical flow:
 * <ol>
 *   <li>First-stage retrieval: top-50 candidates from BM25 + vector</li>
 *   <li>Encode query tokens via {@link TokenEmbeddingProvider}</li>
 *   <li>Encode each candidate's text via {@link TokenEmbeddingProvider}</li>
 *   <li>Compute MaxSim score for each candidate</li>
 *   <li>Re-sort by MaxSim score, return top-K</li>
 * </ol></p>
 *
 * <h3>Performance</h3>
 * <p>With 128-dim embeddings and AVX-512:
 * <ul>
 *   <li>Single dot product: ~8 SIMD iterations (~3ns)</li>
 *   <li>MaxSim for 10-token query × 200-token doc: ~600µs</li>
 *   <li>Reranking 50 candidates: ~30ms total</li>
 * </ul></p>
 */
public final class ColBERTReranker {

    private static final Logger log = LoggerFactory.getLogger(ColBERTReranker.class);

    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final boolean SIMD_AVAILABLE = SPECIES.length() >= 2;

    private final TokenEmbeddingProvider provider;
    private final ColBERTTokenCache cache;

    /**
     * A candidate for reranking.
     *
     * @param id             document identifier
     * @param text           document text (for re-encoding)
     * @param firstStageScore the score from first-stage retrieval
     */
    public record RerankCandidate(String id, String text, float firstStageScore) {}

    /**
     * A reranked result.
     *
     * @param id              document identifier
     * @param maxSimScore     ColBERT MaxSim score
     * @param firstStageScore original first-stage score
     * @param combinedScore   fused score: α·maxSim + (1-α)·firstStage
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
     * @param provider the token embedding provider (e.g., ONNX ColBERT v2)
     */
    public ColBERTReranker(TokenEmbeddingProvider provider) {
        this(provider, null);
    }

    /**
     * Creates a ColBERT reranker with an off-heap token cache.
     *
     * <p>When a cache is provided, document token embeddings are stored in off-heap
     * memory after first encoding and reused on subsequent queries. This eliminates
     * redundant ONNX inference for previously-seen documents.</p>
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
     * <p>Encodes the query and each candidate's text into per-token embeddings,
     * computes MaxSim score, and returns the top-K results sorted by combined score.</p>
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
                // Check off-heap cache first
                float[][] docEmbeddings = null;
                if (cache != null) {
                    docEmbeddings = cache.get(candidate.id());
                }

                if (docEmbeddings == null) {
                    // Cache miss — encode via provider
                    TokenEmbeddingResult docTokens = provider.encode(candidate.text());
                    if (docTokens.tokenCount() == 0) {
                        results.add(new RerankResult(candidate.id(), 0f,
                                candidate.firstStageScore(), oneMinusAlpha * candidate.firstStageScore()));
                        continue;
                    }
                    docEmbeddings = docTokens.embeddings();

                    // Populate cache
                    if (cache != null) {
                        cache.put(candidate.id(), docEmbeddings);
                    }
                }

                float maxSim = maxSimScore(queryTokens.embeddings(), docEmbeddings);

                // Normalize MaxSim to [0, 1] range (divide by query token count)
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

    /**
     * Computes the MaxSim score between query and document token embeddings.
     *
     * <p>MaxSim: for each query token, find the maximum dot product with any
     * document token, then sum across all query tokens.</p>
     *
     * <p>The inner dot product uses SIMD acceleration via {@link FloatVector}
     * when available, with automatic scalar fallback.</p>
     *
     * @param queryTokens  query token embeddings: [queryLen][dims]
     * @param docTokens    document token embeddings: [docLen][dims]
     * @return MaxSim score (sum of per-query-token max similarities)
     */
    static float maxSimScore(float[][] queryTokens, float[][] docTokens) {
        float totalScore = 0f;

        for (float[] qToken : queryTokens) {
            float maxDot = Float.NEGATIVE_INFINITY;

            for (float[] dToken : docTokens) {
                float dot = simdDotProduct(qToken, dToken);
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
     * SIMD-accelerated dot product between two vectors.
     *
     * <p>Uses {@link FloatVector} for bulk multiply-accumulate with scalar tail.
     * On AVX-512 with 128-dim vectors, this runs in ~8 SIMD iterations.</p>
     *
     * @param a first vector
     * @param b second vector (must be same length as a)
     * @return dot product: Σ a[i] × b[i]
     */
    static float simdDotProduct(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);

        if (SIMD_AVAILABLE && n >= SPECIES.length()) {
            return simdDotProductVectorized(a, b, n);
        } else {
            return scalarDotProduct(a, b, n);
        }
    }

    private static float simdDotProductVectorized(float[] a, float[] b, int n) {
        int i = 0;
        int limit = SPECIES.loopBound(n);

        FloatVector sum = FloatVector.zero(SPECIES);

        for (; i < limit; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            sum = va.fma(vb, sum); // fused multiply-add: sum += va * vb
        }

        float result = sum.reduceLanes(VectorOperators.ADD);

        // Scalar tail
        for (; i < n; i++) {
            result += a[i] * b[i];
        }

        return result;
    }

    private static float scalarDotProduct(float[] a, float[] b, int n) {
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Returns the token embedding provider used by this reranker.
     */
    public TokenEmbeddingProvider provider() {
        return provider;
    }
}
