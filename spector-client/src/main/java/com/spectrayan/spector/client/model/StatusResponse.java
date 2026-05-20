package com.spectrayan.spector.client.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for server status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusResponse {

    private String engine;
    private String version;
    private long documents;
    private int dimensions;
    private String similarity;
    private String indexType;
    private String gpu;
    private String reranker;
    private String embedding;
    private Map<String, Object> simd;

    public StatusResponse() {}

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getDocuments() { return documents; }
    public void setDocuments(long documents) { this.documents = documents; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }

    public String getSimilarity() { return similarity; }
    public void setSimilarity(String similarity) { this.similarity = similarity; }

    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }

    public String getGpu() { return gpu; }
    public void setGpu(String gpu) { this.gpu = gpu; }

    public String getReranker() { return reranker; }
    public void setReranker(String reranker) { this.reranker = reranker; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public Map<String, Object> getSimd() { return simd; }
    public void setSimd(Map<String, Object> simd) { this.simd = simd; }
}
