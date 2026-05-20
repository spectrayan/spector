package com.spectrayan.spector.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for document ingestion operations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestResponse {

    private String id;
    private boolean indexed;
    private boolean autoEmbedded;
    private int total;
    private int success;
    private int failed;

    public IngestResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }

    public boolean isAutoEmbedded() { return autoEmbedded; }
    public void setAutoEmbedded(boolean autoEmbedded) { this.autoEmbedded = autoEmbedded; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
}
