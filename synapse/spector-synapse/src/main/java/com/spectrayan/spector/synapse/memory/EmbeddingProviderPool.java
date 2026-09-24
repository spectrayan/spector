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
package com.spectrayan.spector.synapse.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.provider.ProviderFingerprint;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

/**
 * Reference-counted pool of embedding providers, keyed by {@link ProviderFingerprint}.
 *
 * <h3>Why fingerprint and not namespace</h3>
 * <p>An embedding provider is expensive — an HTTP client with its own connection pool, or an ONNX
 * inference session holding native memory. Keying a pool by {@code namespaceId} is the obvious move and
 * the wrong one: in the expected deployment shape most namespaces resolve to the same configuration, so
 * per-namespace instances would create N clients for one distinct model. That cost is exactly why the
 * code this replaces hoisted a single process-wide provider. The hoist was solving a real problem; it
 * just solved it by collapsing to one instance instead of one per distinct configuration.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>Reference counted. {@link #acquire} hands out a provider and increments its count;
 * {@link #release} decrements. A provider is closed only when its last reference goes away, and
 * <strong>never</strong> while a namespace still holds it. When the distinct-configuration cap is
 * reached, {@link #acquire} <em>refuses</em> rather than evicting: closing a provider out from under a
 * live namespace would surface later as unexplained recall failures with nothing pointing back here,
 * which is worse than a clear refusal at open time.</p>
 *
 * <h3>Concurrency</h3>
 * <p>Uses a {@link ReentrantLock} rather than {@code synchronized}, because the engine runs on virtual
 * threads and a monitor would pin the carrier thread.</p>
 */
public final class EmbeddingProviderPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderPool.class);

    /** Default cap on distinct live embedding configurations. */
    public static final int DEFAULT_MAX_CONFIGURATIONS = 16;

    /** A pooled provider and its reference count. */
    private static final class Entry {
        private final EmbeddingProvider provider;
        private int refCount;

        Entry(EmbeddingProvider provider) {
            this.provider = provider;
        }
    }

    private final Map<ProviderFingerprint, Entry> entries = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxConfigurations;
    private volatile boolean closed;

    /**
     * Creates a pool with the default configuration cap.
     */
    public EmbeddingProviderPool() {
        this(DEFAULT_MAX_CONFIGURATIONS);
    }

    /**
     * Creates a pool with an explicit cap on distinct live configurations.
     *
     * @param maxConfigurations the cap; values below 1 are raised to 1
     */
    public EmbeddingProviderPool(int maxConfigurations) {
        this.maxConfigurations = Math.max(1, maxConfigurations);
    }

    /**
     * Returns the provider for a configuration, creating it if this is the first reference.
     *
     * <p>The factory is invoked at most once per fingerprint, under the pool lock, so an expensive
     * provider is never constructed twice concurrently for the same configuration.</p>
     *
     * @param fingerprint the configuration identity, must not be {@code null}
     * @param factory     creates the provider on first use; must not return {@code null}
     * @return the shared provider for this configuration
     * @throws IllegalStateException if the pool is closed, or if admitting a new configuration would
     *                               exceed the cap
     */
    public EmbeddingProvider acquire(ProviderFingerprint fingerprint,
                                     Function<ProviderFingerprint, EmbeddingProvider> factory) {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("EmbeddingProviderPool is closed");
            }
            Entry existing = entries.get(fingerprint);
            if (existing != null) {
                existing.refCount++;
                log.debug("[EmbeddingProviderPool] reusing provider for {} (refs={})",
                        fingerprint.toLogString(), existing.refCount);
                return existing.provider;
            }
            if (entries.size() >= maxConfigurations) {
                // Refuse, do not evict. Every pooled provider is referenced by at least one live
                // namespace, so there is nothing here that can be closed safely.
                throw new IllegalStateException(String.format(
                        "Cannot admit a new embedding configuration %s: the pool already holds %d distinct "
                                + "live configurations, which is the configured maximum. Every pooled provider is "
                                + "referenced by an open namespace, so none can be closed to make room. Either "
                                + "reduce the number of distinct embedding configurations in use, or raise the "
                                + "cap.",
                        fingerprint.toLogString(), maxConfigurations));
            }
            EmbeddingProvider created = factory.apply(fingerprint);
            if (created == null) {
                throw new IllegalStateException(
                        "Provider factory returned null for " + fingerprint.toLogString());
            }
            Entry entry = new Entry(created);
            entry.refCount = 1;
            entries.put(fingerprint, entry);
            log.info("[EmbeddingProviderPool] created provider for {} (distinct configurations={})",
                    fingerprint.toLogString(), entries.size());
            return created;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops one reference to a configuration's provider, closing it when the last reference goes.
     *
     * <p>Releasing an unknown or already-fully-released fingerprint is a no-op rather than an error, so
     * that a failed build path can release defensively without needing to know how far it got.</p>
     *
     * @param fingerprint the configuration identity; {@code null} is ignored
     * @return {@code true} if this call closed the provider
     */
    public boolean release(ProviderFingerprint fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        lock.lock();
        try {
            Entry entry = entries.get(fingerprint);
            if (entry == null) {
                return false;
            }
            entry.refCount--;
            if (entry.refCount > 0) {
                log.debug("[EmbeddingProviderPool] released a reference to {} (refs={})",
                        fingerprint.toLogString(), entry.refCount);
                return false;
            }
            entries.remove(fingerprint);
            closeQuietly(entry.provider, fingerprint);
            log.info("[EmbeddingProviderPool] closed provider for {} (distinct configurations={})",
                    fingerprint.toLogString(), entries.size());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of distinct live configurations.
     *
     * <p>This is the number the gauge reports, and the number that must not grow with namespace count
     * when namespaces share a configuration.
     *
     * @return the distinct-configuration count
     */
    public int distinctConfigurations() {
        return entries.size();
    }

    /**
     * Returns the reference count for a configuration, or 0 if it is not pooled.
     *
     * @param fingerprint the configuration identity
     * @return the current reference count
     */
    public int referenceCount(ProviderFingerprint fingerprint) {
        if (fingerprint == null) {
            return 0;
        }
        lock.lock();
        try {
            Entry entry = entries.get(fingerprint);
            return entry != null ? entry.refCount : 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the cap on distinct live configurations.
     *
     * @return the configured maximum
     */
    public int maxConfigurations() {
        return maxConfigurations;
    }

    /**
     * Returns a snapshot of the pooled fingerprints, for diagnostics.
     *
     * @return the live fingerprints
     */
    public Set<ProviderFingerprint> fingerprints() {
        return Set.copyOf(entries.keySet());
    }

    /**
     * Closes every pooled provider regardless of reference count.
     *
     * <p>Only correct at shutdown, once no namespace will be served again.</p>
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            Map<ProviderFingerprint, Entry> snapshot = new LinkedHashMap<>(entries);
            entries.clear();
            snapshot.forEach((fingerprint, entry) -> closeQuietly(entry.provider, fingerprint));
            log.info("[EmbeddingProviderPool] closed {} pooled providers", snapshot.size());
        } finally {
            lock.unlock();
        }
    }

    private static void closeQuietly(EmbeddingProvider provider, ProviderFingerprint fingerprint) {
        try {
            provider.close();
        } catch (Exception e) {
            log.warn("[EmbeddingProviderPool] error closing provider for {}: {}",
                    fingerprint.toLogString(), e.getMessage());
        }
    }
}
