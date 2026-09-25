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

import com.spectrayan.spector.error.SpectorException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class QuiesceGuardTest {

    @Test
    void testConcurrentWritersAllowed() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        CountDownLatch latch = new CountDownLatch(1);
        
        try (AutoCloseable p1 = guard.acquireWritePermit()) {
            AtomicBoolean p2Acquired = new AtomicBoolean(false);
            Thread t = new Thread(() -> {
                try (AutoCloseable p2 = guard.acquireWritePermit()) {
                    p2Acquired.set(true);
                } catch (Exception e) {}
                latch.countDown();
            });
            t.start();
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertTrue(p2Acquired.get());
        }
    }

    @Test
    void testQuiesceBlocksNewWriters() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        CountDownLatch writerReady = new CountDownLatch(1);
        CountDownLatch quiesceDone = new CountDownLatch(1);
        AtomicBoolean writerAcquired = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try {
                writerReady.await();
                try (AutoCloseable p = guard.acquireWritePermit()) {
                    writerAcquired.set(true);
                }
            } catch (Exception e) {}
        });
        writer.start();

        try (AutoCloseable q = guard.acquireQuiesce()) {
            writerReady.countDown();
            Thread.sleep(100);
            assertFalse(writerAcquired.get(), "Writer should block while quiesce is held");
        }
        
        writer.join(1000);
        assertTrue(writerAcquired.get(), "Writer should acquire after quiesce is released");
    }

    @Test
    void testQuiesceWaitsForInFlightWriters() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        CountDownLatch writerAcquired = new CountDownLatch(1);
        CountDownLatch writerRelease = new CountDownLatch(1);
        AtomicBoolean quiesceAcquired = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try (AutoCloseable p = guard.acquireWritePermit()) {
                writerAcquired.countDown();
                writerRelease.await();
            } catch (Exception e) {}
        });
        writer.start();

        writerAcquired.await();

        Thread quiescer = new Thread(() -> {
            try (AutoCloseable q = guard.acquireQuiesce()) {
                quiesceAcquired.set(true);
            } catch (Exception e) {}
        });
        quiescer.start();

        Thread.sleep(100);
        assertFalse(quiesceAcquired.get(), "Quiesce should wait for writer to release");

        writerRelease.countDown();
        quiescer.join(1000);
        assertTrue(quiesceAcquired.get(), "Quiesce should acquire after writer releases");
    }

    @Test
    void testQuiesceTimeoutThrows() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        
        try (AutoCloseable p = guard.acquireWritePermit()) {
            assertThrows(SpectorException.class, () -> {
                guard.acquireQuiesce(100, TimeUnit.MILLISECONDS);
            });
        }
    }

    @Test
    void testQuiesceDurationTracking() throws Exception {
        QuiesceGuard guard = new QuiesceGuard();
        
        try (AutoCloseable q = guard.acquireQuiesce()) {
            Thread.sleep(50);
        }
        
        assertTrue(guard.lastQuiesceDurationNanos() >= TimeUnit.MILLISECONDS.toNanos(50));
    }
}
