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

import java.util.Optional;

/**
 * Optional durable persistence SPI for committed idempotency records.
 *
 * <p>Implementations persist completed {@code 2xx} responses so that idempotent replay
 * and fingerprint mismatch detection survive process restarts within the configured TTL,
 * while in-flight reservations remain ephemeral so interrupted writes can be retried
 * cleanly after recovery.</p>
 */
public interface IdempotencyPersistenceBackend {

    record PersistedEntry(
            String namespaceScope,
            String idempotencyKey,
            RequestFingerprint fingerprint,
            StoredIdempotentResponse response,
            long expiresAtEpochMs
    ) {}

    /**
     * Looks up a non-expired committed entry for {@code (namespaceScope, idempotencyKey)}.
     */
    Optional<PersistedEntry> findActive(String namespaceScope, String idempotencyKey, long nowEpochMs);

    /**
     * Durably persists a committed entry and enforces TTL / maximum entry bounds.
     */
    void save(PersistedEntry entry, int maxEntries, long nowEpochMs);

    /**
     * Deletes a persisted entry if present.
     */
    void delete(String namespaceScope, String idempotencyKey);
}
