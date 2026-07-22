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
import java.util.regex.Pattern;

/**
 * Centralized storage layout constants for the Spector Memory system.
 *
 * <h3>Design</h3>
 * <p>Every file name, directory name, and extension used by the memory
 * persistence layer is defined here. No file or directory name is hardcoded
 * anywhere else in the codebase. The user only needs to configure a single
 * {@code persistence-path} — this class resolves everything beneath it.</p>
 *
 * <h3>Directory Structure (V3)</h3>
 * <pre>
 * persistence-path/
 * ├── manifest.json
 * ├── runtime/
 * │   ├── working.mem
 * │   ├── coactivation.tracker
 * │   ├── index.midx
 * │   ├── hebbian.graph
 * │   ├── temporal.chain
 * │   ├── entity.graph
 * │   ├── entity-types.treg
 * │   ├── relation-types.treg
 * │   └── bm25.bidx
 * ├── wal/
 * │   └── wal-000001.bin
 * ├── partitions/
 * │   ├── 000_1717430400/
 * │   │   ├── semantic.mem
 * │   │   ├── episodic.mem
 * │   │   ├── procedural.mem
 * │   │   └── text.dat
 * │   └── 001_1719849600/
 * │       └── ...
 * </pre>
 *
 * <h3>With Namespaces</h3>
 * <pre>
 * persistence-path/
 * ├── spector.lock
 * ├── server.json
 * └── namespaces/
 *     └── agent-alpha/
 *         ├── namespace.json
 *         ├── runtime/
 *         ├── partitions/
 * </pre>
 *
 * <h3>Snapshots</h3>
 * <pre>
 * persistence-path/
 * └── snapshots/
 *     └── {namespace-id}/
 *         └── {snapshot-id}/
 *             ├── snapshot.json
 *             ├── runtime/
 *             ├── partitions/
 * </pre>
 *
 * @see DefaultSpectorMemory.Builder#persistence(Path)
 */
public final class StorageLayout {

    private StorageLayout() {}

    // ═══════════════════════════════════════════════════════════════
    // Top-Level Directories
    // ═══════════════════════════════════════════════════════════════

    /** Directory for runtime (non-partitioned) state — V3 name for global structures. */
    public static final String DIR_RUNTIME = "runtime";

    /** @deprecated Use {@link #DIR_RUNTIME}. Kept for V2 migration detection. */
    @Deprecated(forRemoval = true)
    public static final String DIR_GLOBAL = "global";

    /** Directory containing colocated partition subdirectories. */
    public static final String DIR_PARTITIONS = "partitions";

    /** @deprecated V3 layout eliminates cross-partition directory. Kept for migration. */
    @Deprecated(forRemoval = true)
    public static final String DIR_CROSS = "cross";

    /** Directory for WAL segments (top-level in V3, was inside global/ in V2). */
    public static final String DIR_WAL = "wal";

    /** Directory for namespace directories (multi-tenant mode). */
    public static final String DIR_NAMESPACES = "namespaces";

    // ═══════════════════════════════════════════════════════════════
    // Top-Level Files
    // ═══════════════════════════════════════════════════════════════

    /** Global manifest with version, dimensions, partition config. */
    public static final String FILE_MANIFEST = "manifest.json";

    /** Process lock file (multi-tenant mode). */
    public static final String FILE_LOCK = "spector.lock";

    /** Server-level configuration (multi-tenant mode). */
    public static final String FILE_SERVER_CONFIG = "server.json";

    // ═══════════════════════════════════════════════════════════════
    // Global Files (inside DIR_GLOBAL)
    // ═══════════════════════════════════════════════════════════════

    /** Working memory tier store (volatile, TTL-based). */
    public static final String FILE_WORKING = "working.mem";

    /** Global co-activation frequency tracker. */
    public static final String FILE_COACTIVATION = "coactivation.tracker";

    /** Checkpoint metadata — WAL high-water mark for crash recovery. */
    public static final String FILE_CHECKPOINT_META = "checkpoint.meta";

