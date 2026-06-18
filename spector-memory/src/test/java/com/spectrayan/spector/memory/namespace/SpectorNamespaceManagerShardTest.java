/*
 * Copyright 2026 Spectrayan
 */
package com.spectrayan.spector.memory.namespace;

import com.spectrayan.spector.memory.StorageLayout;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for sharded namespace discovery in {@link SpectorNamespaceManager}.
 */
class SpectorNamespaceManagerShardTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ns-shard-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        // Recursive delete
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
    }

    @Test
    @DisplayName("Sharded manager creates namespaces in shard directories")
    void shardedCreateNamespace() {
        var mgr = new SpectorNamespaceManager(tempDir, true);
        var ctx = mgr.createNamespace(NamespaceConfig.unlimited("test-agent"));

        assertThat(ctx.directory()).isNotNull();
        assertThat(Files.exists(ctx.directory())).isTrue();

        // Path should contain shard directories
        Path relative = tempDir.relativize(ctx.directory());
        String[] parts = relative.toString().replace("\\", "/").split("/");
        // namespaces / XX / YY / test-agent
        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("namespaces");
        assertThat(parts[1]).hasSize(StorageLayout.SHARD_HEX_DIGITS);
        assertThat(parts[2]).hasSize(StorageLayout.SHARD_HEX_DIGITS);
        assertThat(parts[3]).isEqualTo("test-agent");
    }

    @Test
    @DisplayName("Sharded manager discovers existing sharded namespaces")
    void shardedDiscovery() throws IOException {
        // Create a sharded namespace manually
        String nsId = "agent-alpha";
        Path shardedDir = StorageLayout.namespaceDirSharded(tempDir, nsId);
        Files.createDirectories(shardedDir);
        Files.writeString(shardedDir.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"" + nsId + "\"}");

        // Create another
        String nsId2 = "agent-beta";
        Path shardedDir2 = StorageLayout.namespaceDirSharded(tempDir, nsId2);
        Files.createDirectories(shardedDir2);
        Files.writeString(shardedDir2.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"" + nsId2 + "\"}");

        // Discover
        var mgr = new SpectorNamespaceManager(tempDir, true);

        assertThat(mgr.count()).isEqualTo(2);
        assertThat(mgr.exists("agent-alpha")).isTrue();
        assertThat(mgr.exists("agent-beta")).isTrue();
    }

    @Test
    @DisplayName("Flat manager does NOT discover sharded namespaces")
    void flatIgnoresShardedDirs() throws IOException {
        String nsId = "agent-alpha";
        Path shardedDir = StorageLayout.namespaceDirSharded(tempDir, nsId);
        Files.createDirectories(shardedDir);
        Files.writeString(shardedDir.resolve(StorageLayout.FILE_NAMESPACE),
                "{\"id\": \"" + nsId + "\"}");

        // Flat mode should not discover this
        var mgr = new SpectorNamespaceManager(tempDir, false);
        assertThat(mgr.count()).isZero();
    }

    @Test
    @DisplayName("getOrCreateNamespace uses sharded paths when enabled")
    void getOrCreateSharded() {
        var mgr = new SpectorNamespaceManager(tempDir, true);
        var ctx = mgr.getOrCreateNamespace("my-agent");

        assertThat(mgr.exists("my-agent")).isTrue();
        assertThat(ctx.directory().toString()).contains(
                StorageLayout.namespaceDirSharded(tempDir, "my-agent").toString());
    }

    @Test
    @DisplayName("Sharded and flat managers produce different paths for same ID")
    void shardedVsFlatPaths() {
        var shardedMgr = new SpectorNamespaceManager(tempDir, true);
        var flatMgr = new SpectorNamespaceManager(tempDir, false);

        var shardedCtx = shardedMgr.createNamespace(NamespaceConfig.unlimited("agent-x"));
        // Clean up for flat creation
        var flatBase = Files.isDirectory(tempDir) ? tempDir : tempDir;

        Path shardedPath = shardedCtx.directory();
        Path flatPath = StorageLayout.namespaceDir(tempDir, "agent-x");

        assertThat(shardedPath).isNotEqualTo(flatPath);
        assertThat(shardedPath.toString().length()).isGreaterThan(flatPath.toString().length());
    }

    @Test
    @DisplayName("isSharded returns correct value")
    void isShardedReturnsCorrectValue() {
        var shardedMgr = new SpectorNamespaceManager(tempDir, true);
        var flatMgr = new SpectorNamespaceManager(tempDir, false);

        assertThat(shardedMgr.isSharded()).isTrue();
        assertThat(flatMgr.isSharded()).isFalse();
    }
}
