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

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded, TTL-expiring {@link IdempotencyStore} with atomic in-flight reservation and
 * optional write-through {@link IdempotencyPersistenceBackend} durability.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    public static final Duration DEFAULT_TTL = Duration.ofHours(24);
    public static final int DEFAULT_MAX_ENTRIES = 16_384;

    private record ScopeKey(String namespaceScope, String idempotencyKey) {}

    private record StoreEntry(
            RequestFingerprint fingerprint,
            StoredIdempotentResponse response,
            long createdAtEpochMs,
            long expiresAtEpochMs,
            long sequence
    ) {
        boolean isInFlight() {
            return response == null;
        }

        boolean isExpired(long nowEpochMs) {
            return nowEpochMs >= expiresAtEpochMs;
        }
    }

    private final ConcurrentHashMap<ScopeKey, StoreEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0L);
    private final long ttlMillis;
    private final int maxEntries;
    private final Clock clock;
    private final IdempotencyPersistenceBackend persistenceBackend;

    public InMemoryIdempotencyStore() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC(), null);
    }

    public InMemoryIdempotencyStore(Duration ttl, int maxEntries) {
        this(ttl, maxEntries, Clock.systemUTC(), null);
    }

    public InMemoryIdempotencyStore(
            Duration ttl,
            int maxEntries,
            Clock clock,
            IdempotencyPersistenceBackend persistenceBackend
    ) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.ttlMillis = Math.max(1L, ttl.toMillis());
        this.maxEntries = maxEntries;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.persistenceBackend = persistenceBackend;
    }

    @Override
    public IdempotencyReservation reserve(
            String namespaceScope,
            String idempotencyKey,
            RequestFingerprint fingerprint
    ) {
        Objects.requireNonNull(namespaceScope, "namespaceScope must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");

        long now = clock.millis();
        ScopeKey key = new ScopeKey(namespaceScope, idempotencyKey);
        AtomicReference<IdempotencyReservation> outcomeRef = new AtomicReference<>();

        entries.compute(key, (k, existing) -> {
            StoreEntry active = (existing != null && !existing.isExpired(now)) ? existing : null;

            if (active == null && persistenceBackend != null) {
                Optional<IdempotencyPersistenceBackend.PersistedEntry> persisted =
                        persistenceBackend.findActive(namespaceScope, idempotencyKey, now);
                if (persisted.isPresent()) {
                    IdempotencyPersistenceBackend.PersistedEntry p = persisted.get();
                    active = new StoreEntry(
                            p.fingerprint(),
                            p.response(),
                            p.response().committedAtEpochMs(),
                            p.expiresAtEpochMs(),
                            sequenceGenerator.incrementAndGet()
                    );
                }
            }

            if (active == null) {
                outcomeRef.set(IdempotencyReservation.acquired(fingerprint));
                return new StoreEntry(
                        fingerprint,
                        null,
                        now,
                        now + ttlMillis,
                        sequenceGenerator.incrementAndGet()
                );
            }

            if (!active.fingerprint().equals(fingerprint)) {
                outcomeRef.set(IdempotencyReservation.mismatch(active.fingerprint()));
                return active;
            }

            if (active.isInFlight()) {
                outcomeRef.set(IdempotencyReservation.inFlight(active.fingerprint()));
                return active;
            }

            outcomeRef.set(IdempotencyReservation.replayed(active.response(), active.fingerprint()));
            return active;
        });

        IdempotencyReservation outcome = outcomeRef.get();
        if (outcome.status() == IdempotencyReservation.Status.ACQUIRED) {
            enforceBounds(now, key);
        }
        return outcome;
    }

    @Override
    public void commit(
            String namespaceScope,
            String idempotencyKey,
            RequestFingerprint fingerprint,
            StoredIdempotentResponse response
    ) {
        Objects.requireNonNull(namespaceScope, "namespaceScope must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(response, "response must not be null");

        long now = clock.millis();
        long expiresAt = now + ttlMillis;
        ScopeKey key = new ScopeKey(namespaceScope, idempotencyKey);

        entries.put(key, new StoreEntry(
                fingerprint,
                response,
                now,
                expiresAt,
                sequenceGenerator.incrementAndGet()
        ));

        if (persistenceBackend != null) {
            persistenceBackend.save(
                    new IdempotencyPersistenceBackend.PersistedEntry(
                            namespaceScope,
                            idempotencyKey,
                            fingerprint,
                            response,
                            expiresAt
                    ),
                    maxEntries,
                    now
            );
        }

        enforceBounds(now, key);
    }

    @Override
    public void release(String namespaceScope, String idempotencyKey) {
        if (namespaceScope == null || idempotencyKey == null) {
            return;
        }
        ScopeKey key = new ScopeKey(namespaceScope, idempotencyKey);
        entries.computeIfPresent(key, (k, existing) -> existing.isInFlight() ? null : existing);
    }

    @Override
    public int size() {
        long now = clock.millis();
        entries.entrySet().removeIf(e -> e.getValue().isExpired(now));
        return entries.size();
    }

    private void enforceBounds(long nowEpochMs, ScopeKey protectedKey) {
        if (entries.size() <= maxEntries) {
            return;
        }
        entries.entrySet().removeIf(e -> e.getValue().isExpired(nowEpochMs));
        while (entries.size() > maxEntries) {
            Optional<Map.Entry<ScopeKey, StoreEntry>> victim = entries.entrySet().stream()
                    .filter(e -> !e.getKey().equals(protectedKey) && !e.getValue().isInFlight())
                    .min(Comparator.comparingLong(e -> e.getValue().sequence()));
            if (victim.isEmpty()) {
                victim = entries.entrySet().stream()
                        .filter(e -> !e.getKey().equals(protectedKey))
                        .min(Comparator.comparingLong(e -> e.getValue().sequence()));
            }
            if (victim.isPresent()) {
                ScopeKey victimKey = victim.get().getKey();
                entries.remove(victimKey, victim.get().getValue());
                if (persistenceBackend != null) {
                    persistenceBackend.delete(victimKey.namespaceScope(), victimKey.idempotencyKey());
                }
            } else {
                break;
            }
        }
    }
}