    // ═══════════════════════════════════════════════════════════════
    // Partition Files (inside each partition directory)
    // ═══════════════════════════════════════════════════════════════

    /** Semantic tier — cognitive headers + quantized vectors. */
    public static final String FILE_SEMANTIC = "semantic.mem";

    /** Episodic tier — episodic headers + vectors. */
    public static final String FILE_EPISODIC = "episodic.mem";

    /** Procedural tier — procedural skills + patterns. */
    public static final String FILE_PROCEDURAL = "procedural.mem";

    /** Raw text content for all tiers in this partition. */
    public static final String FILE_TEXT = "text.dat";

    /** Global memory index (id → location, source, tags). Stored in runtime/ (V3). */
    public static final String FILE_INDEX = "index.midx";

    /** Global Hebbian co-activation edges. Stored in runtime/ (V3). */
    public static final String FILE_HEBBIAN = "hebbian.graph";

    /** Global temporal sequence links. Stored in runtime/ (V3). */
    public static final String FILE_TEMPORAL = "temporal.chain";

    /** Global entity knowledge graph. Stored in runtime/ (V3). */
    public static final String FILE_ENTITY = "entity.graph";

    /** HyperEntityGraph binary file (hyperedge storage). Stored in runtime/ (V3). */
    public static final String FILE_HYPERGRAPH = "hypergraph.hyeg";

    /** BM25 inverted index binary file. Stored in runtime/ (V3). */
    public static final String FILE_BM25 = "bm25.bidx";

    /** Entity type registry (String ↔ int mapping for entity types). Stored in runtime/ (V3). */
    public static final String FILE_ENTITY_TYPES = "entity-types.treg";

    /** Relation type registry (String ↔ int mapping for relation types). Stored in runtime/ (V3). */
    public static final String FILE_RELATION_TYPES = "relation-types.treg";

    // ═══════════════════════════════════════════════════════════════
    // Cross-Partition Files — DEPRECATED (V3 eliminates cross/)
    // ═══════════════════════════════════════════════════════════════

    /** @deprecated V3 layout eliminates cross-partition graphs. */
    @Deprecated(forRemoval = true)
    public static final String FILE_HEBBIAN_CROSS = "hebbian-cross.graph";

    /** @deprecated V3 layout eliminates cross-partition graphs. */
    @Deprecated(forRemoval = true)
    public static final String FILE_ENTITY_CROSS = "entity-cross.graph";

    // ═══════════════════════════════════════════════════════════════
    // Namespace Files (inside each namespace directory)
    // ═══════════════════════════════════════════════════════════════

    /** Namespace metadata, permissions, and quotas. */
    public static final String FILE_NAMESPACE = "namespace.json";

    // ═══════════════════════════════════════════════════════════════
    // Snapshot Directory & Files
    // ═══════════════════════════════════════════════════════════════

    /** Top-level directory for all snapshots. */
    public static final String DIR_SNAPSHOTS = "snapshots";

    /** Snapshot metadata file (inside each snapshot directory). */
    public static final String FILE_SNAPSHOT = "snapshot.json";

    // ═══════════════════════════════════════════════════════════════
    // WAL File Pattern
    // ═══════════════════════════════════════════════════════════════

    /** WAL segment file prefix. */
    public static final String WAL_PREFIX = "wal-";

    /** WAL segment file extension. */
    public static final String WAL_SUFFIX = ".bin";

    /** WAL segment format string: {@code String.format(WAL_FORMAT, seqNo)}. */
    public static final String WAL_FORMAT = WAL_PREFIX + "%06d" + WAL_SUFFIX;

    // ═══════════════════════════════════════════════════════════════
    // Partition Directory Naming
    // ═══════════════════════════════════════════════════════════════

    /** Separator between sequence number and epoch in partition dir names. */
    public static final char PARTITION_SEPARATOR = '_';

    /** Format string for partition directory names: {@code 000_1717430400}. */
    public static final String PARTITION_DIR_FORMAT = "%03d" + PARTITION_SEPARATOR + "%d";

