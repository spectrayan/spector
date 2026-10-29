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
package com.spectrayan.spector.cluster.idempotency;

/**
 * Namespace-scoped store that tracks in-flight and completed mutating HTTP requests
 * keyed by {@code (namespaceScope, Idempotency-Key)}.
 */
public interface IdempotencyStore {

    /**
     * Attempts to reserve the given {@code idempotencyKey} within {@code namespaceScope}
     * for a request with {@code fingerprint}, or returns the replay / conflict / mismatch outcome.
     */
    IdempotencyReservation reserve(String namespaceScope, String idempotencyKey, RequestFingerprint fingerprint);

    /**
     * Commits a completed {@code 2xx} response for a previously reserved key.
     */
    void commit(
            String namespaceScope,
            String idempotencyKey,
            RequestFingerprint fingerprint,
            StoredIdempotentResponse response
    );

    /**
     * Releases an in-flight reservation when the underlying execution fails or returns non-2xx,
     * allowing a subsequent retry with the same key to re-execute.
     */
    void release(String namespaceScope, String idempotencyKey);

    /**
     * Returns the current number of active entries held in memory.
     */
    int size();
}
