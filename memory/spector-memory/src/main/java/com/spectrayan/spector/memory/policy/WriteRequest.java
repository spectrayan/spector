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
 * @param operationType what kind of mutation is about to happen
 * @param namespaceId   the namespace being written to; never null
 * @param memoryId      the specific record, or {@code null} if not record-scoped
 * @param epoch         the fence epoch
 * @param timestamp     epoch milliseconds
 */
public record WriteRequest(OperationType operationType, String namespaceId, String memoryId, long epoch, long timestamp) {

    /** The class of mutation being attempted. */
    public enum OperationType {
        /** A new memory is being stored. */
        REMEMBER,
        /** An existing memory is being forgotten. */
        FORGET,
        /** An existing memory is being purged. */
        PURGE,
        /** An existing memory's strength or associations are being updated. */
        REINFORCE,
        /** Consolidation is writing derived memories. */
        CONSOLIDATE
    }

    public WriteRequest {
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(namespaceId, "namespaceId");
    }

    public static WriteRequest remember(String namespaceId, String memoryId, long epoch, long timestamp) {
        return new WriteRequest(OperationType.REMEMBER, namespaceId, memoryId, epoch, timestamp);
    }

    public static WriteRequest forget(String namespaceId, String memoryId, long epoch, long timestamp) {
        return new WriteRequest(OperationType.FORGET, namespaceId, memoryId, epoch, timestamp);
    }

    public static WriteRequest purge(String namespaceId, String memoryId, long epoch, long timestamp) {
        return new WriteRequest(OperationType.PURGE, namespaceId, memoryId, epoch, timestamp);
    }

    public static WriteRequest reinforce(String namespaceId, String memoryId, long epoch, long timestamp) {
        return new WriteRequest(OperationType.REINFORCE, namespaceId, memoryId, epoch, timestamp);
    }

    public static WriteRequest consolidate(String namespaceId, long epoch, long timestamp) {
        return new WriteRequest(OperationType.CONSOLIDATE, namespaceId, null, epoch, timestamp);
    }
}
