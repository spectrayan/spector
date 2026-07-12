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

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SPLADE-style learned sparse retrieval index.
 *
 * <h3>Architecture</h3>
 * <p>Unlike {@link BM25Index} which computes term weights from corpus statistics
 * (TF-IDF), SpladeIndex accepts pre-computed neural term weights from a
 * {@link com.spectrayan.spector.embed.SparseEncodingProvider}. This enables
 * <b>learned term expansion</b> — the model discovers that "car" should also
 * match "vehicle" and "automobile", capturing semantic relationships that
 * BM25 misses entirely.</p>
 *
 * <h3>Scoring</h3>
 * <p>SPLADE scoring is inner-product between query and document sparse vectors:
 * <pre>
 *   score(Q, D) = Σ_t∈Q∩D  q_weight(t) × d_weight(t)
 * </pre>
 * This is fundamentally simpler than BM25 — no IDF, no document length
 * normalization — because the neural model has already learned to produce
 * properly calibrated weights.</p>
 *
 * <h3>Index Structure</h3>
 * <p>Uses the same struct-of-arrays inverted index as the optimized
 * {@link BM25Index}, but stores neural weights ({@code float}) instead of
 * raw term frequencies ({@code int}):
 * <pre>
 *   term → WeightedPostingList {
 *       int[] docIndices;       // document index
 *       float[] docWeights;     // neural weight for this term in this document
 *   }
 * </pre></p>
 *
 * <h3>Performance</h3>
 * <p>SPLADE vectors are typically very sparse (~100-200 non-zero terms per document
 * from a ~30K vocabulary), so the posting lists are short.</p>
 *
 * <p>For multi-term queries above the parallel threshold, each term's postings
 * are scored independently on virtual threads, then merged via
 * {@link SIMDScoreAccumulator#addArrays} — the same pattern used by
 * {@link BM25Index}. This provides SIMD-accelerated score merging
 * while the per-term scatter-access scoring loop remains scalar
 * (data-dependent indexing doesn't vectorize).</p>
 */
public class SpladeIndex implements KeywordIndex {

    private static final Logger log = LoggerFactory.getLogger(SpladeIndex.class);

    /** Threshold: use parallel term scoring when total postings exceed this.
     * Matches the strategy in {@link BM25Index} — virtual thread scheduling
     * overhead only pays off for large posting lists. */
    private static final int PARALLEL_POSTING_THRESHOLD = 15_000;

    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // ── Weighted inverted index ──
    private final Map<String, WeightedPostingList> invertedIndex;

    // ── Document metadata ──
    private final List<String> docIds;
    private final Map<String, Integer> docIdToIndex;
    private int totalDocs;

    /**
     * Struct-of-arrays weighted posting list for SPLADE scoring.
     *
     * <p>Stores neural weights as float[] instead of term frequencies as int[].
     * Layout optimized for cache-friendly sequential access during scoring.</p>
     */
    static final class WeightedPostingList {
        int[] docIndices;
        float[] docWeights;
        int size;

        WeightedPostingList() {
            this(16);
        }

        WeightedPostingList(int initialCapacity) {
            this.docIndices = new int[initialCapacity];
            this.docWeights = new float[initialCapacity];
            this.size = 0;
        }

        void add(int docIndex, float weight) {
            if (size == docIndices.length) {
                int newCap = docIndices.length * 2;
                docIndices = Arrays.copyOf(docIndices, newCap);
                docWeights = Arrays.copyOf(docWeights, newCap);
            }
            docIndices[size] = docIndex;
            docWeights[size] = weight;
            size++;
        }

        void removeByDocIndex(int docIndex) {
            for (int i = 0; i < size; i++) {
                if (docIndices[i] == docIndex) {
                    System.arraycopy(docIndices, i + 1, docIndices, i, size - i - 1);
                    System.arraycopy(docWeights, i + 1, docWeights, i, size - i - 1);
                    size--;
                    return;
                }
            }
        }
    }

    public SpladeIndex() {
        this.invertedIndex = new HashMap<>();
        this.docIds = new ArrayList<>();
        this.docIdToIndex = new HashMap<>();
        this.totalDocs = 0;
    }

    /**
     * Indexes a document using its pre-computed SPLADE sparse vector.
     *
     * @param id          the document identifier
     * @param sparseVec   SPLADE-encoded sparse vector: term → neural weight
     */
    public void indexSparse(String id, Map<String, Float> sparseVec) {
        rwLock.writeLock().lock();
        try {
            // Remove old entry if re-indexing
            if (docIdToIndex.containsKey(id)) {
                removeInternal(id);
            }

            int docIndex = docIds.size();
            docIds.add(id);
            docIdToIndex.put(id, docIndex);
            totalDocs++;

            for (var entry : sparseVec.entrySet()) {
                float weight = entry.getValue();
                if (weight > 0f) {
                    invertedIndex
                            .computeIfAbsent(entry.getKey(), k -> new WeightedPostingList())
                            .add(docIndex, weight);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Searches using a SPLADE-encoded query sparse vector.
     *
     * <p>Scoring is inner-product: for each term present in both query and a
     * document, multiply the query weight by the document weight and accumulate.</p>
     *
     * @param querySparse SPLADE-encoded query sparse vector
     * @param k           max results to return
     * @return array of scored results, sorted by relevance (best first)
     */
    public ScoredResult[] searchSparse(Map<String, Float> querySparse, int k) {
        rwLock.readLock().lock();
        try {
            return searchSparseInternal(querySparse, k);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Note:</b> This text-based index method is a compatibility shim.
     * For SPLADE, use {@link #indexSparse(String, Map)} with pre-computed
     * sparse vectors from a {@link com.spectrayan.spector.embed.SparseEncodingProvider}.</p>
     */
    @Override
    public void index(String id, String content) {
        // Compatibility shim: creates a trivial sparse vector from raw terms.
        // In production, callers should use indexSparse() with neural vectors.
        log.warn("SpladeIndex.index(String) called without sparse encoding — "
                + "using trivial term weights. Use indexSparse() for proper SPLADE indexing.");

        Map<String, Float> trivialSparse = new HashMap<>();
        for (String token : content.toLowerCase().split("\\W+")) {
            if (token.length() >= 2) {
                trivialSparse.merge(token, 1.0f, Float::sum);
            }
        }
        indexSparse(id, trivialSparse);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Note:</b> This text-based search method is a compatibility shim.
     * For SPLADE, use {@link #searchSparse(Map, int)} with a pre-computed
     * query sparse vector.</p>
     */
    @Override
    public ScoredResult[] search(String query, int k) {
        // Compatibility shim: trivial term extraction
        Map<String, Float> trivialQuery = new HashMap<>();
        for (String token : query.toLowerCase().split("\\W+")) {
            if (token.length() >= 2) {
                trivialQuery.merge(token, 1.0f, Float::sum);
            }
        }
        return searchSparse(trivialQuery, k);
    }

    @Override
    public int size() {
        return totalDocs;
    }

    @Override
    public void remove(String id) {
        rwLock.writeLock().lock();
        try {
            removeInternal(id);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        rwLock.writeLock().lock();
        try {
            invertedIndex.clear();
            docIds.clear();
            docIdToIndex.clear();
            totalDocs = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ─────────────── Internal scoring ───────────────

    private ScoredResult[] searchSparseInternal(Map<String, Float> querySparse, int k) {
        if (querySparse.isEmpty() || totalDocs == 0) {
            return new ScoredResult[0];
        }

        final int n = docIds.size();

        // ── Collect valid terms and estimate total postings ──
        int totalPostings = 0;
        List<Map.Entry<String, Float>> validTerms = new ArrayList<>(querySparse.size());
        for (var qEntry : querySparse.entrySet()) {
            WeightedPostingList postings = invertedIndex.get(qEntry.getKey());
            if (postings != null && postings.size > 0) {
                totalPostings += postings.size;
                validTerms.add(qEntry);
            }
        }
        if (validTerms.isEmpty()) {
            return new ScoredResult[0];
        }

        // ── Score: parallel + SIMD merge for large workloads, sequential otherwise ──
        float[] scores;
        if (validTerms.size() > 1 && totalPostings >= PARALLEL_POSTING_THRESHOLD) {
            scores = scoreTermsParallel(validTerms, n);
        } else {
            scores = scoreTermsSequential(validTerms, n);
        }

        // ── Top-K extraction via bounded min-heap ──
        return extractTopK(scores, n, k);
    }

    /**
     * Scores all terms sequentially into a single float[] array.
     */
    private float[] scoreTermsSequential(List<Map.Entry<String, Float>> terms, int n) {
        float[] scores = new float[n];
        for (var qEntry : terms) {
            accumulatePostings(qEntry.getKey(), qEntry.getValue(), scores);
        }
        return scores;
    }

    /**
     * Scores each term in parallel using virtual threads, then merges via SIMD.
     *
     * <p>Each term's postings are scored into a separate float[] on its own
     * virtual thread. The arrays are then merged with
     * {@link SIMDScoreAccumulator#addArrays} for SIMD-accelerated sequential
     * addition. This avoids contention on a shared scores array.</p>
     */
    private float[] scoreTermsParallel(List<Map.Entry<String, Float>> terms, int n) {
        List<Callable<float[]>> tasks = new ArrayList<>(terms.size());
        for (var qEntry : terms) {
            final String term = qEntry.getKey();
            final float queryWeight = qEntry.getValue();
            tasks.add(() -> {
                float[] termScores = new float[n];
                accumulatePostings(term, queryWeight, termScores);
                return termScores;
            });
        }

        float[] mergedScores = new float[n];

        try {
            List<float[]> results = ConcurrentTasks.forkJoinAll(tasks);

            // SIMD-accelerated merge: vectorized dst[i] += src[i]
            for (float[] termScores : results) {
                if (termScores != null) {
                    SIMDScoreAccumulator.addArrays(mergedScores, termScores, n);
                }
            }
        } catch (ConcurrentExecutionException e) {
            log.error("Parallel SPLADE scoring failed, falling back to sequential", e.getCause());
            return scoreTermsSequential(terms, n);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel SPLADE scoring interrupted");
        }

        return mergedScores;
    }

    /**
     * Inner scoring loop — accumulates weighted inner-product scores.
     *
     * <p>Iterates the posting list for a single term, multiplying query weight
     * by document weight and accumulating into the scores array. This is
     * inherently a scatter-access pattern ({@code scores[docIdx[i]]}) which
     * does not vectorize, so it remains scalar.</p>
     */
    private void accumulatePostings(String term, float queryWeight, float[] scores) {
        WeightedPostingList postings = invertedIndex.get(term);
        if (postings == null) return;

        final int sz = postings.size;
        final int[] docIdx = postings.docIndices;
        final float[] docWts = postings.docWeights;

        for (int i = 0; i < sz; i++) {
            scores[docIdx[i]] += queryWeight * docWts[i];
        }
    }

    /**
     * Extracts top-K results from the scores array using a bounded min-heap.
     */
    private ScoredResult[] extractTopK(float[] scores, int n, int k) {
        var heap = new NeighborQueue(Math.min(k, 64), k, true);
        for (int i = 0; i < n; i++) {
            if (scores[i] > 0f) {
                heap.add(i, scores[i]);
            }
        }

        int resultCount = heap.size();
        ScoredResult[] results = new ScoredResult[resultCount];
        for (int i = resultCount - 1; i >= 0; i--) {
            float score = heap.topScore();
            int idx = heap.poll();
            results[i] = new ScoredResult(docIds.get(idx), idx, score);
        }
        return results;
    }

    private void removeInternal(String id) {
        Integer idx = docIdToIndex.remove(id);
        if (idx != null) {
            totalDocs--;
            for (var postings : invertedIndex.values()) {
                postings.removeByDocIndex(idx);
            }
        }
    }
}
