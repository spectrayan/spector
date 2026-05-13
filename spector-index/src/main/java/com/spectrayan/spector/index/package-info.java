/**
 * Spector Index — HNSW vector index and BM25 keyword index implementations.
 *
 * <p>Contains the core indexing data structures: a lock-free HNSW graph for
 * approximate nearest-neighbor vector search, and an inverted index with
 * BM25 scoring for keyword search. Both indexes delegate distance/scoring
 * computations to the SIMD kernels in {@code spector-core}.</p>
 */
package com.spectrayan.spector.index;
