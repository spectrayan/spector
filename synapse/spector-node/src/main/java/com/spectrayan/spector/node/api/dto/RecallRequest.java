/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
