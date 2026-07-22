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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;

/**
 * Property-based tests (jqwik) for <b>Property 10: Migration idempotency &amp;
 * monotonicity</b> of the multi-user auth design.
 *
 * <p>This class is intentionally separate from the other storage-layout property
 * tests so that each correctness property lives in its own compilation unit.</p>
 *
 * <h3>Property 10: Migration idempotency &amp; monotonicity</h3>
 * <p>For ANY seeded flat data root,
 * {@code LayoutMigrator.migrateIfNeeded(migrateIfNeeded(dataRoot, u), u)} produces the
 * same final directory tree and stored layout version as a single application
 * (double-apply {@code ==} single-apply); and {@link DataLayoutVersion#read(Path)}
 * after migration is {@code >=} its prior value and never decreases across repeated
 * migrations.</p>
 *
 * <p>Fixtures are randomized flat layouts: varied files under {@code runtime/} and
 * {@code partitions/} (including nested partition subdirectories) with random byte
 * content, seeded under a fresh temp data root per try. The namespace id is the
 * {@code User_Id} itself (no {@code user-} prefix), matching
 * {@link StorageLayout#namespaceDirSharded(Path, String)}.</p>
 *
 * <p><b>Validates: Requirements 17.4, 17.5</b></p>
 */
class LayoutMigrationIdempotencyPropertyTest {

    /** Fresh, empty data root created per try. */
    private Path dataRoot;

    @BeforeTry
    void createDataRoot() throws IOException {
        dataRoot = Files.createTempDirectory("spector-migration-prop-").toAbsolutePath().normalize();
    }

    @AfterTry
    void deleteDataRoot() throws IOException {
        deleteRecursively(dataRoot);
    }

    // ── Generators ──

