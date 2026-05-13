package com.spectrayan.spector.index;

import com.spectrayan.spector.core.SimilarityFunction;

/**
 * A scored search result from a vector or keyword index.
 *
 * @param id    the document/vector identifier
 * @param index the internal integer index in the store
 * @param score the similarity or distance score
 */
public record ScoredResult(String id, int index, float score) implements Comparable<ScoredResult> {

    /**
     * Compares by score in descending order (highest score first).
     * For distance metrics where lower is better, callers should negate or
     * use {@link #compareAscending}.
     */
    @Override
    public int compareTo(ScoredResult other) {
        return Float.compare(other.score, this.score); // descending
    }

    /**
     * Compares by score ascending (lowest first) — used for distance metrics.
     */
    public static int compareAscending(ScoredResult a, ScoredResult b) {
        return Float.compare(a.score, b.score);
    }
}
