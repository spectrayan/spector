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
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.synapse.catalog.GrantRole;

/**
 * A single recall hit returned from a federated recall operation (ADR-0029 §7).
 *
 * <p>Wraps the full, rich {@link CognitiveResult} telemetry alongside cross-rememberer
 * provenance annotations (namespaceId, slug, role, local rank, and heuristic global rank).</p>
 *
 * @param namespaceId         globally unique namespace identifier (TSID)
 * @param slug                account-scoped slug alias
 * @param role                caller's access role on the namespace
 * @param localRank           rank within the originating rememberer (1-indexed)
 * @param heuristicGlobalRank heuristic merged rank across rememberers (1-indexed)
 * @param result              full cognitive result with complete scoring telemetry and metadata
 */
public record FederatedRecallHit(
        @JsonProperty("namespaceId") String namespaceId,
        @JsonProperty("slug") String slug,
        @JsonProperty("role") GrantRole role,
        @JsonProperty("localRank") int localRank,
        @JsonProperty("heuristicGlobalRank") int heuristicGlobalRank,
        @JsonProperty("result") CognitiveResult result
) {
    /** Convenience accessor for memory ID. */
    public String id() {
        return result != null ? result.id() : null;
    }

    /** Convenience accessor for memory text. */
    public String text() {
        return result != null ? result.text() : null;
    }

    /** Convenience accessor for cognitive score. */
    public float score() {
        return result != null ? result.score() : 0.0f;
    }
}
