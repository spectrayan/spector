package com.spectrayan.spector.client.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for search operations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {

    private List<SearchResult> results;
    private int totalHits;
    private long queryTimeMs;
    private String mode;

    public SearchResponse() {}

    public List<SearchResult> getResults() { return results; }
    public void setResults(List<SearchResult> results) { this.results = results; }

    public int getTotalHits() { return totalHits; }
    public void setTotalHits(int totalHits) { this.totalHits = totalHits; }

    public long getQueryTimeMs() { return queryTimeMs; }
    public void setQueryTimeMs(long queryTimeMs) { this.queryTimeMs = queryTimeMs; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    /**
     * A single search result entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResult {
        private String id;
        private float score;

        public SearchResult() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
    }
}
