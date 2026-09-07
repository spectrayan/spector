/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.ScoringMode;

import java.util.List;

/**
 * Request for cross-rememberer federated recall (ADR-0029 §7).
 *
 * @param queryText        the query text to search for across rememberers
 * @param namespaces       list of namespace slugs or IDs, or ["granted"] to query all accessible namespaces
 * @param topK             overall maximum results to return across all namespaces (default 10)
 * @param perNamespaceTopK maximum results to query per namespace (default 5)
 * @param timeoutMs        maximum timeout budget in milliseconds (default 3000ms, max 10000ms)
 * @param maxColdOpens     maximum number of cold (uncached) namespaces allowed to open (default 2)
 * @param profile          optional cognitive profile preset
 * @param scoringMode      optional scoring mode (COGNITIVE, SIMILARITY, ASSOCIATIVE)
 */
public record FederatedRecallRequest(
        @JsonProperty("queryText") String queryText,
        @JsonProperty("namespaces") List<String> namespaces,
        @JsonProperty("topK") Integer topK,
        @JsonProperty("perNamespaceTopK") Integer perNamespaceTopK,
        @JsonProperty("timeoutMs") Integer timeoutMs,
        @JsonProperty("maxColdOpens") Integer maxColdOpens,
        @JsonProperty("profile") CognitiveProfile profile,
        @JsonProperty("scoringMode") ScoringMode scoringMode
) {
    public FederatedRecallRequest {
        if (topK == null || topK <= 0) topK = 10;
        if (perNamespaceTopK == null || perNamespaceTopK <= 0) perNamespaceTopK = 5;
        if (timeoutMs == null || timeoutMs <= 0) timeoutMs = 3000;
        if (timeoutMs > 10000) timeoutMs = 10000;
        if (maxColdOpens == null || maxColdOpens < 0) maxColdOpens = 2;
    }
}
