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

/**
 * Reference-counted pool of expensive provider instances, keyed by {@link ProviderFingerprint}.
 *
 * <h3>Why fingerprint and not namespace</h3>
 * <p>A provider is expensive — an HTTP client with its own connection pool, or an ONNX inference session
 * holding native memory. Keying a pool by {@code namespaceId} is the obvious move and the wrong one: in the
 * expected deployment shape most namespaces resolve to the same configuration, so per-namespace instances
 * would create N clients for one distinct model. That cost is why the code this replaced hoisted a single
 * process-wide provider; the hoist was solving a real problem, it just solved it by collapsing to one
 * instance instead of one per distinct configuration.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>{@link #acquire} hands out a provider and increments its count; {@link #release} decrements. A provider
 * is closed only when its last reference goes away, and <strong>never</strong> while a namespace still holds
 * it. At the distinct-configuration cap {@link #acquire} <em>refuses</em> rather than evicting: closing a
 * provider out from under a live namespace would surface later as unexplained failures with nothing pointing
 * back here, which is worse than a clear refusal at open time.</p>
 *
 * <h3>Concurrency</h3>
 * <p>Uses a {@link ReentrantLock} rather than {@code synchronized}, because the engine runs on virtual
 * threads and a monitor would pin the carrier thread.</p>
 *
 * <p>The type parameter is unbounded rather than {@code AutoCloseable}: {@code LlmProvider} does not
 * implement it, and requiring it would exclude the very type this pool was generalised for. Providers that
 * are {@code AutoCloseable} are closed; those that are not are simply dropped.</p>
 *
 * @param <T> the pooled provider type
 */
public class ReferenceCountedProviderPool<T> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReferenceCountedProviderPool.class);

    /** Default cap on distinct live configurations. */
    public static final int DEFAULT_MAX_CONFIGURATIONS = 16;

    /** A pooled provider and its reference count. */
    private static final class Entry<T> {
        private final T provider;
        private int refCount;

        Entry(T provider) {
            this.provider = provider;
        }
    }

    private final Map<ProviderFingerprint, Entry<T>> entries = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxConfigurations;
    private final String kind;
    private volatile boolean closed;

    /**
     * Creates a pool.
     *
     * @param kind              a short name for this pool's contents, used in log and error messages
     * @param maxConfigurations the cap on distinct live configurations; values below 1 are raised to 1
     */
    protected ReferenceCountedProviderPool(String kind, int maxConfigurations) {
        this.kind = kind == null || kind.isBlank() ? "provider" : kind;
        this.maxConfigurations = Math.max(1, maxConfigurations);
    }

    /**
     * Returns the provider for a configuration, creating it if this is the first reference.
     *
     * <p>The factory is invoked at most once per fingerprint, under the pool lock, so an expensive provider
     * is never constructed twice concurrently for the same configuration.</p>
     *
     * @param fingerprint the configuration identity, must not be {@code null}
     * @param factory     creates the provider on first use; must not return {@code null}
     * @return the shared provider for this configuration
     * @throws IllegalStateException if the pool is closed, or if admitting a new configuration would exceed
     *                               the cap
     */
    public T acquire(ProviderFingerprint fingerprint, Function<ProviderFingerprint, T> factory) {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException(poolName() + " is closed");
            }
            Entry<T> existing = entries.get(fingerprint);
            if (existing != null) {
                existing.refCount++;
                log.debug("[{}] reusing {} for {} (refs={})", poolName(), kind,
                        fingerprint.toLogString(), existing.refCount);
                return existing.provider;
            }
            if (entries.size() >= maxConfigurations) {
                // Refuse, do not evict. Every pooled provider is referenced by at least one live namespace,
                // so there is nothing here that can be closed safely.
                throw new IllegalStateException(String.format(
                        "Cannot admit a new %s configuration %s: the pool already holds %d distinct live "
                                + "configurations, which is the configured maximum. Every pooled provider is "
                                + "referenced by an open namespace, so none can be closed to make room. Either "
                                + "reduce the number of distinct configurations in use, or raise the cap.",
                        kind, fingerprint.toLogString(), maxConfigurations));
            }
            T created = factory.apply(fingerprint);
            if (created == null) {
                throw new IllegalStateException(
                        kind + " factory returned null for " + fingerprint.toLogString());
            }
            Entry<T> entry = new Entry<>(created);
            entry.refCount = 1;
            entries.put(fingerprint, entry);
            log.info("[{}] created {} for {} (distinct configurations={})", poolName(), kind,
                    fingerprint.toLogString(), entries.size());
            return created;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drops one reference to a configuration's provider, closing it when the last reference goes.
     *
     * <p>Releasing an unknown or already-fully-released fingerprint is a no-op rather than an error, so a
     * failed build path can release defensively without needing to know how far it got.</p>
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
            Entry<T> entry = entries.get(fingerprint);
            if (entry == null) {
                return false;
            }
            entry.refCount--;
            if (entry.refCount > 0) {
                log.debug("[{}] released a reference to {} (refs={})", poolName(),
                        fingerprint.toLogString(), entry.refCount);
                return false;
            }
            entries.remove(fingerprint);
            closeQuietly(entry.provider, fingerprint);
            log.info("[{}] closed {} for {} (distinct configurations={})", poolName(), kind,
                    fingerprint.toLogString(), entries.size());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of distinct live configurations.
     *
     * <p>This is the number a gauge reports, and the number that must not grow with namespace count when
     * namespaces share a configuration.</p>
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
            Entry<T> entry = entries.get(fingerprint);
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
            Map<ProviderFingerprint, Entry<T>> snapshot = new LinkedHashMap<>(entries);
            entries.clear();
            snapshot.forEach((fingerprint, entry) -> closeQuietly(entry.provider, fingerprint));
            log.info("[{}] closed {} pooled {}s", poolName(), snapshot.size(), kind);
        } finally {
            lock.unlock();
        }
    }

    private String poolName() {
        return getClass().getSimpleName();
    }

    private void closeQuietly(T provider, ProviderFingerprint fingerprint) {
        if (!(provider instanceof AutoCloseable closeable)) {
            // Not every provider type owns resources. LlmProvider, for one, is a stateless facade over an
            // HTTP client the factory manages, so there is nothing to close here.
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("[{}] error closing {} for {}: {}", poolName(), kind,
                    fingerprint.toLogString(), e.getMessage());
        }
    }
}
