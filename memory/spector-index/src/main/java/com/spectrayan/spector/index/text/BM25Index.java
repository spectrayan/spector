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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BM25-scored inverted index for keyword search.
 *
 * <p>Implements the Okapi BM25 ranking function over an inverted index.
 * Documents are analyzed via a pluggable {@link Analyzer} and stored as
 * posting lists mapping terms to document IDs and term frequencies.</p>
 *
 * <h3>BM25 Formula</h3>
 * <pre>
 *   score(D, Q) = Σ IDF(qi) · (f(qi, D) · (k1 + 1)) / (f(qi, D) + k1 · (1 - b + b · |D|/avgdl))
 *
 *   IDF(qi) = ln((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
 * </pre>
 *
 * <h3>Performance Optimizations</h3>
 * <ul>
 *   <li><b>float[] score array</b> — eliminates HashMap boxing overhead for O(1) accumulation</li>
 *   <li><b>Bounded min-heap top-K</b> — O(N log K) via NeighborQueue instead of O(N log N) full sort</li>
 *   <li><b>int[] docLengths</b> — primitive array for cache-friendly access during scoring</li>
 *   <li><b>Parallel term scoring</b> — multi-term queries scored in parallel via virtual threads</li>
 *   <li><b>ReadWriteLock</b> — concurrent reads during search, exclusive writes during indexing</li>
 * </ul>
 *
 * <p>Default parameters: k1 = 1.2, b = 0.75</p>
 */
public class BM25Index implements KeywordIndex {

    private static final Logger log = LoggerFactory.getLogger(BM25Index.class);

    /** Threshold: use parallel term scoring when total postings exceed this.
     * Set conservatively — virtual thread scheduling overhead only pays off
     * for large posting lists. Below 20K, sequential scoring is faster. */
    private static final int PARALLEL_POSTING_THRESHOLD = 20_000;

    private final Analyzer analyzer;
    private final float k1;
    private final float b;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // ── Inverted index ──
    private final Map<String, PostingList> invertedIndex;  // term → postings

    // ── Document metadata ──
    private final List<String> docIds;               // index → doc ID
    private final Map<String, Integer> docIdToIndex;  // doc ID → index
    private int[] docLengthsArray;                   // index → doc length (primitive array)
    private int docLengthsCapacity;
    private long totalDocLength;  // running total for O(1) avg computation
    private double avgDocLength;
    private int totalDocs;

    /**
     * Struct-of-arrays posting list for cache-friendly access.
     *
     * <p>Replaces {@code List<Posting>} with parallel primitive arrays.
     * This eliminates pointer chasing (no object headers, no indirection per posting)
     * and enables sequential memory access during scoring — a ~1.5-2x speedup
     * from improved cache line utilization and hardware prefetching.</p>
     */
    static final class PostingList {
        int[] docIndices;
        int[] termFrequencies;
        int size;

        PostingList() {
            this(16);
        }

        PostingList(int initialCapacity) {
            this.docIndices = new int[initialCapacity];
            this.termFrequencies = new int[initialCapacity];
            this.size = 0;
        }

        void add(int docIndex, int termFrequency) {
            if (size == docIndices.length) {
                int newCap = docIndices.length * 2;
                docIndices = java.util.Arrays.copyOf(docIndices, newCap);
                termFrequencies = java.util.Arrays.copyOf(termFrequencies, newCap);
            }
            docIndices[size] = docIndex;
            termFrequencies[size] = termFrequency;
            size++;
        }

        void removeByDocIndex(int docIndex) {
            for (int i = 0; i < size; i++) {
                if (docIndices[i] == docIndex) {
                    // Compact: shift remaining elements left
                    System.arraycopy(docIndices, i + 1, docIndices, i, size - i - 1);
                    System.arraycopy(termFrequencies, i + 1, termFrequencies, i, size - i - 1);
                    size--;
                    return;
                }
            }
        }
    }

    /**
     * Creates a BM25 index with a custom analyzer and parameters.
     *
     * @param analyzer the text analyzer
     * @param k1       term frequency saturation parameter (default 1.2)
     * @param b        document length normalization parameter (default 0.75)
     */
    public BM25Index(Analyzer analyzer, float k1, float b) {
        this.analyzer = analyzer;
        this.k1 = k1;
        this.b = b;
        this.invertedIndex = new HashMap<>();
        this.docIds = new ArrayList<>();
        this.docIdToIndex = new HashMap<>();
        this.docLengthsCapacity = 1024;
        this.docLengthsArray = new int[docLengthsCapacity];
        this.totalDocLength = 0;
        this.avgDocLength = 0;
        this.totalDocs = 0;
    }

    /** Creates a BM25 index with default parameters (k1=1.2, b=0.75). */
    public BM25Index(Analyzer analyzer) {
        this(analyzer, 1.2f, 0.75f);
    }

    /** Creates a BM25 index with the standard analyzer and default params. */
    public BM25Index() {
        this(new StandardAnalyzer());
    }

    @Override
    public void index(String id, String content) {
        rwLock.writeLock().lock();
        try {
            indexInternal(id, content);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void indexInternal(String id, String content) {
        // Remove old entry if re-indexing
        if (docIdToIndex.containsKey(id)) {
            removeDoc(id);
        }

        List<String> terms = analyzer.analyze(content);
        int docIndex = docIds.size();

        docIds.add(id);
        docIdToIndex.put(id, docIndex);

        // Grow primitive doc lengths array if needed
        if (docIndex >= docLengthsCapacity) {
            docLengthsCapacity = Math.max(docLengthsCapacity * 2, docIndex + 1);
            docLengthsArray = Arrays.copyOf(docLengthsArray, docLengthsCapacity);
        }
        docLengthsArray[docIndex] = terms.size();

        totalDocs++;
        totalDocLength += terms.size();

        // Count term frequencies
        Map<String, Integer> termFreqs = new HashMap<>();
        for (String term : terms) {
            termFreqs.merge(term, 1, Integer::sum);
        }

        // Add to inverted index (struct-of-arrays posting lists)
        for (var entry : termFreqs.entrySet()) {
            invertedIndex
                    .computeIfAbsent(entry.getKey(), k -> new PostingList())
                    .add(docIndex, entry.getValue());
        }

        // Update average doc length — O(1) incremental
        avgDocLength = totalDocs > 0 ? (double) totalDocLength / totalDocs : 0;
    }

    @Override
    public ScoredResult[] search(String query, int k) {
        rwLock.readLock().lock();
        try {
            return searchInternal(query, k);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private ScoredResult[] searchInternal(String query, int k) {
        List<String> queryTerms = analyzer.analyze(query);
        if (queryTerms.isEmpty() || totalDocs == 0) {
            return new ScoredResult[0];
        }

        // ── Snapshot immutable state for thread-safe parallel scoring ──
        final int n = docIds.size();
        final int nDocs = totalDocs;
        final double avgDL = avgDocLength;
        final int[] docLens = docLengthsArray; // safe: only grows, never shrinks

        // ── Estimate total postings to decide parallel vs sequential ──
        int totalPostings = 0;
        List<String> validTerms = new ArrayList<>(queryTerms.size());
        for (String term : queryTerms) {
            PostingList postings = invertedIndex.get(term);
            if (postings != null) {
                totalPostings += postings.size;
                validTerms.add(term);
            }
        }
        if (validTerms.isEmpty()) {
            return new ScoredResult[0];
        }

        // ── Score using float[] array (zero-copy, no boxing) ──
        float[] scores;

        if (validTerms.size() > 1 && totalPostings >= PARALLEL_POSTING_THRESHOLD) {
            scores = scoreTermsParallel(validTerms, n, nDocs, avgDL, docLens);
        } else {
            scores = scoreTermsSequential(validTerms, n, nDocs, avgDL, docLens);
        }

        // ── Extract top-K using bounded min-heap: O(N log K) ──
        var heap = new NeighborQueue(Math.min(k, 64), k, true); // min-heap: smallest on top
        for (int i = 0; i < n; i++) {
            if (scores[i] > 0f) {
                heap.add(i, scores[i]);
            }
        }

        // ── Build result array directly ──
        int resultCount = heap.size();
        ScoredResult[] results = new ScoredResult[resultCount];
        // Poll from min-heap gives ascending order; fill array back-to-front for descending
        for (int i = resultCount - 1; i >= 0; i--) {
            float score = heap.topScore();
            int idx = heap.poll();
            results[i] = new ScoredResult(docIds.get(idx), idx, score);
        }

        return results;
    }

    /**
     * Scores all terms sequentially into a single float[] array.
     */
    private float[] scoreTermsSequential(List<String> terms, int n,
                                          int nDocs, double avgDL, int[] docLens) {
        float[] scores = new float[n];

        for (String term : terms) {
            PostingList postings = invertedIndex.get(term);
            if (postings == null) continue;
            float idf = computeIdf(postings.size, nDocs);
            accumulatePostings(postings, idf, scores, docLens, avgDL);
        }

        return scores;
    }

    /**
     * Scores each term in parallel using virtual threads, then merges.
     *
     * <p>Each term's postings are scored into a separate float[] array on its own
     * virtual thread. The arrays are then merged with SIMD-friendly sequential addition.
     * This avoids contention on a shared scores array.</p>
     */
    private float[] scoreTermsParallel(List<String> terms, int n,
                                        int nDocs, double avgDL, int[] docLens) {
        // Build tasks — one per term
        List<Callable<float[]>> tasks = new ArrayList<>(terms.size());
        for (String term : terms) {
            tasks.add(() -> {
                PostingList postings = invertedIndex.get(term);
                if (postings == null) return null;
                float idf = computeIdf(postings.size, nDocs);
                float[] termScores = new float[n];
                accumulatePostings(postings, idf, termScores, docLens, avgDL);
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
            log.error("Parallel BM25 scoring failed, falling back to sequential", e.getCause());
            return scoreTermsSequential(terms, n, nDocs, avgDL, docLens);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel BM25 scoring interrupted", e);
        }

        return mergedScores;
    }

    /**
     * Inner scoring loop — accumulates BM25 term scores into the scores array.
     * Kept as a tight loop for maximum throughput.
     */
    /**
     * Inner scoring loop — accumulates BM25 term scores into the scores array.
     *
     * <p>Uses struct-of-arrays PostingList for sequential memory access.
     * The docIndices[] and termFrequencies[] arrays are laid out contiguously,
     * enabling hardware prefetching and better cache line utilization compared
     * to the old List<Posting> approach (which required pointer chasing per element).</p>
     */
    private void accumulatePostings(PostingList postings, float idf,
                                     float[] scores, int[] docLens, double avgDL) {
        final float avgDLf = (float) avgDL;
        final float k1PlusOne = k1 + 1f;
        final float oneMinusB = 1f - b;
        final int sz = postings.size;
        final int[] docIdx = postings.docIndices;
        final int[] tfs = postings.termFrequencies;

        for (int i = 0; i < sz; i++) {
            int docIndex = docIdx[i];
            int tf = tfs[i];
            int docLen = docLens[docIndex];

            float tfNorm = (tf * k1PlusOne)
                    / (tf + k1 * (oneMinusB + b * docLen / avgDLf));

            scores[docIndex] += idf * tfNorm;
        }
    }

    @Override
    public int size() {
        return totalDocs;
    }

    @Override
    public void remove(String id) {
        rwLock.writeLock().lock();
        try {
            removeDoc(id);
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
            docLengthsArray = new int[1024];
            docLengthsCapacity = 1024;
            totalDocLength = 0;
            totalDocs = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Returns the analyzer used by this index.
     *
     * @return the analyzer
     */
    public Analyzer analyzer() {
        return analyzer;
    }

    // ─────────────── BM25 internals ───────────────

    /**
     * Computes the IDF (Inverse Document Frequency) component.
     *
     * <p>Uses the BM25 IDF variant: ln((N - n + 0.5) / (n + 0.5) + 1)</p>
     *
     * @param docFreq number of documents containing the term
     * @param numDocs total number of documents
     * @return IDF score
     */
    private float computeIdf(int docFreq, int numDocs) {
        return (float) Math.log(
                ((double) numDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0
        );
    }

    private void recalcAvgDocLength() {
        long total = 0;
        int n = docIds.size();
        for (int i = 0; i < n; i++) {
            total += docLengthsArray[i];
        }
        totalDocLength = total;
        avgDocLength = totalDocs > 0 ? (double) totalDocLength / totalDocs : 0;
    }

    private void removeDoc(String id) {
        // Simple removal: mark as removed but don't compact
        // For a production system, we'd implement proper deletion
        Integer idx = docIdToIndex.remove(id);
        if (idx != null) {
            totalDocs--;
            totalDocLength -= docLengthsArray[idx];
            // Remove postings (expensive but correct for re-index)
            for (var postings : invertedIndex.values()) {
                postings.removeByDocIndex(idx);
            }
        }
    }

    // ─────────────── Binary Persistence (bm25.bidx) ───────────────

    /** Magic bytes for the BM25 binary index file format. */
    private static final int MAGIC = 0x42494458; // "BIDX"
    private static final int FORMAT_VERSION = 1;

    /**
     * Saves the current BM25 index to a binary file ({@code bm25.bidx}).
     *
     * <p>File format (VERSION 1):</p>
     * <pre>
     *   Header:  [4B magic "BIDX"] [4B version] [4B totalDocs] [4B termCount] [8B totalDocLength]
     *   DocIds:  [4B count] { [4B idLen] [N idBytes] } × count
     *   DocLens: [4B count] [4B × count docLengths]
     *   Terms:   [4B termCount] { [4B termLen] [N termBytes] [4B postingsSize]
     *                              { [4B docIdx] [4B termFreq] }* }
     * </pre>
     *
     * @param filePath path to write (parent directories created if needed)
     */
    public void save(java.nio.file.Path filePath) {
        rwLock.readLock().lock();
        try {
            java.nio.file.Path parent = filePath.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }

            try (var ch = java.nio.channels.FileChannel.open(filePath,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

                // ── Header: 24 bytes ──
                java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(24);
                header.putInt(MAGIC);
                header.putInt(FORMAT_VERSION);
                header.putInt(totalDocs);
                header.putInt(invertedIndex.size());
                header.putLong(totalDocLength);
                header.flip();
                ch.write(header);

                // ── DocIds ──
                int docCount = docIds.size();
                java.nio.ByteBuffer docCountBuf = java.nio.ByteBuffer.allocate(4);
                docCountBuf.putInt(docCount);
                docCountBuf.flip();
                ch.write(docCountBuf);
                for (String id : docIds) {
                    byte[] idBytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    java.nio.ByteBuffer idBuf = java.nio.ByteBuffer.allocate(4 + idBytes.length);
                    idBuf.putInt(idBytes.length);
                    idBuf.put(idBytes);
                    idBuf.flip();
                    ch.write(idBuf);
                }

                // ── DocLengths ──
                java.nio.ByteBuffer docLenHeader = java.nio.ByteBuffer.allocate(4 + docCount * 4);
                docLenHeader.putInt(docCount);
                for (int i = 0; i < docCount; i++) {
                    docLenHeader.putInt(docLengthsArray[i]);
                }
                docLenHeader.flip();
                ch.write(docLenHeader);

                // ── Terms + Postings ──
                java.nio.ByteBuffer termCountBuf = java.nio.ByteBuffer.allocate(4);
                termCountBuf.putInt(invertedIndex.size());
                termCountBuf.flip();
                ch.write(termCountBuf);
                for (var entry : invertedIndex.entrySet()) {
                    byte[] termBytes = entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    PostingList pl = entry.getValue();
                    // term header: termLen + termBytes + postingsSize
                    java.nio.ByteBuffer termBuf = java.nio.ByteBuffer.allocate(
                            4 + termBytes.length + 4 + pl.size * 8);
                    termBuf.putInt(termBytes.length);
                    termBuf.put(termBytes);
                    termBuf.putInt(pl.size);
                    for (int i = 0; i < pl.size; i++) {
                        termBuf.putInt(pl.docIndices[i]);
                        termBuf.putInt(pl.termFrequencies[i]);
                    }
                    termBuf.flip();
                    ch.write(termBuf);
                }

                ch.force(true);
                log.info("BM25 index saved: {} docs, {} terms → {}",
                        totalDocs, invertedIndex.size(), filePath);
            }
        } catch (java.io.IOException e) {
            log.error("Failed to save BM25 index to {}", filePath, e);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Loads a BM25 index from a binary file ({@code bm25.bidx}).
     *
     * @param filePath path to the binary index file
     * @return the loaded BM25Index, or null if the file doesn't exist or is invalid
     */
    public static BM25Index load(java.nio.file.Path filePath) {
        if (!java.nio.file.Files.exists(filePath)) {
            return null;
        }

        try (var ch = java.nio.channels.FileChannel.open(filePath,
                java.nio.file.StandardOpenOption.READ)) {

            // ── Header ──
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(24);
            ch.read(header);
            header.flip();
            int magic = header.getInt();
            if (magic != MAGIC) {
                log.warn("Invalid BM25 index magic: expected 0x{}, got 0x{}",
                        Integer.toHexString(MAGIC), Integer.toHexString(magic));
                return null;
            }
            int version = header.getInt();
            if (version != FORMAT_VERSION) {
                log.warn("Unsupported BM25 index version: {}", version);
                return null;
            }
            int savedTotalDocs = header.getInt();
            int termCount = header.getInt();
            long savedTotalDocLength = header.getLong();

            BM25Index idx = new BM25Index();

            // ── DocIds ──
            java.nio.ByteBuffer docCountBuf = java.nio.ByteBuffer.allocate(4);
            ch.read(docCountBuf);
            docCountBuf.flip();
            int docCount = docCountBuf.getInt();
            for (int i = 0; i < docCount; i++) {
                java.nio.ByteBuffer lenBuf = java.nio.ByteBuffer.allocate(4);
                ch.read(lenBuf);
                lenBuf.flip();
                int idLen = lenBuf.getInt();
                java.nio.ByteBuffer idBuf = java.nio.ByteBuffer.allocate(idLen);
                ch.read(idBuf);
                idBuf.flip();
                String id = new String(idBuf.array(), 0, idLen, java.nio.charset.StandardCharsets.UTF_8);
                idx.docIds.add(id);
                idx.docIdToIndex.put(id, i);
            }

            // ── DocLengths ──
            java.nio.ByteBuffer docLenHeader = java.nio.ByteBuffer.allocate(4);
            ch.read(docLenHeader);
            docLenHeader.flip();
            int docLenCount = docLenHeader.getInt();
            if (docLenCount > idx.docLengthsCapacity) {
                idx.docLengthsCapacity = docLenCount;
                idx.docLengthsArray = new int[docLenCount];
            }
            if (docLenCount > 0) {
                java.nio.ByteBuffer lensBuf = java.nio.ByteBuffer.allocate(docLenCount * 4);
                ch.read(lensBuf);
                lensBuf.flip();
                for (int i = 0; i < docLenCount; i++) {
                    idx.docLengthsArray[i] = lensBuf.getInt();
                }
            }

            // ── Terms + Postings ──
            java.nio.ByteBuffer termCountBuf2 = java.nio.ByteBuffer.allocate(4);
            ch.read(termCountBuf2);
            termCountBuf2.flip();
            int savedTermCount = termCountBuf2.getInt();
            for (int t = 0; t < savedTermCount; t++) {
                java.nio.ByteBuffer termLenBuf = java.nio.ByteBuffer.allocate(4);
                ch.read(termLenBuf);
                termLenBuf.flip();
                int termLen = termLenBuf.getInt();

                java.nio.ByteBuffer termBuf = java.nio.ByteBuffer.allocate(termLen);
                ch.read(termBuf);
                termBuf.flip();
                String term = new String(termBuf.array(), 0, termLen, java.nio.charset.StandardCharsets.UTF_8);

                java.nio.ByteBuffer postCountBuf = java.nio.ByteBuffer.allocate(4);
                ch.read(postCountBuf);
                postCountBuf.flip();
                int postingsSize = postCountBuf.getInt();

                PostingList pl = new PostingList(Math.max(postingsSize, 16));
                if (postingsSize > 0) {
                    java.nio.ByteBuffer postBuf = java.nio.ByteBuffer.allocate(postingsSize * 8);
                    ch.read(postBuf);
                    postBuf.flip();
                    for (int p = 0; p < postingsSize; p++) {
                        pl.add(postBuf.getInt(), postBuf.getInt());
                    }
                }
                idx.invertedIndex.put(term, pl);
            }

            // ── Restore computed state ──
            idx.totalDocs = savedTotalDocs;
            idx.totalDocLength = savedTotalDocLength;
            idx.avgDocLength = savedTotalDocs > 0 ? (double) savedTotalDocLength / savedTotalDocs : 0;

            log.info("BM25 index loaded: {} docs, {} terms ← {}",
                    savedTotalDocs, savedTermCount, filePath);
            return idx;

        } catch (java.io.IOException e) {
            log.error("Failed to load BM25 index from {}", filePath, e);
            return null;
        }
    }
}
