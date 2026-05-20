package com.spectrayan.spector.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request model for search operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchRequest {

    private String text;
    private float[] vector;
    private String mode;
    private int topK = 10;

    public SearchRequest() {}

    /** Creates a keyword search request. */
    public static SearchRequest keyword(String text, int topK) {
        var req = new SearchRequest();
        req.text = text;
        req.mode = "KEYWORD";
        req.topK = topK;
        return req;
    }

    /** Creates a vector search request. */
    public static SearchRequest vector(float[] vector, int topK) {
        var req = new SearchRequest();
        req.vector = vector;
        req.mode = "VECTOR";
        req.topK = topK;
        return req;
    }

    /** Creates a hybrid search request. */
    public static SearchRequest hybrid(String text, float[] vector, int topK) {
        var req = new SearchRequest();
        req.text = text;
        req.vector = vector;
        req.mode = "HYBRID";
        req.topK = topK;
        return req;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
