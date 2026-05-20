package com.spectrayan.spector.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for server metrics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricsResponse {

    private long uptimeMs;
    private long totalRequests;
    private long totalSearches;
    private long totalIngestions;
    private long totalErrors;
    private long documents;
    private boolean gpu;
    private boolean reranker;

    public MetricsResponse() {}

    public long getUptimeMs() { return uptimeMs; }
    public void setUptimeMs(long uptimeMs) { this.uptimeMs = uptimeMs; }

    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

    public long getTotalSearches() { return totalSearches; }
    public void setTotalSearches(long totalSearches) { this.totalSearches = totalSearches; }

    public long getTotalIngestions() { return totalIngestions; }
    public void setTotalIngestions(long totalIngestions) { this.totalIngestions = totalIngestions; }

    public long getTotalErrors() { return totalErrors; }
    public void setTotalErrors(long totalErrors) { this.totalErrors = totalErrors; }

    public long getDocuments() { return documents; }
    public void setDocuments(long documents) { this.documents = documents; }

    public boolean isGpu() { return gpu; }
    public void setGpu(boolean gpu) { this.gpu = gpu; }

    public boolean isReranker() { return reranker; }
    public void setReranker(boolean reranker) { this.reranker = reranker; }
}