    /**
     * Valid {@code User_Id} / namespace id values accepted by
     * {@link StorageLayout#validateNamespaceId(String)}: non-blank, length 1..256,
     * free of {@code '/'}, {@code '\\'}, {@code '.'}, the null byte, and any C0
     * control character. Mixes realistic 13-char TSID-style ids with longer
     * arbitrary valid ids.
     */
    @Provide
    Arbitrary<String> validUserIds() {
        Arbitrary<String> tsidLike = Arbitraries.strings()
                .withChars("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
                .ofLength(13);
        Arbitrary<String> generalValid = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_~")
                .ofMinLength(1)
                .ofMaxLength(64);
        return Arbitraries.oneOf(tsidLike, generalValid);
    }

    /**
     * A randomized flat-layout fixture: a map of relative path (beneath the data
     * root, always under {@code runtime/} or {@code partitions/}) to random byte
     * content. May be empty (a data root with no flat files is still a valid
     * migration input).
     */
    @Provide
    Arbitrary<Map<String, byte[]>> flatFixtures() {
        Arbitrary<String> topDir = Arbitraries.of(
                StorageLayout.DIR_RUNTIME, StorageLayout.DIR_PARTITIONS);
        // Partition-style subdirectory (e.g. 000_1717430400) or a direct file.
        Arbitrary<String> subPath = Arbitraries.oneOf(
                Arbitraries.strings().withChars("abcdefghij0123456789").ofMinLength(1).ofMaxLength(12),
                Combinators.combine(
                                Arbitraries.strings().withChars("0123456789").ofLength(3),
                                Arbitraries.strings().withChars("abcdefgh0123456789").ofMinLength(1).ofMaxLength(10))
                        .as((dir, file) -> dir + "/" + file));
        Arbitrary<String> relativePath = Combinators.combine(topDir, subPath)
                .as((top, rest) -> top + "/" + rest);
        Arbitrary<byte[]> content = Arbitraries.bytes().array(byte[].class).ofMaxSize(256);

        Arbitrary<Map<String, byte[]>> entries = Arbitraries.maps(relativePath, content)
                .ofMaxSize(8);
        return entries;
    }

    // ── Property 10a: double-apply equals single-apply (tree + version) ──

    @Property(tries = 300)
    void doubleApplyEqualsSingleApply(
            @ForAll("validUserIds") String userId,
            @ForAll("flatFixtures") Map<String, byte[]> fixture,
            @ForAll @IntRange(min = 0, max = DataLayoutVersion.CURRENT) int initialVersion) {

        seedFlatLayout(dataRoot, fixture, initialVersion);

        // Single application.
        LayoutMigrator.migrateIfNeeded(dataRoot, userId);
        Map<String, String> treeAfterSingle = snapshotTreeWithContent(dataRoot);
        int versionAfterSingle = DataLayoutVersion.read(dataRoot);

        // Second application (double-apply) must be a no-op relative to the first.
        LayoutMigrator.migrateIfNeeded(dataRoot, userId);
        Map<String, String> treeAfterDouble = snapshotTreeWithContent(dataRoot);
        int versionAfterDouble = DataLayoutVersion.read(dataRoot);

        assertThat(treeAfterDouble)
                .as("double-apply must produce the same final directory tree as single-apply")
                .isEqualTo(treeAfterSingle);
        assertThat(versionAfterDouble)
                .as("double-apply must produce the same stored layout version as single-apply")
                .isEqualTo(versionAfterSingle);
        assertThat(versionAfterSingle)
                .as("a completed migration advances the stored version to CURRENT")
                .isEqualTo(DataLayoutVersion.CURRENT);
    }

    // ── Property 10b: stored version is monotonic (>= prior, never decreases) ──

    @Property(tries = 300)
    void versionNeverDecreasesAcrossRepeatedMigrations(
            @ForAll("validUserIds") String userId,
            @ForAll("flatFixtures") Map<String, byte[]> fixture,
            @ForAll @IntRange(min = 0, max = DataLayoutVersion.CURRENT) int initialVersion,
            @ForAll @IntRange(min = 1, max = 6) int repetitions) {

        seedFlatLayout(dataRoot, fixture, initialVersion);

        int prior = DataLayoutVersion.read(dataRoot);
        assertThat(prior).as("seeded prior version").isEqualTo(initialVersion);

        int previous = prior;
        for (int i = 0; i < repetitions; i++) {
            LayoutMigrator.migrateIfNeeded(dataRoot, userId);
            int current = DataLayoutVersion.read(dataRoot);

            assertThat(current)
                    .as("stored version after migration must be >= its prior value")
                    .isGreaterThanOrEqualTo(prior);
            assertThat(current)
                    .as("stored version must never decrease across repeated migrations")
                    .isGreaterThanOrEqualTo(previous);
            previous = current;
        }

        assertThat(previous)
                .as("after migrating, the stored version reaches CURRENT")
                .isEqualTo(DataLayoutVersion.CURRENT);
    }

    // ── Helpers ──

    /**
     * Seeds a flat layout at {@code dataRoot}: writes each fixture file (creating
     * parent directories) and, when {@code initialVersion > LEGACY_FLAT}, records
     * the initial stored layout version. A {@code LEGACY_FLAT} (0) initial version
     * is left implicit (no marker file), matching an untracked legacy deployment.
     */
    private static void seedFlatLayout(Path dataRoot, Map<String, byte[]> fixture, int initialVersion) {
        try {
            Files.createDirectories(dataRoot);
            for (Map.Entry<String, byte[]> e : fixture.entrySet()) {
                Path target = dataRoot.resolve(e.getKey()).normalize();
                // Skip entries whose path collides with an existing file/dir in the
                // generated tree (e.g. one key is a file, another needs it as a parent
                // directory). Any valid subset of the fixture is a valid flat layout.
                Path parent = target.getParent();
                if (parent != null && !createDirsQuietly(parent)) {
                    continue;
                }
                if (Files.isDirectory(target)) {
                    continue;
                }
                Files.write(target, e.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (initialVersion > DataLayoutVersion.LEGACY_FLAT) {
            DataLayoutVersion.write(dataRoot, initialVersion);
        }
    }

    /**
     * Snapshots the entire tree rooted at {@code root} as a map from the relative
     * path (with {@code /} separators) to a stable content descriptor. Regular
     * files map to {@code "f:" + length + ":" + sha256hex}; directories map to
     * {@code "d"}. This captures both the directory structure and byte-for-byte
     * file content so idempotency can be asserted precisely.
     */
    private static Map<String, String> snapshotTreeWithContent(Path root) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted().toList();
            for (Path p : paths) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (Files.isRegularFile(p)) {
                    byte[] bytes = Files.readAllBytes(p);
                    snapshot.put(rel, "f:" + bytes.length + ":" + sha256Hex(bytes));
                } else {
                    snapshot.put(rel, "d");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return snapshot;
    }

    /**
     * Creates {@code dir} and its ancestors, returning {@code false} (rather than
     * throwing) when the path or an ancestor already exists as a regular file — a
     * benign collision between two generated fixture entries.
     */
    private static boolean createDirsQuietly(Path dir) {
        try {
            Files.createDirectories(dir);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Lowercase hex SHA-256 of the given bytes, used as a byte-for-byte content fingerprint. */
    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
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
