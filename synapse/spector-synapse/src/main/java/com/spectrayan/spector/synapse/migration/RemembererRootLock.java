/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.migration;

import com.spectrayan.spector.kernel.storage.StoragePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Advisory inter-process lock over a rememberer root, held on {@code spector.lock}.
 *
 * <h3>Why this exists</h3>
 * <p>An in-process lease predicate cannot see mmap leases held by a different JVM. The migration CLI
 * runs as its own process, so asking a CLI-local resolver whether a namespace is leased always
 * answers "no" — the server holding the mapping is invisible to it. Relocating a directory that
 * another process has mapped is undefined behaviour, so the guard has to live on the filesystem
 * (Req R5.4).</p>
 *
 * <h3>Semantics</h3>
 * <p>A running Synapse holds this lock for its lifetime. {@link #tryAcquire(Path)} therefore
 * distinguishes three outcomes:</p>
 * <ul>
 *   <li><strong>Acquired</strong> — no other process is serving this root; a migration may proceed.</li>
 *   <li><strong>{@link Outcome#HELD_BY_OTHER_PROCESS}</strong> — another JVM owns the root. Refuse.</li>
 *   <li><strong>{@link Outcome#HELD_BY_THIS_JVM}</strong> — this JVM already holds it, which is the
 *       normal case for a migration triggered inside the server. The inter-process question is moot
 *       and the in-process lease guard is the authority.</li>
 * </ul>
 *
 * <p>The lock is advisory: it detects a cooperating Spector, not arbitrary external writers.</p>
 */
public final class RemembererRootLock implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RemembererRootLock.class);

    /** Result of an acquisition attempt. */
    public enum Outcome {
        ACQUIRED,
        HELD_BY_THIS_JVM,
        HELD_BY_OTHER_PROCESS,
        UNAVAILABLE
    }

    /**
     * An acquisition attempt. {@code lock} is non-null only when {@code outcome == ACQUIRED}.
     *
     * @param outcome what happened
     * @param lock    the held lock, or {@code null}
     */
    public record Attempt(Outcome outcome, RemembererRootLock lock) {
        public boolean mayMutateRoot() {
            return outcome == Outcome.ACQUIRED || outcome == Outcome.HELD_BY_THIS_JVM;
        }
    }

    private final Path lockFile;
    private final FileChannel channel;
    private final FileLock lock;

    private RemembererRootLock(Path lockFile, FileChannel channel, FileLock lock) {
        this.lockFile = lockFile;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to take the exclusive lock on {@code root}'s {@code spector.lock}.
     *
     * @param root the rememberer root
     * @return the attempt outcome; never {@code null}
     */
    public static Attempt tryAcquire(Path root) {
        Path lockFile = root.resolve(StoragePaths.FILE_LOCK);
        FileChannel channel = null;
        try {
            Files.createDirectories(root);
            channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            FileLock fileLock = channel.tryLock();
            if (fileLock == null) {
                closeQuietly(channel);
                return new Attempt(Outcome.HELD_BY_OTHER_PROCESS, null);
            }
            return new Attempt(Outcome.ACQUIRED, new RemembererRootLock(lockFile, channel, fileLock));
        } catch (OverlappingFileLockException e) {
            // A FileLock is owned by the JVM, not the thread: this means we already hold it, which
            // happens whenever a migration runs inside the server process that took the lock at
            // startup. Not a conflict.
            closeQuietly(channel);
            return new Attempt(Outcome.HELD_BY_THIS_JVM, null);
        } catch (IOException e) {
            closeQuietly(channel);
            log.warn("[RemembererRootLock] could not evaluate {}: {}", lockFile, e.getMessage());
            return new Attempt(Outcome.UNAVAILABLE, null);
        }
    }

    /** @return the lock file backing this lock. */
    public Path lockFile() {
        return lockFile;
    }

    @Override
    public void close() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            log.warn("[RemembererRootLock] failed to release {}: {}", lockFile, e.getMessage());
        } finally {
            closeQuietly(channel);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing actionable
        }
    }
}
