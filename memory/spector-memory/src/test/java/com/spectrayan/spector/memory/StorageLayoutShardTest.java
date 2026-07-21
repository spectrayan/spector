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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StorageLayout} sharded namespace path resolution.
 *
 * <p>Validates determinism, structure, and distribution evenness of the
 * hash-based 2-level directory sharding for namespace IDs.</p>
 */
class StorageLayoutShardTest {

    @TempDir
    Path tempDir;

    @Test
    void shardedPathIsDeterministic() {
        Path p1 = StorageLayout.namespaceDirSharded(tempDir, "tenant-alpha");
        Path p2 = StorageLayout.namespaceDirSharded(tempDir, "tenant-alpha");
        assertThat(p1).isEqualTo(p2);
    }

    @Test
    void shardedPathContainsNamespaceId() {
        Path sharded = StorageLayout.namespaceDirSharded(tempDir, "tenant-alpha");
        assertThat(sharded.getFileName().toString()).isEqualTo("tenant-alpha");
    }

    @Test
    void shardedPathHasTwoLevelPrefix() {
        Path sharded = StorageLayout.namespaceDirSharded(tempDir, "tenant-alpha");
        Path namespacesDir = StorageLayout.namespacesDir(tempDir);

        Path relative = namespacesDir.relativize(sharded);
        // Should be XX/YY/tenant-alpha (3 components)
        assertThat(relative.getNameCount()).isEqualTo(3);
        // Each shard level should be exactly 2 hex characters
        assertThat(relative.getName(0).toString()).matches("[0-9a-f]{2}");
        assertThat(relative.getName(1).toString()).matches("[0-9a-f]{2}");
    }

    @Test
    void differentNamespacesGetDifferentPaths() {
        Path p1 = StorageLayout.namespaceDirSharded(tempDir, "tenant-alpha");
        Path p2 = StorageLayout.namespaceDirSharded(tempDir, "tenant-beta");
        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    void tenantScopedShardedPathStructure() {
        Path sharded = StorageLayout.tenantNamespaceDirSharded(tempDir, "org-acme", "user-1");
        Path namespacesDir = StorageLayout.namespacesDir(tempDir);

        Path relative = namespacesDir.relativize(sharded);
        // Should be XX/YY/org-acme/user-1 (4 components)
        assertThat(relative.getNameCount()).isEqualTo(4);
        assertThat(relative.getName(0).toString()).matches("[0-9a-f]{2}");
        assertThat(relative.getName(1).toString()).matches("[0-9a-f]{2}");
        assertThat(relative.getName(2).toString()).isEqualTo("org-acme");
        assertThat(relative.getName(3).toString()).isEqualTo("user-1");
    }

    @Test
    void tenantScopedShardsOnTenantNotUser() {
        // Same tenant, different users should share the same shard bucket
        Path u1 = StorageLayout.tenantNamespaceDirSharded(tempDir, "org-acme", "user-1");
        Path u2 = StorageLayout.tenantNamespaceDirSharded(tempDir, "org-acme", "user-2");

        // Their parent (the tenant dir) should be the same
        assertThat(u1.getParent()).isEqualTo(u2.getParent());
    }

    @Test
    void shardDistributionIsReasonablyEven() {
        // Generate 10K namespace IDs and check shard distribution
        Map<String, Integer> shardCounts = new HashMap<>();

        for (int i = 0; i < 10_000; i++) {
            String nsId = "tenant-" + i;
            Path sharded = StorageLayout.namespaceDirSharded(tempDir, nsId);
            Path namespacesDir = StorageLayout.namespacesDir(tempDir);
            Path relative = namespacesDir.relativize(sharded);

            // Use the first shard level (256 buckets)
            String l1 = relative.getName(0).toString();
            shardCounts.merge(l1, 1, Integer::sum);
        }

        // With 10K items across 256 L1 buckets, expect ~39 per bucket
        // Allow 5x variance (8-195 range)
        assertThat(shardCounts.size()).as("Should use most of the 256 L1 buckets")
                .isGreaterThan(200);

        int maxCount = shardCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minCount = shardCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);

        assertThat(maxCount).as("Max per bucket should not be extreme")
                .isLessThan(200);
        assertThat(minCount).as("Min per bucket should not be zero for most")
                .isGreaterThan(0);
    }

    @Test
    void sha256HexProducesCorrectLength() {
        String hash = StorageLayout.sha256Hex("test-input");
        assertThat(hash).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void flatAndShardedPathsDiffer() {
        Path flat = StorageLayout.namespaceDir(tempDir, "tenant-1");
        Path sharded = StorageLayout.namespaceDirSharded(tempDir, "tenant-1");

        // Both end with the namespace ID
        assertThat(flat.getFileName().toString()).isEqualTo("tenant-1");
        assertThat(sharded.getFileName().toString()).isEqualTo("tenant-1");

        // But sharded has extra shard levels
        assertThat(sharded.getNameCount()).isGreaterThan(flat.getNameCount());
    }

    // ── Identifier validation (Requirements 8.4, 8.5, 8.6) ──

    @Test
    void rejectsNullIdentifier() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyIdentifier() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWhitespaceOnlyIdentifier() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, "   \t\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdentifierExceeding256Characters() {
        String tooLong = "a".repeat(257);
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsIdentifierAtMaxLength() {
        String atMax = "a".repeat(256);
        assertThatCode(() -> StorageLayout.namespaceDirSharded(tempDir, atMax))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsForwardSlash() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, "a/b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBackslash() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, "a\\b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDot() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, "a.b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, ".."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullByte() {
        assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, "a\u0000b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsC0ControlCharacters() {
        for (char c = '\u0000'; c <= '\u001F'; c++) {
            String id = "a" + c + "b";
            assertThatThrownBy(() -> StorageLayout.namespaceDirSharded(tempDir, id))
                    .as("control char U+%04X must be rejected", (int) c)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsValidTsidLikeIdentifier() {
        // 13-char TSID (Crockford Base32) — the real per-user namespace id
        assertThatCode(() -> StorageLayout.namespaceDirSharded(tempDir, "0GXABCDEFGHJK"))
                .doesNotThrowAnyException();
    }

    // ── Descendant-of-base and sharding well-formedness (Requirements 8.3, 8.7) ──

    @Test
    void resolvedPathIsDescendantOfBase() {
        Path sharded = StorageLayout.namespaceDirSharded(tempDir, "0GXABCDEFGHJK");
        Path normalizedBase = tempDir.toAbsolutePath().normalize();
        Path normalizedResolved = sharded.toAbsolutePath().normalize();

        // Lexical descendant check — resolution is pure and creates no directory on disk.
        assertThat(normalizedResolved.startsWith(normalizedBase)).isTrue();
        assertThat(normalizedResolved).isNotEqualTo(normalizedBase);
    }

    @Test
    void shardSegmentsEqualFirstTwoSha256BytePairs() {
        String id = "0GXABCDEFGHJK";
        String hash = StorageLayout.sha256Hex(id);
        String expectedL1 = hash.substring(0, 2);
        String expectedL2 = hash.substring(2, 4);

        Path sharded = StorageLayout.namespaceDirSharded(tempDir, id);
        Path relative = StorageLayout.namespacesDir(tempDir).relativize(sharded);

        assertThat(relative.getName(0).toString()).isEqualTo(expectedL1).matches("[0-9a-f]{2}");
        assertThat(relative.getName(1).toString()).isEqualTo(expectedL2).matches("[0-9a-f]{2}");
    }
}
