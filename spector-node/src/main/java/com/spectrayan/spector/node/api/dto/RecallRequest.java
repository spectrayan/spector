package com.spectrayan.spector.node.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for cognitive recall via the REST API.
 *
 * @param query        natural language query (required)
 * @param topK         max results (default 10)
 * @param profile      cognitive profile: BALANCED (default), HYPERFOCUS, PARANOID, DIVERGENT, etc.
 * @param queryValence optional emotional context for valence-filtered recall
 */
public record RecallRequest(
        @JsonProperty("query") String query,
        @JsonProperty("topK") Integer topK,
        @JsonProperty("profile") String profile,
        @JsonProperty("queryValence") Integer queryValence
) {
    /** Returns topK or default 10. */
    public int effectiveTopK() {
        return topK != null && topK > 0 ? topK : 10;
    }

    /** Returns profile or default "BALANCED". */
    public String effectiveProfile() {
        return profile != null && !profile.isBlank() ? profile.toUpperCase() : "BALANCED";
    }
}
