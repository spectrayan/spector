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
import com.spectrayan.spector.synapse.migration.RemembererRootLock.Attempt;
import com.spectrayan.spector.synapse.migration.RemembererRootLock.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semantics of the advisory rememberer-root lock (Req R5.4).
 */
@DisplayName("RemembererRootLock: advisory inter-process root ownership")
class RemembererRootLockTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A free root is acquired and the lock file is created")
    void freeRootIsAcquired() {
        Attempt attempt = RemembererRootLock.tryAcquire(tempDir);
        try (RemembererRootLock lock = attempt.lock()) {
            assertThat(attempt.outcome()).isEqualTo(Outcome.ACQUIRED);
            assertThat(attempt.mayMutateRoot()).isTrue();
            assertThat(lock).isNotNull();
            assertThat(tempDir.resolve(StoragePaths.FILE_LOCK)).exists();
        }
    }

    @Test
    @DisplayName("A root already locked by this JVM is reported as such and still permits mutation")
    void sameJvmReacquisitionIsPermitted() {
        Attempt first = RemembererRootLock.tryAcquire(tempDir);
        try (RemembererRootLock held = first.lock()) {
            assertThat(first.outcome()).isEqualTo(Outcome.ACQUIRED);

            Attempt second = RemembererRootLock.tryAcquire(tempDir);
            assertThat(second.outcome())
                    .as("a FileLock is JVM-scoped, so re-acquiring inside the same JVM is not contention")
                    .isEqualTo(Outcome.HELD_BY_THIS_JVM);
            assertThat(second.lock()).isNull();
            assertThat(second.mayMutateRoot())
                    .as("migration triggered inside the server process must still be allowed to run; "
                            + "the in-process lease guard is the authority there")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("The lock is released on close, so a later attempt succeeds again")
    void lockIsReleasedOnClose() {
        Attempt first = RemembererRootLock.tryAcquire(tempDir);
        assertThat(first.outcome()).isEqualTo(Outcome.ACQUIRED);
        first.lock().close();

        Attempt second = RemembererRootLock.tryAcquire(tempDir);
        try (RemembererRootLock lock = second.lock()) {
            assertThat(second.outcome()).isEqualTo(Outcome.ACQUIRED);
            assertThat(lock).isNotNull();
        }
    }

    @Test
    @DisplayName("A missing root directory is created rather than failing the attempt")
    void missingRootIsCreated() {
        Path nested = tempDir.resolve("cognitive").resolve("deeper");
        assertThat(Files.exists(nested)).isFalse();

        Attempt attempt = RemembererRootLock.tryAcquire(nested);
        try (RemembererRootLock lock = attempt.lock()) {
            assertThat(attempt.outcome()).isEqualTo(Outcome.ACQUIRED);
            assertThat(lock).isNotNull();
            assertThat(nested).isDirectory();
        }
    }
}
