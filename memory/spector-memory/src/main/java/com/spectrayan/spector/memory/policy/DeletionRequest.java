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
 * A deletion the engine is about to perform, presented to {@link MutationPolicy} for approval.
 *
 * <p>Carries only what the engine can supply without reaching outside itself. Notably it does <b>not</b>
 * carry an account id: the engine does not know about accounts, and a policy implementation that needs one
 * resolves it from {@code namespaceId} on its own side. Putting it here would make the engine pretend to
 * knowledge it does not have.</p>
 *
 * @param kind        what the operation does to the bytes
 * @param namespaceId the namespace being operated on; never null
 * @param memoryId    the specific record, or {@code null} for a namespace-wide operation
 */
public record DeletionRequest(DeletionKind kind, String namespaceId, String memoryId) {

    public DeletionRequest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(namespaceId, "namespaceId");
    }

    /** A request to logically forget one record. */
    public static DeletionRequest forget(String namespaceId, String memoryId) {
        return new DeletionRequest(DeletionKind.FORGET, namespaceId, memoryId);
    }

    /** A request to physically destroy one record's content. */
    public static DeletionRequest purge(String namespaceId, String memoryId) {
        return new DeletionRequest(DeletionKind.PURGE, namespaceId, memoryId);
    }

    /** A request to compact a namespace, overwriting tombstoned records' bytes. */
    public static DeletionRequest vacuum(String namespaceId) {
        return new DeletionRequest(DeletionKind.VACUUM, namespaceId, null);
    }

    /** A request to destroy an entire namespace's data. */
    public static DeletionRequest eraseNamespace(String namespaceId) {
        return new DeletionRequest(DeletionKind.ERASE_NAMESPACE, namespaceId, null);
    }

    /** Returns whether this request targets a single record rather than a whole namespace. */
    public boolean isRecordScoped() {
        return memoryId != null;
    }
}
