package com.spectrayan.spector.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for delete operations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteResponse {

    private String id;
    private boolean deleted;

    public DeleteResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
