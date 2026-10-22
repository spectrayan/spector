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
package com.spectrayan.spector.memory.persist;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * One writer per on-disk store: an exclusive OS file lock on {@value #LOCK_FILE} inside the persistence
 * directory, held from open until {@code close()}.
 *
 * <p>Two processes writing the same directory overwrite each other's {@code runtime.bundle} and WAL. The
 * lock is taken before anything in the directory is read or written; a second opener (another process, or
 * a second memory in this JVM) fails with SPE-310-031. The OS drops the lock when the process dies, so a
 * crash never leaves the store locked.</p>
 */
public final class StoreDirectoryLock {

    /** Lock file name inside the persistence directory. */
    public static final String LOCK_FILE = ".spector-store.lock";

    private static final Logger log = LoggerFactory.getLogger(StoreDirectoryLock.class);
    private static final Map<Path, Held> HELD = new ConcurrentHashMap<>();

    private record Held(FileChannel channel, FileLock lock) {
    }

    private StoreDirectoryLock() {
    }

    /**
     * Locks {@code dir}, creating it if needed.
     *
     * @param dir the persistence directory
     * @throws SpectorConfigException with {@link ErrorCode#STORE_LOCKED} if another writer holds it, or
     *                                {@link ErrorCode#CONFIG_VALUE_INVALID} if the lock file cannot be created
     */
    public static void acquire(Path dir) {
        Path key = dir.toAbsolutePath().normalize();
        Path lockFile = key.resolve(LOCK_FILE);
        if (HELD.containsKey(key)) {
            throw new SpectorConfigException(ErrorCode.STORE_LOCKED, key, "this process");
        }
        FileChannel channel = null;
        try {
            Files.createDirectories(key);
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new SpectorConfigException(ErrorCode.STORE_LOCKED, key, "another process");
            }
            if (HELD.putIfAbsent(key, new Held(channel, lock)) != null) {
                lock.release();
                channel.close();
                throw new SpectorConfigException(ErrorCode.STORE_LOCKED, key, "this process");
            }
            log.debug("[Store] locked {}", lockFile);
        } catch (OverlappingFileLockException e) {
            closeQuietly(channel);
            throw new SpectorConfigException(ErrorCode.STORE_LOCKED, key, "this process");
        } catch (IOException e) {
            closeQuietly(channel);
            throw new SpectorConfigException(ErrorCode.CONFIG_VALUE_INVALID, "spector.memory.persistence-path",
                    "cannot create the store lock " + lockFile + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * Releases the lock on {@code dir} if this process holds it. Safe to call more than once.
     *
     * @param dir the persistence directory
     */
    public static void release(Path dir) {
        if (dir == null) {
            return;
        }
        Held held = HELD.remove(dir.toAbsolutePath().normalize());
        if (held != null) {
            try {
                held.lock().release();
            } catch (IOException e) {
                log.debug("[Store] lock release failed: {}", e.getClass().getSimpleName());
            }
            closeQuietly(held.channel());
        }
    }

    /**
     * @param dir the persistence directory
     * @return whether this process holds the lock on {@code dir}
     */
    public static boolean isHeld(Path dir) {
        return dir != null && HELD.containsKey(dir.toAbsolutePath().normalize());
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // nothing to do
            }
        }
    }
}