    /** Number of digits in the sequence-number prefix (for parsing). */
    public static final int PARTITION_SEQ_DIGITS = 3;

    /**
     * Compiled regex for partition directory names.
     * Group 1: sequence number (digits), Group 2: epoch seconds (digits).
     */
    public static final Pattern PARTITION_DIR_PATTERN =
            Pattern.compile("(\\d{" + PARTITION_SEQ_DIGITS + "})_" + "(\\d+)");



    // ═══════════════════════════════════════════════════════════════
    // Binary Format Magic Numbers
    // ═══════════════════════════════════════════════════════════════

    /** Magic bytes for text.dat files: "TXTD" (0x54585444). */
    public static final int TEXT_DAT_MAGIC = 0x54585444;

    /** Current version of the text.dat format (V2: mmap-backed off-heap reads). */
    public static final int TEXT_DAT_VERSION = 2;

    /** Magic bytes for index.midx files: "MIDX" (0x4D494458). */
    public static final int INDEX_MIDX_MAGIC = 0x4D494458;

    // ═══════════════════════════════════════════════════════════════
    // Path Resolvers — single point of path construction
    // ═══════════════════════════════════════════════════════════════

    /** Resolves the runtime directory (V3) from the base persistence path. */
    public static Path runtimeDir(Path basePath) {
        return basePath.resolve(DIR_RUNTIME);
    }

    /**
     * Resolves the global directory from the base persistence path.
     * @deprecated Use {@link #runtimeDir(Path)}. Kept for V2 migration detection.
     */
    @Deprecated(forRemoval = true)
    public static Path globalDir(Path basePath) {
        return basePath.resolve(DIR_GLOBAL);
    }

    /** Resolves the partitions directory from the base persistence path. */
    public static Path partitionsDir(Path basePath) {
        return basePath.resolve(DIR_PARTITIONS);
    }

    /**
     * Resolves the cross-partition directory from the base persistence path.
     * @deprecated V3 layout eliminates cross-partition directory.
     */
    @Deprecated(forRemoval = true)
    public static Path crossDir(Path basePath) {
        return basePath.resolve(DIR_CROSS);
    }

    /** Resolves the WAL directory — top-level in V3, was inside global/ in V2. */
    public static Path walDir(Path basePath) {
        return basePath.resolve(DIR_WAL);
    }

    /** @deprecated V2 WAL path (inside global/). Use {@link #walDir(Path)} for V3. */
    @Deprecated(forRemoval = true)
    public static Path walDirV2(Path basePath) {
        return globalDir(basePath).resolve(DIR_WAL);
    }

    /** Resolves the namespaces directory from the base persistence path. */
    public static Path namespacesDir(Path basePath) {
        return basePath.resolve(DIR_NAMESPACES);
    }

    /** Resolves a specific namespace directory (flat layout). */
    public static Path namespaceDir(Path basePath, String namespaceId) {
        return namespacesDir(basePath).resolve(namespaceId);
    }

    // ── Sharded Namespace Resolvers ──

    /** Number of hex characters per shard level (2 = 256 buckets per level). */
    public static final int SHARD_HEX_DIGITS = 2;

    /** Number of shard directory levels (2 levels × 256 = 65,536 buckets). */
    public static final int SHARD_LEVELS = 2;

    /**
     * Maximum permitted length of a namespace identifier, in characters.
     * Identifiers longer than this are rejected before path resolution.
     */
    public static final int MAX_NAMESPACE_ID_LENGTH = 256;

