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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorException;
import com.spectrayan.spector.commons.error.SpectorInternalException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Coordinates writers and checkpoint operations.
 * Writers acquire shared read permits; checkpoint acquires exclusive write lock 
 * for a bounded window during mutable bundle copy.
 */
public class QuiesceGuard {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long lastQuiesceDurationNanos;

    /**
     * An unchecked closeable permit for try-with-resources blocks.
     */
    @FunctionalInterface
    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Acquires a permit for writing. Multiple writers can hold permits concurrently.
     * Blocks if a checkpoint is actively holding the quiesce lock.
     *
     * @return an unchecked Permit
     */
    public Permit acquireWritePermit() {
        lock.readLock().lock();
        return () -> lock.readLock().unlock();
    }

    /**
     * Acquires an exclusive quiesce lock for a bounded duration.
     * Throws if the quiesce cannot be acquired within the timeout.
     *
     * @param timeout the maximum time to wait for the quiesce lock
     * @param unit the time unit of the timeout argument
     * @return an unchecked Permit that releases the quiesce lock and records its duration
     * @throws SpectorException if the quiesce lock cannot be acquired
     */
    public Permit acquireQuiesce(long timeout, TimeUnit unit) {
        try {
            if (!lock.writeLock().tryLock(timeout, unit)) {
                throw new SpectorInternalException(ErrorCode.INTERNAL_ERROR, "Failed to acquire quiesce lock within " + timeout + " " + unit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectorInternalException(ErrorCode.INTERNAL_ERROR, e, "Interrupted while waiting for quiesce lock");
        }

        long startNanos = System.nanoTime();
        return () -> {
            lastQuiesceDurationNanos = System.nanoTime() - startNanos;
            lock.writeLock().unlock();
        };
    }

    /**
     * Acquires an exclusive quiesce lock, waiting indefinitely.
     *
     * @return an unchecked Permit that releases the quiesce lock and records its duration
     */
    public Permit acquireQuiesce() {
        lock.writeLock().lock();
        long startNanos = System.nanoTime();
        return () -> {
            lastQuiesceDurationNanos = System.nanoTime() - startNanos;
            lock.writeLock().unlock();
        };
    }

    /**
     * Returns the duration of the last quiesce window in nanoseconds.
     * 
     * @return the last quiesce duration in nanoseconds
     */
    public long lastQuiesceDurationNanos() {
        return lastQuiesceDurationNanos;
    }
}
