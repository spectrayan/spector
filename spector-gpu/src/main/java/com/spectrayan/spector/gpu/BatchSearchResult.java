package com.spectrayan.spector.gpu;

/**
 * A scored search result from a batch GPU search operation.
 *
 * @param vectorIndex the index of the matched vector in the database
 * @param score       the similarity score (higher is more similar)
 */
public record BatchSearchResult(int vectorIndex, float score) implements Comparable<BatchSearchResult> {

    /**
     * Compares by score in descending order (highest score first).
     */
    @Override
    public int compareTo(BatchSearchResult other) {
        return Float.compare(other.score, this.score); // descending
    }
}
