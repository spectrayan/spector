/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.namespace;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Manages active SpectorMemory instances with LRU eviction to control file descriptor and memory mapping limits.
 * Protected by {@link ReentrantLock} and performs lease-checking to avoid evicting busy namespaces.
 */
public final class NamespaceRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NamespaceRegistry.class);

    private final int maxActiveNamespaces;
    private final LinkedHashMap<String, SpectorMemory> activeMemories;
    private final ReentrantLock lock;

    private final AtomicInteger activeCount;
    private final AtomicInteger evictedCount;

    public NamespaceRegistry(int maxActiveNamespaces) {
        this.maxActiveNamespaces = maxActiveNamespaces;
        this.activeMemories = new LinkedHashMap<>(16, 0.75f, true);
        this.lock = new ReentrantLock();
        this.activeCount = new AtomicInteger(0);
        this.evictedCount = new AtomicInteger(0);

        logFdDiagnostics(maxActiveNamespaces);
    }

    /**
     * Retrieves an active namespace instance, opening it via the provided opener if cold/evicted.
     */
    public SpectorMemory getOrOpen(String namespaceId, Supplier<SpectorMemory> opener) {
        lock.lock();
        try {
            SpectorMemory mem = activeMemories.get(namespaceId);
            if (mem != null) {
                return mem;
            }

            log.info("Opening namespace '{}' (not currently active)", namespaceId);
            SpectorMemory newMem = opener.get();
            activeMemories.put(namespaceId, newMem);
            activeCount.set(activeMemories.size());

            checkEviction();

            return newMem;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if active namespace count exceeds capacity, and evicts the oldest idle namespace.
     */
    private void checkEviction() {
        if (activeMemories.size() > maxActiveNamespaces) {
            Iterator<Map.Entry<String, SpectorMemory>> it = activeMemories.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, SpectorMemory> entry = it.next();
                SpectorMemory memory = entry.getValue();

                if (memory instanceof DefaultSpectorMemory dm) {
                    if (!dm.hasActiveLeases()) {
                        it.remove();
                        activeCount.set(activeMemories.size());
                        evictedCount.incrementAndGet();
                        log.info("Evicting cold namespace '{}' to free up resources (FDs, mmap)", entry.getKey());
                        try {
                            dm.close();
                        } catch (Exception e) {
                            log.error("Failed to close evicted namespace '{}'", entry.getKey(), e);
                        }
                        break; // successfully evicted one, cache is back to capacity
                    }
                } else {
                    // Evict decorated/mock memories immediately
                    it.remove();
                    activeCount.set(activeMemories.size());
                    evictedCount.incrementAndGet();
                    log.info("Evicting custom namespace '{}'", entry.getKey());
                    try {
                        memory.close();
                    } catch (Exception e) {
                        log.error("Failed to close evicted custom namespace '{}'", entry.getKey(), e);
                    }
                    break;
                }
            }
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
        lock.lock();
        try {
            log.info("Closing NamespaceRegistry ({} active memories)", activeMemories.size());
            for (Map.Entry<String, SpectorMemory> entry : activeMemories.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.error("Failed to close namespace '{}' during registry shutdown", entry.getKey(), e);
                }
            }
            activeMemories.clear();
            activeCount.set(0);
        } finally {
            lock.unlock();
        }
    }
}
