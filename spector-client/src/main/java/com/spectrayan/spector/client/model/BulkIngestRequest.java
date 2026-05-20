package com.spectrayan.spector.client.model;

import java.util.List;

/**
 * Request model for bulk document ingestion.
 */
public class BulkIngestRequest {

    private List<IngestRequest> documents;

    public BulkIngestRequest() {}

    public BulkIngestRequest(List<IngestRequest> documents) {
        this.documents = documents;
    }

    public List<IngestRequest> getDocuments() { return documents; }
    public void setDocuments(List<IngestRequest> documents) { this.documents = documents; }
}
