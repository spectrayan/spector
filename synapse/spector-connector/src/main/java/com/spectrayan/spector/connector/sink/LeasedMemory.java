/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.sink;

import com.spectrayan.spector.memory.SpectorMemory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ref-counted wrapper around {@link SpectorMemory} that prevents concurrent
 * close+re-open races between the eviction timer and HTTP request threads.
 *
 * <h3>Problem</h3>
 * <p>{@link TenantMemoryRegistry#evictIdle()} runs on a timer thread and can
 * call {@code closeQuietly()} on a namespace that's in the middle of serving
 * an HTTP request. Closing invalidates the Arena's MemorySegments, causing
 * {@code IllegalStateException} on the request thread.</p>
 *
 * <h3>Solution: Acquire/Release Leases</h3>
 * <pre>
 *   HTTP Thread:
 *     LeasedMemory leased = pool.get(key);
 *     SpectorMemory mem = leased.acquire();  // refCount++
 *     try {
 *         mem.remember(...);                 // safe — can't be evicted
 *     } finally {
 *         leased.release();                  // refCount--
 *     }
 *
 *   Eviction Thread:
 *     leased.markForEviction();              // sets flag
 *     if (leased.isEvictable()) {            // refCount == 0?
 *         pool.remove(key);
 *         leased.memory().close();           // safe — no active users
 *     }
 *     // else: skip, retry next cycle
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link AtomicInteger} for lock-free ref counting and a volatile
 * flag for eviction marking. The acquire/mark ordering prevents races:
 * acquire checks the flag after incrementing, mark sets the flag before
 * checking the count.</p>
 */
public final class LeasedMemory {

    private final SpectorMemory memory;
    private final AtomicInteger refCount = new AtomicInteger(0);
    private volatile boolean markedForEviction = false;

    public LeasedMemory(SpectorMemory memory) {
        this.memory = memory;
    }

    /**
     * Acquires a lease on this memory instance.
     *
     * <p>Returns the underlying {@link SpectorMemory} if the lease is
     * successfully acquired. Returns {@code null} if this instance has
     * been marked for eviction (the caller should re-load from disk).</p>
     *
     * @return the memory instance, or null if marked for eviction
     */
    public SpectorMemory acquire() {
        if (markedForEviction) return null;
        refCount.incrementAndGet();
        // Double-check after increment to close the race window:
        // if eviction was marked between our flag check and increment,
        // we roll back and return null.
        if (markedForEviction) {
            refCount.decrementAndGet();
            return null;
        }
        return memory;
    }

    /**
     * Releases a previously acquired lease.
     *
     * <p>Must be called in a finally block after {@link #acquire()}.
     * Decrements the ref count, allowing eviction when it reaches zero.</p>
     */
    public void release() {
        int prev = refCount.decrementAndGet();
        if (prev < 0) {
            // Bug: more releases than acquires — reset to prevent underflow
            refCount.set(0);
        }
    }

    /**
     * Marks this instance for eviction. After this call, no new leases
     * can be acquired.
     *
     * @return true if the instance can be safely closed now (refCount == 0)
     */
    public boolean markForEviction() {
        markedForEviction = true;
        return refCount.get() == 0;
    }

    /**
     * Returns true if eviction has been requested and all leases are released.
     */
    public boolean isEvictable() {
        return markedForEviction && refCount.get() == 0;
    }

    /**
     * Returns true if this instance has been marked for eviction.
     */
    public boolean isMarkedForEviction() {
        return markedForEviction;
    }

    /**
     * Returns the current number of active leases.
     */
    public int activeLeases() {
        return refCount.get();
    }

    /**
     * Returns the underlying memory instance.
     * <b>Use {@link #acquire()} for safe access.</b>
     */
    public SpectorMemory memory() {
        return memory;
    }
}
