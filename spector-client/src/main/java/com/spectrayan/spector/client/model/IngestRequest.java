package com.spectrayan.spector.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request model for single document ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestRequest {

    private String id;
    private String title;
    private String content;
    private float[] vector;

    public IngestRequest() {}

    public IngestRequest(String id, String content, float[] vector) {
        this.id = id;
        this.content = content;
        this.vector = vector;
    }

    public IngestRequest(String id, String title, String content, float[] vector) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.vector = vector;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }
}
