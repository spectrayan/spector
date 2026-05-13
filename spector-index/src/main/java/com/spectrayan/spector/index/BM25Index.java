package com.spectrayan.spector.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Default parameters: k1 = 1.2, b = 0.75</p>
 */
public class BM25Index implements KeywordIndex {

    private static final Logger log = LoggerFactory.getLogger(BM25Index.class);

    private final Analyzer analyzer;
    private final float k1;
    private final float b;

    // ── Inverted index ──
    private final Map<String, List<Posting>> invertedIndex;  // term → postings

    // ── Document metadata ──
    private final List<String> docIds;               // index → doc ID
    private final Map<String, Integer> docIdToIndex;  // doc ID → index
    private final List<Integer> docLengths;           // index → doc length (in terms)
    private double avgDocLength;
    private int totalDocs;

    /** A posting: document index + term frequency in that document. */
    private record Posting(int docIndex, int termFrequency) {}

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
        this.docLengths = new ArrayList<>();
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
    public synchronized void index(String id, String content) {
        // Remove old entry if re-indexing
        if (docIdToIndex.containsKey(id)) {
            removeDoc(id);
        }

        List<String> terms = analyzer.analyze(content);
        int docIndex = docIds.size();

        docIds.add(id);
        docIdToIndex.put(id, docIndex);
        docLengths.add(terms.size());
        totalDocs++;

        // Count term frequencies
        Map<String, Integer> termFreqs = new HashMap<>();
        for (String term : terms) {
            termFreqs.merge(term, 1, Integer::sum);
        }

        // Add to inverted index
        for (var entry : termFreqs.entrySet()) {
            invertedIndex
                    .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(new Posting(docIndex, entry.getValue()));
        }

        // Update average doc length
        updateAvgDocLength();
    }

    @Override
    public ScoredResult[] search(String query, int k) {
        List<String> queryTerms = analyzer.analyze(query);
        if (queryTerms.isEmpty() || totalDocs == 0) {
            return new ScoredResult[0];
        }

        // Score all matching documents
        Map<Integer, Float> scores = new HashMap<>();

        for (String term : queryTerms) {
            List<Posting> postings = invertedIndex.get(term);
            if (postings == null) continue;

            float idf = computeIdf(postings.size());

            for (Posting posting : postings) {
                int docIndex = posting.docIndex();
                int tf = posting.termFrequency();
                int docLen = docLengths.get(docIndex);

                float tfNorm = (tf * (k1 + 1))
                        / (tf + k1 * (1 - b + b * (float) docLen / (float) avgDocLength));

                scores.merge(docIndex, idf * tfNorm, Float::sum);
            }
        }

        // Convert to sorted results
        ScoredResult[] results = scores.entrySet().stream()
                .map(e -> new ScoredResult(docIds.get(e.getKey()), e.getKey(), e.getValue()))
                .sorted()  // descending by score (ScoredResult.compareTo)
                .limit(k)
                .toArray(ScoredResult[]::new);

        return results;
    }

    @Override
    public int size() {
        return totalDocs;
    }

    @Override
    public void close() {
        invertedIndex.clear();
        docIds.clear();
        docIdToIndex.clear();
        docLengths.clear();
        totalDocs = 0;
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
     * @return IDF score
     */
    private float computeIdf(int docFreq) {
        return (float) Math.log(
                ((double) totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0
        );
    }

    private void updateAvgDocLength() {
        long totalLength = 0;
        for (int len : docLengths) {
            totalLength += len;
        }
        avgDocLength = totalDocs > 0 ? (double) totalLength / totalDocs : 0;
    }

    private void removeDoc(String id) {
        // Simple removal: mark as removed but don't compact
        // For a production system, we'd implement proper deletion
        Integer idx = docIdToIndex.remove(id);
        if (idx != null) {
            totalDocs--;
            // Remove postings (expensive but correct for re-index)
            for (var postings : invertedIndex.values()) {
                postings.removeIf(p -> p.docIndex() == idx);
            }
        }
    }
}
