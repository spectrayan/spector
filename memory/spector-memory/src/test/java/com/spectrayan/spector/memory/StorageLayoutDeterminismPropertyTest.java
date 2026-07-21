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
package com.spectrayan.spector.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Property-based tests for {@link StorageLayout#namespaceDirSharded(Path, String)}
 * <b>path determinism / purity</b> (jqwik).
 *
 * <p>This class is intentionally separate from the isolation/well-formedness
 * property tests to keep each correctness property in its own compilation unit.</p>
 *
 * <h3>Property 2: Path determinism / purity</h3>
 * <p>{@code ∀ u: namespaceDirSharded(base, u)} called repeatedly (in any registry
 * state, any thread interleaving) returns byte-for-byte equal {@link Path} values
 * and performs no filesystem mutation during resolution.</p>
 *
 * <p><b>Validates: Requirements 8.8</b></p>
 */
class StorageLayoutDeterminismPropertyTest {

    /** Fresh, empty base directory created per try; resolution must never mutate it. */
    private Path base;

    @BeforeTry
    void createBase() throws IOException {
        base = Files.createTempDirectory("spector-determinism-");
    }

    @AfterTry
    void deleteBase() throws IOException {
        if (base != null && Files.exists(base)) {
            try (Stream<Path> walk = Files.walk(base)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    // ── Generators ──

    /**
     * Smart generator for valid User_Id (namespace) values.
     *
     * <p>Constrains to the input space accepted by
     * {@link StorageLayout#validateNamespaceId(String)}: non-null, non-blank,
     * length 1..256, and free of {@code '/'}, {@code '\\'}, {@code '.'}, the null
     * byte, and any C0 control character (U+0000..U+001F). Produces both realistic
     * 13-char TSID-style identifiers (Crockford Base32) and longer arbitrary valid
     * identifiers drawn from a safe alphabet.</p>
     */
    @Provide
    Arbitrary<String> validUserIds() {
        // Crockford Base32 alphabet used by TSIDs (excludes I, L, O, U).
        Arbitrary<String> tsidLike = Arbitraries.strings()
                .withChars("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
                .ofLength(13);

        // General valid identifiers from a safe alphabet (all pass validation).
        Arbitrary<String> generalValid = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_~")
                .ofMinLength(1)
                .ofMaxLength(256);

        return Arbitraries.oneOf(tsidLike, generalValid);
    }

    // ── Property 2: repeated-call determinism + purity (sequential) ──

    @Property(tries = 500)
    void repeatedCallsReturnByteForByteEqualPathsAndDoNotMutateFilesystem(
            @ForAll("validUserIds") String userId) {

        Set<String> before = snapshotTree(base);

        Path reference = StorageLayout.namespaceDirSharded(base, userId);
        for (int i = 0; i < 64; i++) {
            Path again = StorageLayout.namespaceDirSharded(base, userId);
            // byte-for-byte equality: object equality AND identical string form.
            assertThat(again).isEqualTo(reference);
            assertThat(again.toString()).isEqualTo(reference.toString());
        }

        Set<String> after = snapshotTree(base);
        assertThat(after)
                .as("namespaceDirSharded must perform no filesystem mutation during resolution")
                .isEqualTo(before);
    }

    // ── Property 2: concurrent-call determinism + purity ──

    @Property(tries = 120)
    void concurrentCallsReturnByteForByteEqualPathsAndDoNotMutateFilesystem(
            @ForAll("validUserIds") String userId) throws InterruptedException {

        Set<String> before = snapshotTree(base);

        Path reference = StorageLayout.namespaceDirSharded(base, userId);

        final int threads = 8;
        final int callsPerThread = 32;
        ConcurrentLinkedQueue<Path> results = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        for (int i = 0; i < callsPerThread; i++) {
                            results.add(StorageLayout.namespaceDirSharded(base, userId));
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }
            startGate.countDown(); // release all threads to maximize interleaving
            boolean finished = doneGate.await(30, TimeUnit.SECONDS);
            assertThat(finished).as("all concurrent resolution threads completed").isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failure.get())
                .as("no thread threw during concurrent resolution")
                .isNull();
        assertThat(results)
                .as("every concurrent resolution returned the same path")
                .hasSize(threads * callsPerThread)
                .allSatisfy(p -> {
                    assertThat(p).isEqualTo(reference);
                    assertThat(p.toString()).isEqualTo(reference.toString());
                });

        Set<String> after = snapshotTree(base);
        assertThat(after)
                .as("concurrent resolution must perform no filesystem mutation")
                .isEqualTo(before);
    }

    // ── Helpers ──

    /**
     * Snapshots the entire directory tree rooted at {@code root} as the sorted set
     * of paths relativized to {@code root}. Used to detect any filesystem mutation
     * (creation, deletion, or renaming) caused by path resolution.
     */
    private static Set<String> snapshotTree(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            List<String> entries = walk
                    .map(p -> root.relativize(p).toString())
                    .collect(Collectors.toList());
            return new TreeSet<>(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
