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
package com.spectrayan.spector.commons.crypto;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded-TTL in-memory cache of unwrapped Data-Encryption Keys (DEKs) with explicit
 * zeroization on eviction/expiry and fail-closed KMS outage enforcement (ADR-0088 §7, Issue #1070).
 *
 * <h3>Security Invariants</h3>
 * <ul>
 *   <li><strong>Bounded TTL</strong>: Unwrapped DEKs are cached in heap memory for at most {@code ttl}
 *       (default {@code 5m}).</li>
 *   <li><strong>Explicit Zeroization</strong>: Whenever a cached DEK expires, is evicted, or is
 *       invalidated due to tenant KEK rotation/revocation, its backing {@link DestroyableKeyMaterial}
 *       is immediately overwritten with zeroes via {@link DestroyableKeyMaterial#destroy()}.</li>
 *   <li><strong>Fail Closed on KMS Outage or Revocation</strong>: After TTL expiry, {@link #getOrUnwrap}
 *       re-verifies with the configured {@link KeyProvider}. If the KMS is unreachable or denies
 *       decryption, the cache never serves expired key material and throws
 *       {@link ErrorCode#KMS_UNAVAILABLE_FAIL_CLOSED} ({@code SPE-310-027}) or
 *       {@link ErrorCode#BYOK_KEY_REVOKED} ({@code SPE-820-010} / {@code SPE-SEC-010}).</li>
 * </ul>
 */
public final class DekCache implements AutoCloseable {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    public static final int DEFAULT_MAX_ENTRIES = 4096;

    /**
     * Composite cache key identifying a specific version of a namespace DEK within a tenant.
     */
    public record DekCacheKey(String tenantId, String namespaceId, int dekVersion) {
        public DekCacheKey {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(namespaceId, "namespaceId must not be null");
            tenantId = tenantId.trim();
            namespaceId = namespaceId.trim();
        }
    }

    private record CachedDekEntry(
            DestroyableKeyMaterial keyMaterial,
            Instant cachedAt,
            Instant expiresAt) {
        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt) || keyMaterial.isDestroyed();
        }
    }

    private final ConcurrentHashMap<DekCacheKey, CachedDekEntry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;

    public DekCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    public DekCache(Duration ttl) {
        this(ttl, DEFAULT_MAX_ENTRIES, Clock.systemUTC());
    }

    public DekCache(Duration ttl, Clock clock) {
        this(ttl, DEFAULT_MAX_ENTRIES, clock);
    }

    public DekCache(Duration ttl, int maxEntries, Clock clock) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive, got " + maxEntries);
        }
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /**
     * Retrieves a copy of the unwrapped DEK if present and within TTL; otherwise invokes
     * {@link KeyProvider#unwrapDek} and caches the result for {@link #ttl()}.
     *
     * <p>Fails closed if the {@link KeyProvider} is unreachable or rejects the unwrap operation
     * after TTL expiry.</p>
     */
    public DestroyableKeyMaterial getOrUnwrap(
            String tenantId,
            String namespaceId,
            int dekVersion,
            String kekKeyId,
            byte[] wrappedDek,
            byte[] aad,
            KeyProvider keyProvider) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        Objects.requireNonNull(keyProvider, "keyProvider must not be null");

        DekCacheKey key = new DekCacheKey(tenantId, namespaceId, dekVersion);
        Instant now = clock.instant();

        CachedDekEntry existing = entries.get(key);
        if (existing != null) {
            if (!existing.isExpired(now)) {
                try {
                    return existing.keyMaterial().copy();
                } catch (IllegalStateException ignored) {
                    // Concurrent destroy; fall through to unwrap
                }
            }
            // Expired: remove and zeroize immediately before attempting KMS unwrap
            if (entries.remove(key, existing)) {
                existing.keyMaterial().destroy();
            }
        }

        // Unwrap via KMS — fail closed if KMS is unavailable or key is revoked
        final DestroyableKeyMaterial unwrapped;
        try {
            unwrapped = keyProvider.unwrapDek(tenantId.trim(), kekKeyId, wrappedDek, aad);
            if (unwrapped == null || unwrapped.isDestroyed()) {
                throw new SpectorMemoryException(
                        ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                        tenantId,
                        kekKeyId,
                        "KeyProvider returned null or destroyed DEK");
            }
        } catch (SpectorException se) {
            if (se.errorCode() == ErrorCode.BYOK_KEY_REVOKED) {
                invalidateTenant(tenantId);
                throw se;
            }
            if (se.errorCode() == ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED) {
                throw se;
            }
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    se,
                    tenantId,
                    kekKeyId,
                    se.getMessage());
        } catch (Exception e) {
            throw new SpectorMemoryException(
                    ErrorCode.KMS_UNAVAILABLE_FAIL_CLOSED,
                    e,
                    tenantId,
                    kekKeyId,
                    e.getMessage());
        }

        putInternal(key, unwrapped, now);
        return unwrapped.copy();
    }

    /**
     * Explicitly caches an unwrapped DEK (storing an internal copy that will be zeroized on eviction).
     */
    public void put(String tenantId, String namespaceId, int dekVersion, DestroyableKeyMaterial dek) {
        Objects.requireNonNull(dek, "dek must not be null");
        DekCacheKey key = new DekCacheKey(tenantId, namespaceId, dekVersion);
        putInternal(key, dek.copy(), clock.instant());
    }

    /**
     * Returns an independent copy of the cached DEK if present and not expired, or empty.
     * If an expired entry is encountered, it is immediately removed and zeroized.
     */
    public Optional<DestroyableKeyMaterial> getIfPresent(String tenantId, String namespaceId, int dekVersion) {
        DekCacheKey key = new DekCacheKey(tenantId, namespaceId, dekVersion);
        Instant now = clock.instant();
        CachedDekEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(now)) {
            if (entries.remove(key, entry)) {
                entry.keyMaterial().destroy();
            }
            return Optional.empty();
        }
        try {
            return Optional.of(entry.keyMaterial().copy());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * Evicts and zeroizes all cached DEK versions for the specified tenant and namespace.
     *
     * @return number of cached entries evicted and zeroized
     */
    public int invalidateNamespace(String tenantId, String namespaceId) {
        if (tenantId == null || namespaceId == null) {
            return 0;
        }
        String normTenant = tenantId.trim();
        String normNamespace = namespaceId.trim();
        int evicted = 0;
        Iterator<Map.Entry<DekCacheKey, CachedDekEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DekCacheKey, CachedDekEntry> mapEntry = it.next();
            DekCacheKey k = mapEntry.getKey();
            if (k.tenantId().equals(normTenant) && k.namespaceId().equals(normNamespace)) {
                it.remove();
                mapEntry.getValue().keyMaterial().destroy();
                evicted++;
            }
        }
        return evicted;
    }

    /**
     * Evicts and zeroizes all cached DEKs belonging to the specified tenant (e.g. upon
     * KEK revocation, rotation, or tenant erasure).
     *
     * @return number of cached entries evicted and zeroized
     */
    public int invalidateTenant(String tenantId) {
        if (tenantId == null) {
            return 0;
        }
        String normTenant = tenantId.trim();
        int evicted = 0;
        Iterator<Map.Entry<DekCacheKey, CachedDekEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DekCacheKey, CachedDekEntry> mapEntry = it.next();
            if (mapEntry.getKey().tenantId().equals(normTenant)) {
                it.remove();
                mapEntry.getValue().keyMaterial().destroy();
                evicted++;
            }
        }
        return evicted;
    }

    /**
     * Scans the cache and removes + zeroizes all entries whose TTL has elapsed.
     *
     * @return number of expired entries zeroized
     */
    public int purgeExpired() {
        Instant now = clock.instant();
        int purged = 0;
        Iterator<Map.Entry<DekCacheKey, CachedDekEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DekCacheKey, CachedDekEntry> mapEntry = it.next();
            if (mapEntry.getValue().isExpired(now)) {
                it.remove();
                mapEntry.getValue().keyMaterial().destroy();
                purged++;
            }
        }
        return purged;
    }

    /**
     * Evicts and zeroizes all cached DEKs across all tenants.
     */
    public void clear() {
        Iterator<Map.Entry<DekCacheKey, CachedDekEntry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DekCacheKey, CachedDekEntry> mapEntry = it.next();
            it.remove();
            mapEntry.getValue().keyMaterial().destroy();
        }
    }

    /**
     * Returns the current number of entries in the map (after purging expired entries).
     */
    public int size() {
        purgeExpired();
        return entries.size();
    }

    @Override
    public void close() {
        clear();
    }

    private void putInternal(DekCacheKey key, DestroyableKeyMaterial ownedCopy, Instant now) {
        if (entries.size() >= maxEntries) {
            purgeExpired();
            if (entries.size() >= maxEntries) {
                evictOldest();
            }
        }
        CachedDekEntry newEntry = new CachedDekEntry(ownedCopy, now, now.plus(ttl));
        CachedDekEntry previous = entries.put(key, newEntry);
        if (previous != null && previous.keyMaterial() != ownedCopy) {
            previous.keyMaterial().destroy();
        }
    }

    private void evictOldest() {
        DekCacheKey oldestKey = null;
        Instant oldestTime = null;
        for (Map.Entry<DekCacheKey, CachedDekEntry> entry : entries.entrySet()) {
            Instant cachedAt = entry.getValue().cachedAt();
            if (oldestTime == null || cachedAt.isBefore(oldestTime)) {
                oldestTime = cachedAt;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            CachedDekEntry removed = entries.remove(oldestKey);
            if (removed != null) {
                removed.keyMaterial().destroy();
            }
        }
    }
}
