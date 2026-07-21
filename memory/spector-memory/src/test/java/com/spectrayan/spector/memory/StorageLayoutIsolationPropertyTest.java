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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Property-based tests (jqwik) for <b>Property 1: Per-user filesystem isolation
 * (no overlap / no traversal)</b> of the multi-user auth design.
 *
 * <p>For ANY two distinct valid {@code User_Id} values,
 * {@link StorageLayout#namespaceDirSharded(Path, String)} resolves two distinct
 * paths where neither is an ancestor of the other; for ANY valid {@code User_Id}
 * the resolved path is a strict descendant of the configured base (no {@code ..}
 * escape); and any identifier containing {@code /}, {@code \}, {@code .}, a null
 * byte, or any C0 control character (U+0000..U+001F) is rejected with an
 * {@link IllegalArgumentException} before path resolution, resolving no path and
 * performing no filesystem mutation.</p>
 *
 * <p><b>Validates: Requirements 8.1, 8.2, 8.3, 8.4</b></p>
 */
class StorageLayoutIsolationPropertyTest {

    /**
     * A base persistence root shared across tries. Path resolution is a pure
     * function that never touches the filesystem, so a single stable root is
     * sufficient and lets us assert that no directory is ever created.
     */
    private static Path base;

    @BeforeContainer
    static void createBase() throws IOException {
        base = Files.createTempDirectory("spector-isolation-prop").toAbsolutePath().normalize();
    }

    @AfterContainer
    static void deleteBase() throws IOException {
        if (base != null && Files.exists(base)) {
            try (Stream<Path> walk = Files.walk(base)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
        }
    }

    // ── Generators ──

    /**
     * Valid {@code User_Id} values: non-blank identifiers drawn from a safe
     * alphabet (alphanumeric plus {@code -} and {@code _}) that excludes every
     * character the layout must reject. Length is constrained to 1..256 to stay
     * within {@link StorageLayout#MAX_NAMESPACE_ID_LENGTH}.
     */
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .withChars("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
                .ofMinLength(1)
                .ofMaxLength(256)
                .filter(s -> !s.isBlank());
    }

    /**
     * Identifiers that contain at least one forbidden character: a path separator
     * ({@code /} or {@code \}), a dot ({@code .}), or a C0 control character in the
     * range U+0000..U+001F (which includes the null byte).
     */
    @Provide
    Arbitrary<String> forbiddenUserIds() {
        Arbitrary<Character> forbiddenChar = Arbitraries.oneOf(
                Arbitraries.of('/', '\\', '.'),
                Arbitraries.chars().range('\u0000', '\u001F'));
        Arbitrary<String> prefix = Arbitraries.strings().withChars("ab01-_").ofMaxLength(8);
        Arbitrary<String> suffix = Arbitraries.strings().withChars("ab01-_").ofMaxLength(8);
        return Combinators.combine(prefix, forbiddenChar, suffix)
                .as((p, c, s) -> p + c + s);
    }

    // ── Properties ──

    /**
     * Requirement 8.2 — any two distinct valid User_Id values resolve to two
     * distinct directory paths where neither path is an ancestor of the other.
     */
    @Property
    void distinctUsersResolveToDistinctNonAncestorPaths(
            @ForAll("validUserIds") String u1,
            @ForAll("validUserIds") String u2) {
        Assume.that(!u1.equals(u2));

        Path p1 = StorageLayout.namespaceDirSharded(base, u1).toAbsolutePath().normalize();
        Path p2 = StorageLayout.namespaceDirSharded(base, u2).toAbsolutePath().normalize();

        assertThat(p1).isNotEqualTo(p2);
        assertThat(p1.startsWith(p2)).as("p1 must not be a descendant of p2").isFalse();
        assertThat(p2.startsWith(p1)).as("p2 must not be a descendant of p1").isFalse();
    }

    /**
     * Requirements 8.1, 8.3 — for any valid User_Id the resolved path is a strict
     * descendant of the base (equal to {@code namespaceDirSharded(base, userId)})
     * and never equals, nor escapes outside of, the base.
     */
    @Property
    void resolvedPathIsStrictDescendantOfBase(@ForAll("validUserIds") String userId) {
        Path resolved = StorageLayout.namespaceDirSharded(base, userId).toAbsolutePath().normalize();

        assertThat(resolved.startsWith(base))
                .as("resolved path must stay within the base (no .. escape)")
                .isTrue();
        assertThat(resolved).isNotEqualTo(base);
        // Terminal segment is the userId itself; shard prefix keeps it a descendant.
        assertThat(resolved.getFileName().toString()).isEqualTo(userId);
    }

    /**
     * Requirement 8.4 — any identifier containing {@code /}, {@code \}, {@code .},
     * a null byte, or a C0 control character is rejected with
     * {@link IllegalArgumentException} before resolution, resolves no path, and
     * performs no filesystem mutation.
     */
    @Property
    void forbiddenIdentifiersAreRejectedWithoutMutation(@ForAll("forbiddenUserIds") String badId)
            throws IOException {
        List<Path> before = snapshot(base);

        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(base, badId))
                .isInstanceOf(IllegalArgumentException.class);

        List<Path> after = snapshot(base);
        assertThat(after).as("no filesystem mutation may occur during rejection").isEqualTo(before);
    }

    private static List<Path> snapshot(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.sorted().toList();
        }
    }
}