    /**
     * Validates a namespace identifier before it is used to resolve any path.
     *
     * <p>This guard is a pure, side-effect-free check: it never touches the
     * filesystem and never resolves a path. It rejects identifiers that could
     * escape the sharded namespace root or produce a malformed directory name:</p>
     * <ul>
     *   <li>{@code null}, empty, or whitespace-only identifiers;</li>
     *   <li>identifiers longer than {@link #MAX_NAMESPACE_ID_LENGTH} characters;</li>
     *   <li>identifiers containing a path separator ({@code '/'} or {@code '\'}),
     *       a dot ({@code '.'}), a null byte, or any C0 control character in the
     *       range U+0000 through U+001F.</li>
     * </ul>
     *
     * @param namespaceId the namespace (tenant or user) identifier to validate
     * @throws IllegalArgumentException if the identifier is invalid; no path is
     *                                  resolved and no filesystem mutation occurs
     */
    static void validateNamespaceId(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid namespace identifier: must not be null, empty, or whitespace-only");
        }
        if (namespaceId.length() > MAX_NAMESPACE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid namespace identifier: length " + namespaceId.length()
                            + " exceeds maximum of " + MAX_NAMESPACE_ID_LENGTH + " characters");
        }
        for (int i = 0; i < namespaceId.length(); i++) {
            char c = namespaceId.charAt(i);
            if (c == '/' || c == '\\' || c == '.' || c <= '\u001F') {
                throw new IllegalArgumentException(
                        "Invalid namespace identifier: illegal character at index " + i
                                + " (code point U+" + String.format("%04X", (int) c) + ")");
            }
        }
    }

    /**
     * Resolves the sharded path for a namespace ID.
     *
     * <p>Uses the first 4 hex characters of SHA-256(namespaceId) as
     * two directory levels: {@code namespaces/a3/f7/agent-alpha/}</p>
     *
     * <p>The {@code namespaceId} is validated via {@link #validateNamespaceId(String)}
     * before any resolution. Invalid identifiers raise {@link IllegalArgumentException}
     * and no path is resolved. This method is a pure function of its arguments and
     * performs no filesystem mutation.</p>
     *
     * @param basePath     root persistence path
     * @param namespaceId  the namespace (tenant or user) identifier
     * @return sharded path: basePath/namespaces/XX/YY/namespaceId/
     * @throws IllegalArgumentException if {@code namespaceId} is invalid
     */
    public static Path namespaceDirSharded(Path basePath, String namespaceId) {
        validateNamespaceId(namespaceId);
        String hash = sha256Hex(namespaceId);
        String l1 = hash.substring(0, SHARD_HEX_DIGITS);
        String l2 = hash.substring(SHARD_HEX_DIGITS, SHARD_HEX_DIGITS * SHARD_LEVELS);
        return namespacesDir(basePath).resolve(l1).resolve(l2).resolve(namespaceId);
    }

    /**
     * Resolves a tenant-scoped namespace with sharding.
     *
     * <p>Shards on the tenantId, then nests the namespaceId beneath it:</p>
     * <pre>
     *   basePath/namespaces/XX/YY/tenantId/namespaceId/
     * </pre>
     *
     * @param basePath     root persistence path
     * @param tenantId     the tenant (org) identifier — sharded on this
     * @param namespaceId  the namespace (user/agent) identifier within the tenant
     * @return sharded tenant-scoped path
     */
    public static Path tenantNamespaceDirSharded(Path basePath, String tenantId, String namespaceId) {
        String hash = sha256Hex(tenantId);
        String l1 = hash.substring(0, SHARD_HEX_DIGITS);
        String l2 = hash.substring(SHARD_HEX_DIGITS, SHARD_HEX_DIGITS * SHARD_LEVELS);
        return namespacesDir(basePath).resolve(l1).resolve(l2).resolve(tenantId).resolve(namespaceId);
    }

    /**
     * Computes the hex-encoded SHA-256 hash of the input string.
     *
     * @param input the string to hash
     * @return lowercase hex string of the SHA-256 digest
     */
    static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }


    // ── Snapshot resolvers ──

    /** Resolves the top-level snapshots directory. */
    public static Path snapshotsDir(Path basePath) {
        return basePath.resolve(DIR_SNAPSHOTS);
    }

    /**
     * Resolves a specific snapshot directory.
     *
     * @param basePath    the persistence root
     * @param namespaceId the namespace identifier
     * @param snapshotId  the snapshot identifier (e.g., timestamp-based)
     * @return path to the snapshot directory
     */
    public static Path snapshotDir(Path basePath, String namespaceId, String snapshotId) {
        return snapshotsDir(basePath).resolve(namespaceId).resolve(snapshotId);
    }

    // ── Runtime file resolvers (V3 — global structures in runtime/) ──

    /** Resolves the manifest file path. */
    public static Path manifest(Path basePath) {
        return basePath.resolve(FILE_MANIFEST);
    }

    /** Resolves the working memory file path (in runtime/). */
    public static Path workingMem(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_WORKING);
    }

    /** Resolves the co-activation tracker file path (in runtime/). */
    public static Path coactivationTracker(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_COACTIVATION);
    }

    /** Resolves the checkpoint metadata file path (in runtime/). */
    public static Path checkpointMeta(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_CHECKPOINT_META);
    }

    // ── Global structure resolvers (V3 — in runtime/, not partition/) ──

    /** Resolves the index.midx file path (in runtime/). */
    public static Path indexMidxRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_INDEX);
    }

    /** Resolves the hebbian.graph file path (in runtime/). */
    public static Path hebbianGraphRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_HEBBIAN);
    }

    /** Resolves the temporal.chain file path (in runtime/). */
    public static Path temporalChainRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_TEMPORAL);
    }

    /** Resolves the entity.graph file path (in runtime/). */
    public static Path entityGraphRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_ENTITY);
    }

    /** Resolves the hypergraph.hyeg file path (in runtime/). */
    public static Path hyperEntityGraphRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_HYPERGRAPH);
    }

    /** Resolves the entity-types.treg file path (in runtime/). */
    public static Path entityTypesRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_ENTITY_TYPES);
    }

    /** Resolves the relation-types.treg file path (in runtime/). */
    public static Path relationTypesRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_RELATION_TYPES);
    }

    /** Resolves the bm25.bidx file path (in runtime/). */
    public static Path bm25BidxRuntime(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_BM25);
    }

    // ── Partition resolvers ──

    /**
     * Generates a partition directory name from sequence number and creation time.
     *
     * @param seqNo     zero-based partition sequence number
     * @param epochSecs creation time as Unix epoch seconds
     * @return directory name in the format {@code 000_1717430400}
     */
    public static String partitionDirName(int seqNo, long epochSecs) {
        return String.format(PARTITION_DIR_FORMAT, seqNo, epochSecs);
    }

    /**
     * Resolves a partition directory from the base persistence path.
     *
     * @param basePath  the persistence root
     * @param seqNo     zero-based partition sequence number
     * @param epochSecs creation time as Unix epoch seconds
     * @return path to the partition directory
     */
    public static Path partitionDir(Path basePath, int seqNo, long epochSecs) {
        return partitionsDir(basePath).resolve(partitionDirName(seqNo, epochSecs));
    }

    /**
     * Extracts the sequence number from a partition directory name.
     *
     * @param dirName directory name (e.g., {@code "003_1717603200"})
     * @return the sequence number (e.g., 3)
     * @throws NumberFormatException if the name doesn't match the expected format
     */
    public static int parsePartitionSeqNo(String dirName) {
        return Integer.parseInt(dirName.substring(0, PARTITION_SEQ_DIGITS));
    }

    /**
     * Extracts the creation epoch (seconds) from a partition directory name.
     *
     * @param dirName directory name (e.g., {@code "003_1717603200"})
     * @return the Unix epoch seconds (e.g., 1717603200)
     * @throws NumberFormatException if the name doesn't match the expected format
     */
    public static long parsePartitionEpoch(String dirName) {
        return Long.parseLong(dirName.substring(PARTITION_SEQ_DIGITS + 1));
    }

    /**
     * Checks if a directory name matches the partition naming convention.
     *
     * @param dirName directory name to check
     * @return true if it matches {@code NNN_EPOCH} format
     */
    public static boolean isPartitionDir(String dirName) {
        if (dirName == null || dirName.length() <= PARTITION_SEQ_DIGITS + 1) return false;
        if (dirName.charAt(PARTITION_SEQ_DIGITS) != PARTITION_SEPARATOR) return false;
        try {
            parsePartitionSeqNo(dirName);
            parsePartitionEpoch(dirName);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Partition file resolvers (V3: only .mem + text.dat remain per-partition) ──

    /** Resolves a file within a partition directory. */
    public static Path partitionFile(Path partitionDir, String fileName) {
        return partitionDir.resolve(fileName);
    }

    /** Resolves the semantic.mem file within a partition. */
    public static Path semanticMem(Path partitionDir) {
        return partitionDir.resolve(FILE_SEMANTIC);
    }

    /** Resolves the episodic.mem file within a partition. */
    public static Path episodicMem(Path partitionDir) {
        return partitionDir.resolve(FILE_EPISODIC);
    }

    /** Resolves the procedural.mem file within a partition. */
    public static Path proceduralMem(Path partitionDir) {
        return partitionDir.resolve(FILE_PROCEDURAL);
    }

    /** Resolves the text.dat file within a partition. */
    public static Path textDat(Path partitionDir) {
        return partitionDir.resolve(FILE_TEXT);
    }

    /**
     * Resolves the index.midx file within a partition.
     * @deprecated V3: use {@link #indexMidxRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path indexMidx(Path partitionDir) {
        return partitionDir.resolve(FILE_INDEX);
    }

    /**
     * Resolves the bm25.bidx file within a partition.
     * @deprecated V3: use {@link #bm25BidxRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path bm25Bidx(Path partitionDir) {
        return partitionDir.resolve(FILE_BM25);
    }

    /**
     * Resolves the hebbian.graph file within a partition.
     * @deprecated V3: use {@link #hebbianGraphRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path hebbianGraph(Path partitionDir) {
        return partitionDir.resolve(FILE_HEBBIAN);
    }

    /**
     * Resolves the temporal.chain file within a partition.
     * @deprecated V3: use {@link #temporalChainRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path temporalChain(Path partitionDir) {
        return partitionDir.resolve(FILE_TEMPORAL);
    }

    /**
     * Resolves the entity.graph file within a partition.
     * @deprecated V3: use {@link #entityGraphRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path entityGraph(Path partitionDir) {
        return partitionDir.resolve(FILE_ENTITY);
    }

    /**
     * Resolves the entity-types.treg file within a partition.
     * @deprecated V3: use {@link #entityTypesRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path entityTypes(Path partitionDir) {
        return partitionDir.resolve(FILE_ENTITY_TYPES);
    }

    /**
     * Resolves the relation-types.treg file within a partition.
     * @deprecated V3: use {@link #relationTypesRuntime(Path)} with basePath instead.
     */
    @Deprecated(forRemoval = true)
    public static Path relationTypes(Path partitionDir) {
        return partitionDir.resolve(FILE_RELATION_TYPES);
    }

    // ── Cross-partition file resolvers — DEPRECATED ──

    /**
     * Resolves the cross-partition Hebbian graph.
     * @deprecated V3 layout eliminates cross-partition graphs.
     */
    @Deprecated(forRemoval = true)
    public static Path hebbianCrossGraph(Path basePath) {
        return crossDir(basePath).resolve(FILE_HEBBIAN_CROSS);
    }

    /**
     * Resolves the cross-partition entity graph.
     * @deprecated V3 layout eliminates cross-partition graphs.
     */
    @Deprecated(forRemoval = true)
    public static Path entityCrossGraph(Path basePath) {
        return crossDir(basePath).resolve(FILE_ENTITY_CROSS);
    }

    // ── WAL resolvers ──

    /** Generates a WAL segment file name. */
    public static String walFileName(int seqNo) {
        return String.format(WAL_FORMAT, seqNo);
    }

    /** Resolves a WAL segment file path. */
    public static Path walFile(Path basePath, int seqNo) {
        return walDir(basePath).resolve(walFileName(seqNo));
    }
}
