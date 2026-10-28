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
package com.spectrayan.spector.memory.namespace;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Manages active {@link SpectorMemory} instances with LRU eviction to control file descriptor and memory mapping limits.
 *
 * <p>Cache lookups and LRU bookkeeping are protected by {@link ReentrantLock}, while cold namespace opens and
 * evicted namespace {@code close()} calls execute outside the registry lock so active namespace lookups are never
 * blocked by open or close I/O. Per-namespace locks and in-flight close futures serialize close-then-reopen for
 * the same namespace.</p>
 */
public final class NamespaceRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NamespaceRegistry.class);

    private final int maxActiveNamespaces;
    private final LinkedHashMap<String, SpectorMemory> activeMemories;
    private final ReentrantLock lock;
    private final ConcurrentHashMap<String, ReentrantLock> namespaceLocks;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> closingNamespaces;

    private final AtomicInteger activeCount;
    private final AtomicInteger evictedCount;
    private int inFlightOpens;
    private boolean closed;

    private record EvictedEntry(
            String namespaceId,
            SpectorMemory memory,
            CompletableFuture<Void> closeFuture,
            boolean defaultMemory
    ) {}

    public NamespaceRegistry(int maxActiveNamespaces) {
        if (maxActiveNamespaces <= 0) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_OUT_OF_RANGE,
                    "maxActiveNamespaces",
                    1,
                    Integer.MAX_VALUE,
                    maxActiveNamespaces);
        }
        this.maxActiveNamespaces = maxActiveNamespaces;
        this.activeMemories = new LinkedHashMap<>(16, 0.75f, true);
        this.lock = new ReentrantLock();
        this.namespaceLocks = new ConcurrentHashMap<>();
        this.closingNamespaces = new ConcurrentHashMap<>();
        this.activeCount = new AtomicInteger(0);
        this.evictedCount = new AtomicInteger(0);
        this.inFlightOpens = 0;
        this.closed = false;

        logFdDiagnostics(maxActiveNamespaces);
    }

    /**
     * Retrieves an active namespace instance, opening it via the provided opener if cold or previously evicted.
     *
     * @param namespaceId namespace identifier
     * @param opener      supplier that constructs/opens the namespace instance on a cache miss
     * @return the active {@link SpectorMemory} instance
     * @throws SpectorMemoryException if all active namespaces hold leases when {@code maxActiveNamespaces} is reached
     */
    public SpectorMemory getOrOpen(String namespaceId, Supplier<SpectorMemory> opener) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "namespaceId", namespaceId);
        }
        if (opener == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "opener");
        }

        // Fast path: check active LRU map under short lock without waiting on cold opens or eviction closes
        lock.lock();
        try {
            ensureOpenLocked();
            SpectorMemory existing = activeMemories.get(namespaceId);
            if (existing != null) {
                return existing;
            }
        } finally {
            lock.unlock();
        }

        ReentrantLock nsLock = namespaceLocks.computeIfAbsent(namespaceId, id -> new ReentrantLock());
        List<EvictedEntry> evictedEntries = List.of();
        SpectorMemory openedMemory;

        nsLock.lock();
        try {
            awaitPendingClose(namespaceId);

            lock.lock();
            try {
                ensureOpenLocked();
                SpectorMemory existing = activeMemories.get(namespaceId);
                if (existing != null) {
                    return existing;
                }

                evictedEntries = reserveCapacityLocked(namespaceId);
                inFlightOpens++;
            } finally {
                lock.unlock();
            }

            boolean registered = false;
            SpectorMemory closeOnRegistryShutdown = null;
            try {
                log.info("Opening namespace '{}' (not currently active)", namespaceId);
                openedMemory = opener.get();
                if (openedMemory == null) {
                    throw new SpectorValidationException(ErrorCode.ARGUMENT_NULL, "opener result");
                }

                lock.lock();
                try {
                    inFlightOpens--;
                    if (closed) {
                        closeOnRegistryShutdown = openedMemory;
                        throw new SpectorMemoryException(ErrorCode.MEMORY_NAMESPACE_FAILED, "NamespaceRegistry is closed");
                    }
                    activeMemories.put(namespaceId, openedMemory);
                    activeCount.set(activeMemories.size());
                    registered = true;
                } finally {
                    lock.unlock();
                }
            } finally {
                if (!registered && closeOnRegistryShutdown == null) {
                    lock.lock();
                    try {
                        inFlightOpens--;
                        activeCount.set(activeMemories.size());
                    } finally {
                        lock.unlock();
                    }
                }
                if (closeOnRegistryShutdown != null) {
                    closeQuietly(namespaceId, closeOnRegistryShutdown);
                }
            }
        } finally {
            nsLock.unlock();
            closeEvictedEntries(evictedEntries);
        }

        return openedMemory;
    }

    /**
     * Ensures capacity is available for one new namespace under {@link #lock}, evicting the oldest unleased
     * namespace(s) if necessary, or throwing {@link SpectorMemoryException} if all active entries hold leases.
     */
    private List<EvictedEntry> reserveCapacityLocked(String incomingNamespaceId) {
        int neededEvictions = (activeMemories.size() + inFlightOpens + 1) - maxActiveNamespaces;
        if (neededEvictions <= 0) {
            return List.of();
        }

        List<Map.Entry<String, SpectorMemory>> candidates = new ArrayList<>(neededEvictions);
        for (Map.Entry<String, SpectorMemory> entry : activeMemories.entrySet()) {
            if (!hasActiveLeases(entry.getValue())) {
                candidates.add(entry);
                if (candidates.size() == neededEvictions) {
                    break;
                }
            }
        }

        if (candidates.size() < neededEvictions) {
            throw new SpectorMemoryException(
                    ErrorCode.NAMESPACE_CAPACITY_EXHAUSTED,
                    maxActiveNamespaces,
                    incomingNamespaceId);
        }

        List<EvictedEntry> evicted = new ArrayList<>(candidates.size());
        for (Map.Entry<String, SpectorMemory> candidate : candidates) {
            String evictedKey = candidate.getKey();
            SpectorMemory evictedMem = candidate.getValue();
            activeMemories.remove(evictedKey);
            CompletableFuture<Void> closeFuture = new CompletableFuture<>();
            closingNamespaces.put(evictedKey, closeFuture);
            evictedCount.incrementAndGet();
            evicted.add(new EvictedEntry(
                    evictedKey,
                    evictedMem,
                    closeFuture,
                    evictedMem instanceof DefaultSpectorMemory));
        }
        activeCount.set(activeMemories.size());
        return evicted;
    }

    private static boolean hasActiveLeases(SpectorMemory memory) {
        if (memory instanceof DefaultSpectorMemory dm) {
            return dm.hasActiveLeases();
        }
        return memory.hasActiveLeases();
    }

    private void awaitPendingClose(String namespaceId) {
        CompletableFuture<Void> closingFuture = closingNamespaces.get(namespaceId);
        if (closingFuture != null) {
            closingFuture.join();
        }
    }

    private void closeEvictedEntries(List<EvictedEntry> evictedEntries) {
        for (EvictedEntry evicted : evictedEntries) {
            try {
                if (evicted.defaultMemory()) {
                    log.info("Evicting cold namespace '{}' to free up resources (FDs, mmap)", evicted.namespaceId());
                } else {
                    log.info("Evicting custom namespace '{}'", evicted.namespaceId());
                }
                evicted.memory().close();
            } catch (Exception e) {
                if (evicted.defaultMemory()) {
                    log.error("Failed to close evicted namespace '{}'", evicted.namespaceId(), e);
                } else {
                    log.error("Failed to close evicted custom namespace '{}'", evicted.namespaceId(), e);
                }
            } finally {
                evicted.closeFuture().complete(null);
                closingNamespaces.remove(evicted.namespaceId(), evicted.closeFuture());
            }
        }
    }

    private static void closeQuietly(String namespaceId, SpectorMemory memory) {
        try {
            memory.close();
        } catch (Exception e) {
            log.error("Failed to close namespace '{}'", namespaceId, e);
        }
    }

    private void ensureOpenLocked() {
        if (closed) {
            throw new SpectorMemoryException(ErrorCode.MEMORY_NAMESPACE_FAILED, "NamespaceRegistry is closed");
        }
    }

    /**
     * Returns the number of currently active namespaces.
     */
    public int activeCount() {
        return activeCount.get();
    }

    /**
     * Returns the running count of evicted namespaces.
     */
    public int evictedCount() {
        return evictedCount.get();
    }

    /**
     * Returns the maximum allowed active namespaces.
     */
    public int maxActiveNamespaces() {
        return maxActiveNamespaces;
    }

    /**
     * Logs diagnostics about file descriptor limits and expected budget.
     */
    public static void logFdDiagnostics(int maxActiveNamespaces) {
        java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.UnixOperatingSystemMXBean unixBean) {
            long maxFd = unixBean.getMaxFileDescriptorCount();
            long openFd = unixBean.getOpenFileDescriptorCount();
            long expectedFds = (long) maxActiveNamespaces * 15;
            log.info("File Descriptor Diagnostics: max={}, open={}, estimated_budget_for_namespaces={}", maxFd, openFd, expectedFds);
            if (expectedFds > maxFd / 2) {
                log.warn("Estimated FD budget for active namespaces ({}) exceeds 50% of system limit ({}). " +
                        "Consider increasing system RLIMIT_NOFILE or reducing maxActiveNamespaces.", expectedFds, maxFd);
            }
        } else {
            log.info("File Descriptor Diagnostics: operating system is not Unix-like or UnixOperatingSystemMXBean is not available");
        }
    }

    @Override
    public void close() {
        List<Map.Entry<String, SpectorMemory>> toClose;
        List<CompletableFuture<Void>> pendingEvictionCloses;
        lock.lock();
        try {
            closed = true;
            log.info("Closing NamespaceRegistry ({} active memories)", activeMemories.size());
            toClose = new ArrayList<>(activeMemories.entrySet());
            activeMemories.clear();
            activeCount.set(0);
            pendingEvictionCloses = new ArrayList<>(closingNamespaces.values());
        } finally {
            lock.unlock();
        }

        for (CompletableFuture<Void> pending : pendingEvictionCloses) {
            try {
                pending.join();
            } catch (Exception e) {
                log.error("Failed while awaiting in-flight namespace eviction close during registry shutdown", e);
            }
        }

        for (Map.Entry<String, SpectorMemory> entry : toClose) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.error("Failed to close namespace '{}' during registry shutdown", entry.getKey(), e);
            }
        }
    }
}
