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
package com.spectrayan.spector.core.similarity;

/**
 * Pure mathematical kernel for Okapi BM25 term weighting and document frequency (ADR-0033 Domain 11, #28).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class BM25Kernel {

    public static final float DEFAULT_K1 = 1.2f;
    public static final float DEFAULT_B = 0.75f;

    private BM25Kernel() {}

    /**
     * Computes the Robertson-Spärck Jones BM25 Inverse Document Frequency (IDF):
     * <p>{@code IDF(n, N) = ln(1 + (N - n + 0.5) / (n + 0.5))}</p>
     *
     * @param docFreq   number of documents containing the term \(n\)
     * @param totalDocs total number of documents in corpus \(N\)
     * @return non-negative IDF score
     */
    public static float idf(final int docFreq, final int totalDocs) {
        if (totalDocs <= 0 || docFreq < 0) {
            return 0.0f;
        }
        return (float) Math.log(
                ((double) totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0
        );
    }

    /**
     * Computes BM25 single term score:
     * <p>{@code score = IDF · (tf · (k1 + 1)) / (tf + k1 · (1 - b + b · (L / L_avg)))}</p>
     *
     * @param tf        term frequency in the document
     * @param docLen    length of the document in tokens
     * @param avgDocLen average document length in tokens
     * @param k1        term frequency saturation parameter (typically 1.2)
     * @param b         length normalization parameter (typically 0.75)
     * @param idf       precomputed term IDF
     * @return term contribution score
     */
    public static float scoreTerm(
            final int tf,
            final int docLen,
            final float avgDocLen,
            final float k1,
            final float b,
            final float idf) {
        if (tf <= 0 || idf <= 0.0f) {
            return 0.0f;
        }

        final float lenNorm = (avgDocLen > 0.0f) ? (docLen / avgDocLen) : 1.0f;
        final float denom = tf + k1 * (1.0f - b + b * lenNorm);
        if (denom <= 0.0f) {
            return 0.0f;
        }

        return idf * ((tf * (k1 + 1.0f)) / denom);
    }

    /**
     * Batch term scoring across multiple candidate documents (Principle 3).
     *
     * @param tfs        term frequencies per document
     * @param docLens    lengths per document
     * @param avgDocLen  average document length
     * @param k1         BM25 k1 parameter
     * @param b          BM25 b parameter
     * @param idf        term IDF
     * @param outScores  output array for computed term scores
     * @param count      number of documents to process
     */
    public static void scoreTerms(
            final int[] tfs,
            final int[] docLens,
            final float avgDocLen,
            final float k1,
            final float b,
            final float idf,
            final float[] outScores,
            final int count) {
        if (tfs == null || docLens == null || outScores == null || count <= 0) {
            return;
        }

        final int limit = Math.min(count, Math.min(tfs.length, Math.min(docLens.length, outScores.length)));

        // idf is loop-invariant, so its guard is hoisted; everything else delegates to scoreTerm.
        // Do NOT algebraically refactor the denominator here (e.g. pre-folding k1*(1-b) and
        // k1*b/avgDocLen): it is mathematically equivalent but NOT bit-identical in float
        // arithmetic, which breaks batch/scalar parity and silently shifts BM25 rankings.
        // Enforced by BatchScalarParityTest.bm25BatchMatchesScalar.
        if (idf <= 0.0f) {
            java.util.Arrays.fill(outScores, 0, limit, 0.0f);
            return;
        }

        for (int i = 0; i < limit; i++) {
            outScores[i] = scoreTerm(tfs[i], docLens[i], avgDocLen, k1, b, idf);
        }
    }

    /**
     * Batch term scoring across multiple candidate postings using indexed document lengths.
     *
     * @param tfs              term frequencies per posting
     * @param tfOffset         offset into {@code tfs}
     * @param docIndices       document indices per posting
     * @param docIndicesOffset offset into {@code docIndices}
     * @param docLens          global document lengths array
     * @param avgDocLen        average document length
     * @param k1               BM25 k1 parameter
     * @param b                BM25 b parameter
     * @param idf              term IDF
     * @param outScores        output array for computed term scores
     * @param outOffset        offset into output array
     * @param count            number of postings to process
     */
    public static void scoreTerms(
            final int[] tfs,
            final int tfOffset,
            final int[] docIndices,
            final int docIndicesOffset,
            final int[] docLens,
            final float avgDocLen,
            final float k1,
            final float b,
            final float idf,
            final float[] outScores,
            final int outOffset,
            final int count) {
        if (tfs == null || docIndices == null || docLens == null || outScores == null || count <= 0) {
            return;
        }

        // See the sibling scoreTerms overload: the denominator must not be algebraically
        // refactored, or batch and scalar stop being bit-identical.
        if (idf <= 0.0f) {
            java.util.Arrays.fill(outScores, outOffset, outOffset + count, 0.0f);
            return;
        }

        for (int i = 0; i < count; i++) {
            final int docLen = docLens[docIndices[docIndicesOffset + i]];
            outScores[outOffset + i] = scoreTerm(tfs[tfOffset + i], docLen, avgDocLen, k1, b, idf);
        }
    }
}

