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
package com.spectrayan.spector.memory.policy;

import java.util.Objects;

/**
 * A state mutation the engine is about to perform, presented to {@link MutationPolicy} for approval.
 *
 * <p>Separate from {@link DeletionRequest} because it answers a different question. A deletion check asks
 * "may this data be destroyed"; a write check asks "is this node still entitled to write at all". Those have
 * different inputs and different failure modes, and merging them would mean a union-typed request and one
 * exception channel for two unrelated refusals.</p>
 *
 * <p>The immediate consumer is {@code cell-failover-fencing}: an owner must reject a write whose fence token
 * does not match its own current fence, which is what makes a resurrected old owner harmless. The engine
 * cannot see fence state, so it asks.</p>
 *
 * @param kind        what kind of mutation is about to happen
 * @param namespaceId the namespace being written to; never null
 * @param memoryId    the specific record, or {@code null} if not record-scoped
 */
public record WriteRequest(WriteKind kind, String namespaceId, String memoryId) {

    /** The class of mutation being attempted. */
    public enum WriteKind {
        /** A new memory is being stored. */
        REMEMBER,
        /** An existing memory's strength or associations are being updated. */
        REINFORCE,
        /** Consolidation is writing derived memories. */
        CONSOLIDATE,
        /** Graph edges are being added or rewritten. */
        GRAPH_MUTATION
    }

    public WriteRequest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(namespaceId, "namespaceId");
    }

    /** A request to store a new memory. */
    public static WriteRequest remember(String namespaceId, String memoryId) {
        return new WriteRequest(WriteKind.REMEMBER, namespaceId, memoryId);
    }

    /** A request to reinforce an existing memory. */
    public static WriteRequest reinforce(String namespaceId, String memoryId) {
        return new WriteRequest(WriteKind.REINFORCE, namespaceId, memoryId);
    }
}
