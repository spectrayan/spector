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

import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import javax.security.auth.Destroyable;

/**
 * Zeroizable container for sensitive raw key material (such as unwrapped 256-bit DEKs
 * or derived HKDF subkeys) (ADR-0088 §5.1, §7.2, Issue #1070).
 *
 * <p>Calling {@link #destroy()} or {@link #close()} overwrites the internal byte buffer
 * with zeroes and issues a {@link VarHandle#fullFence()} memory barrier to prevent dead-store
 * elimination before marking the key material destroyed.</p>
 */
public final class DestroyableKeyMaterial implements Destroyable, AutoCloseable {

    private final ReentrantLock lock = new ReentrantLock();
    private final byte[] keyBytes;
    private volatile boolean destroyed;

    /**
     * Wraps a defensive copy of the provided key bytes, zeroizing the caller's input array
     * if {@code wipeSource} is {@code true}.
     */
    public DestroyableKeyMaterial(byte[] sourceBytes, boolean wipeSource) {
        Objects.requireNonNull(sourceBytes, "sourceBytes must not be null");
        if (sourceBytes.length == 0) {
            throw new IllegalArgumentException("sourceBytes must not be empty");
        }
        this.keyBytes = sourceBytes.clone();
        if (wipeSource) {
            Arrays.fill(sourceBytes, (byte) 0);
            VarHandle.fullFence();
        }
    }

    /**
     * Wraps a defensive copy of the provided key bytes.
     */
    public DestroyableKeyMaterial(byte[] sourceBytes) {
        this(sourceBytes, false);
    }

    /**
     * Returns the length in bytes of this key material.
     */
    public int length() {
        return keyBytes.length;
    }

    /**
     * Returns a copy of the raw key bytes. Throws {@link IllegalStateException} if this
     * key material has already been destroyed.
     */
    public byte[] bytes() {
        lock.lock();
        try {
            ensureNotDestroyed();
            return keyBytes.clone();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates an independent {@link DestroyableKeyMaterial} copy of this key material.
     */
    public DestroyableKeyMaterial copy() {
        lock.lock();
        try {
            ensureNotDestroyed();
            return new DestroyableKeyMaterial(keyBytes, false);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void destroy() {
        lock.lock();
        try {
            if (!destroyed) {
                Arrays.fill(keyBytes, (byte) 0);
                VarHandle.fullFence();
                destroyed = true;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Verifies that every byte in the internal backing array has been zeroized.
     * Intended for security verification tests.
     */
    public boolean isZeroized() {
        lock.lock();
        try {
            for (byte b : keyBytes) {
                if (b != 0) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        destroy();
    }

    private void ensureNotDestroyed() {
        if (destroyed) {
            throw new IllegalStateException("Key material has been destroyed and zeroized");
        }
    }

    @Override
    public String toString() {
        return "DestroyableKeyMaterial[length=" + keyBytes.length + ", destroyed=" + destroyed + "]";
    }
}
