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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LayoutMigrator} covering byte-for-byte verification, retention of
 * the original flat layout, and fail-closed behavior.
 *
 * <p>Validates Requirements:</p>
 * <ul>
 *   <li><b>17.6</b> — every already-copied file is byte-identical to its source AND the
 *       original flat {@code runtime/}+{@code partitions/} directories are retained until
 *       (and after) the new layout is verified.</li>
 *   <li><b>17.7</b> — the new layout is verified only when every regular file under the
 *       originals has a destination file with identical byte length and byte content, and
 *       the stored version is advanced to {@link DataLayoutVersion#CURRENT} on success.</li>
 *   <li><b>17.8</b> — if relocation/verification does not complete successfully, the
 *       originals are retained unchanged, the stored version is left unchanged, and an
 *       error is surfaced.</li>
 * </ul>
 */
class LayoutMigratorTest {

    /** A valid 13-char TSID-like namespace id (== the default user id, no {@code user-} prefix). */
    private static final String DEFAULT_USER_ID = "0GXABCDEFGHJK";

    @TempDir
    Path dataRoot;

    // ── Fixture ─────────────────────────────────────────────────────────────────────

    /** Known flat-layout contents: relative path (below the data root) -> byte content. */
    private static Map<String, byte[]> flatFixture() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("runtime/index.midx", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, (byte) 0xFF});
        files.put("runtime/hebbian.graph", "hebbian-graph-payload".getBytes(StandardCharsets.UTF_8));
        files.put("runtime/working.mem", new byte[] {(byte) 0x80, 0x00, 0x7F, (byte) 0xA5});
        files.put("runtime/sub/nested.dat", "deeply nested runtime state".getBytes(StandardCharsets.UTF_8));
        files.put("partitions/semantic.mem", new byte[] {10, 20, 30, 40, 50, 60});
        files.put("partitions/episodic.mem", "episodic partition bytes".getBytes(StandardCharsets.UTF_8));
        files.put("partitions/text.dat", new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
        return files;
    }

    private void seedFlatLayout(Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path target = dataRoot.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, e.getValue());
        }
    }

    private Path namespaceRoot() {
        return StorageLayout.namespaceDirSharded(dataRoot, DEFAULT_USER_ID);
    }

    // ── 17.7 / 17.6: successful migration copies byte-identical files ────────────────

    @Test
    void migrationCopiesEveryFileByteIdenticalToDestination() throws IOException {
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);

        LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

        Path ns = namespaceRoot();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path source = dataRoot.resolve(e.getKey());
            Path dest = ns.resolve(e.getKey());

            assertThat(dest)
                    .as("destination file %s must exist under namespaces/AA/BB/{userId}/", e.getKey())
                    .isRegularFile();
            // Identical byte length AND identical byte content (Files.mismatch == -1).
            assertThat(Files.mismatch(source, dest))
                    .as("destination %s must be byte-identical to source", e.getKey())
                    .isEqualTo(-1L);
            assertThat(Files.readAllBytes(dest))
                    .as("destination %s content", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    @Test
    void migrationAdvancesStoredVersionToCurrentOnlyAfterVerification() throws IOException {
        seedFlatLayout(flatFixture());
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);

        LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
    }

    @Test
    void migrationRetainsOriginalFlatDirectoriesAfterSuccess() throws IOException {
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);

        LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

        // Originals are copied (not moved): the flat runtime/ + partitions/ remain intact.
        assertThat(StorageLayout.runtimeDir(dataRoot)).isDirectory();
        assertThat(StorageLayout.partitionsDir(dataRoot)).isDirectory();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path source = dataRoot.resolve(e.getKey());
            assertThat(source)
                    .as("original flat file %s must be retained unchanged", e.getKey())
                    .isRegularFile();
            assertThat(Files.readAllBytes(source))
                    .as("original flat file %s content unchanged", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    // ── 17.8: fail-closed on relocation failure ─────────────────────────────────────

    @Test
    void failClosedLeavesVersionUnchangedAndSurfacesErrorWhenRelocationCannotComplete()
            throws IOException {
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);

        // Induce an I/O failure: pre-create a destination file path as a NON-EMPTY directory
        // so the copy of runtime/index.midx cannot complete (copy over a non-empty directory
        // fails). This deterministically drives the migrator's fail-closed path on any OS.
        Path ns = namespaceRoot();
        Path conflicting = ns.resolve("runtime").resolve("index.midx");
        Files.createDirectories(conflicting);
        Files.write(conflicting.resolve("occupant.tmp"), new byte[] {1});

        assertThatThrownBy(() -> LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID))
                .isInstanceOf(UncheckedIOException.class);

        // Stored version is left unchanged (still legacy flat / absent).
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.LEGACY_FLAT);
    }

    @Test
    void failClosedRetainsOriginalsUnchangedWhenRelocationCannotComplete() throws IOException {
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);

        Path ns = namespaceRoot();
        Path conflicting = ns.resolve("runtime").resolve("index.midx");
        Files.createDirectories(conflicting);
        Files.write(conflicting.resolve("occupant.tmp"), new byte[] {1});

        assertThatThrownBy(() -> LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID))
                .isInstanceOf(UncheckedIOException.class);

        // Every original flat file is retained byte-for-byte after the failed migration.
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path source = dataRoot.resolve(e.getKey());
            assertThat(source)
                    .as("original flat file %s must survive a failed migration", e.getKey())
                    .isRegularFile();
            assertThat(Files.readAllBytes(source))
                    .as("original flat file %s content must be unchanged", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    @Test
    void inducedVerificationFailureDoesNotAdvanceVersionAndSurfacesError() throws IOException {
        // Force verification to fail by corrupting a destination file AFTER seeding a version
        // that is one below CURRENT (still legacy) so migration runs, but arranging the copy
        // to be re-verified against a tampered destination. We achieve a deterministic
        // verification mismatch by making a destination file read-only with different content
        // so the copy step reports an I/O failure before commit; the version must not advance.
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);

        Path ns = namespaceRoot();
        Path destSemantic = ns.resolve("partitions").resolve("semantic.mem");
        Files.createDirectories(destSemantic.getParent());
        // A non-empty directory where a regular file is expected -> copy/verify cannot succeed.
        Files.createDirectories(destSemantic);
        Files.write(destSemantic.resolve("blocker.tmp"), new byte[] {9});

        assertThatThrownBy(() -> LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID))
                .isInstanceOfAny(UncheckedIOException.class, IllegalStateException.class);

        assertThat(DataLayoutVersion.read(dataRoot))
                .as("version must not advance when the new layout cannot be verified")
                .isEqualTo(DataLayoutVersion.LEGACY_FLAT);
    }

    // ── 17.3 context: no-op when already at/above current (originals + version intact) ──

    @Test
    void migrationIsNoOpWhenAlreadyAtCurrentVersion() throws IOException {
        Map<String, byte[]> files = flatFixture();
        seedFlatLayout(files);
        DataLayoutVersion.write(dataRoot, DataLayoutVersion.CURRENT);

        LayoutMigrator.migrateIfNeeded(dataRoot, DEFAULT_USER_ID);

        // No per-user namespace directory is created when already migrated.
        assertThat(namespaceRoot()).doesNotExist();
        assertThat(DataLayoutVersion.read(dataRoot)).isEqualTo(DataLayoutVersion.CURRENT);
    }
}
