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
package com.spectrayan.spector.kernel.storage;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorInternalException;
import com.spectrayan.spector.commons.error.SpectorValidationException;

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
 * │   ├── entity-directory.edir
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
 * @see com.spectrayan.spector.memory.SpectorMemoryBuilder#persistence(Path)
 */
public final class StoragePaths {

    private StoragePaths() {}

    // ═══════════════════════════════════════════════════════════════
    // Top-Level Directories
    // ═══════════════════════════════════════════════════════════════

    /** Directory for runtime (non-partitioned) state — V3 name for global structures. */
    public static final String DIR_RUNTIME = "runtime";

    /** Directory containing colocated partition subdirectories. */
    public static final String DIR_PARTITIONS = "partitions";

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
    // V4 Bundle Files (ADR-0004 — mmap FD scaling)
    // ═══════════════════════════════════════════════════════════════

    /**
     * V4 partition bundle — consolidates semantic/episodic/procedural/text stores
     * into a single mmap file per partition. Stored inside each partition directory.
     */
    public static final String FILE_PARTITION_BUNDLE = "partition.bundle";

    /**
     * V4 runtime bundle — consolidates all runtime stores (hebbian, temporal, entity,
     * hypergraph, coactivation, index, BM25, checkpoint, etc.) into a single mmap file.
     * Stored inside runtime/.
     */
    public static final String FILE_RUNTIME_BUNDLE = "runtime.bundle";

    /** Returns the partition bundle file path within a partition directory. */
    public static Path partitionBundleFile(Path partitionDir) {
        return partitionDir.resolve(FILE_PARTITION_BUNDLE);
    }

    /** Returns the runtime bundle file path within the runtime directory. */
    public static Path runtimeBundleFile(Path basePath) {
        return runtimeDir(basePath).resolve(FILE_RUNTIME_BUNDLE);
    }

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

    /** Resolves the partitions directory from the base persistence path. */
    public static Path partitionsDir(Path basePath) {
        return basePath.resolve(DIR_PARTITIONS);
    }

    /** Resolves the WAL directory — top-level in V3, was inside global/ in V2. */
    public static Path walDir(Path basePath) {
        return basePath.resolve(DIR_WAL);
    }

    /** Resolves the namespaces directory from the base persistence path. */
    public static Path namespacesDir(Path basePath) {
        return basePath.resolve(DIR_NAMESPACES);
    }

    /** Resolves a specific namespace directory (flat layout). */
    public static Path namespaceDir(Path basePath, String namespaceId) {
        validateNamespaceId(namespaceId);
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
     * @throws SpectorValidationException if the identifier is invalid; no path is
     *                                  resolved and no filesystem mutation occurs
     */
    public static void validateNamespaceId(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "must not be null, empty, or whitespace-only");
        }
        if (namespaceId.length() > MAX_NAMESPACE_ID_LENGTH) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "length " + namespaceId.length()
                            + " exceeds maximum of " + MAX_NAMESPACE_ID_LENGTH + " characters");
        }
        for (int i = 0; i < namespaceId.length(); i++) {
            char c = namespaceId.charAt(i);
            if (c == '/' || c == '\\' || c == '.' || c <= '\u001F') {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "namespace identifier", "illegal character at index " + i
                                + " (code point U+" + String.format("%04X", (int) c) + ")");
            }
        }
    }

    /**
     * Validates a tenant identifier that is about to become a path component.
     *
     * <p>Applies every {@link #validateNamespaceId(String)} rule — so the hardened fail-closed error
     * contract carries over unchanged — and additionally requires lowercase.</p>
     *
     * <p>The lowercase requirement exists because the shard segments are lowercased while the tenant
     * segment keeps its original case. On a case-insensitive filesystem such as macOS APFS, tenants
     * {@code Acme} and {@code acme} would then resolve to the same directory and their data would be
     * mixed. Rejecting mixed case is the cheapest way to make the layout behave identically on every
     * platform (Req R4.3, R4.6).</p>
     *
     * @param tenantId the tenant identifier
     * @throws SpectorValidationException if the identifier is invalid or not lowercase
     */
    public static void validateTenantId(String tenantId) {
        validateNamespaceId(tenantId);
        if (!tenantId.equals(tenantId.toLowerCase(java.util.Locale.ROOT))) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "tenant identifier must be lowercase");
        }
    }

    /**
     * Resolves a two-level SHA-256 shard path under {@code parent}: {@code parent/XX/YY/}.
     *
     * @param parent the parent directory
     * @param id     the identifier to shard
     * @return {@code parent.resolve(l1).resolve(l2)}
     */
    public static Path shard2(Path parent, String id) {
        String hash = sha256Hex(id);
        String l1 = hash.substring(0, SHARD_HEX_DIGITS);
        String l2 = hash.substring(SHARD_HEX_DIGITS, SHARD_HEX_DIGITS * SHARD_LEVELS);
        return parent.resolve(l1).resolve(l2);
    }

    /**
     * Normalises the resolved path and verifies that it does not escape {@code base}
     * (traversal guard ported from IdentityPaths, Req R4.5).
     *
     * @param base     the expected base directory
     * @param resolved the resolved target path
     * @return the normalised target path
     * @throws SpectorValidationException if the resolved path escapes {@code base}
     */
    public static Path safeResolve(Path base, Path resolved) {
        Path normalized = resolved.normalize();
        if (!normalized.startsWith(base.normalize())) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "Path traversal attempt detected");
        }
        return normalized;
    }

    /**
     * Resolves the sharded path for a namespace ID.
     *
     * <p>Uses the first 4 hex characters of SHA-256(namespaceId) as
     * two directory levels: {@code namespaces/a3/f7/agent-alpha/}</p>
     *
     * <p>The {@code namespaceId} is validated via {@link #validateNamespaceId(String)}
     * before any resolution. Invalid identifiers raise {@link SpectorValidationException}
     * and no path is resolved. This method is a pure function of its arguments and
     * performs no filesystem mutation.</p>
     *
     * @param basePath     root persistence path
     * @param namespaceId  the namespace (tenant or user) identifier
     * @return sharded path: basePath/namespaces/XX/YY/namespaceId/
     * @throws SpectorValidationException if {@code namespaceId} is invalid
     */
    public static Path namespaceDirSharded(Path basePath, String namespaceId) {
        validateNamespaceId(namespaceId);
        return safeResolve(basePath, shard2(namespacesDir(basePath), namespaceId).resolve(namespaceId));
    }

    /**
     * Resolves a tenant-rooted namespace path with SHA-256 sharding for both tenant and namespace
     * (ADR-0033 §9.2, Phase 0.1, Req R4.2, R4.3, R4.5).
     *
     * <pre>
     *   basePath/tenants/XX/YY/tenantId/namespaces/ZZ/WW/namespaceId/
     * </pre>
     *
     * @param basePath    root persistence path
     * @param tenantId    the tenant identifier (must be lowercase, validated via {@link #validateNamespaceId(String)})
     * @param namespaceId the namespace identifier (validated via {@link #validateNamespaceId(String)})
     * @return sharded tenant-rooted namespace directory
     * @throws SpectorValidationException if either identifier is invalid or fails traversal checks
     */
    public static Path tenantRootedNamespaceDir(Path basePath, String tenantId, String namespaceId) {
        validateTenantId(tenantId);
        validateNamespaceId(namespaceId);
        Path tenantDir = shard2(basePath.resolve(DIR_TENANTS), tenantId).resolve(tenantId);
        Path nsDir = shard2(tenantDir.resolve(DIR_NAMESPACES), namespaceId).resolve(namespaceId);
        return safeResolve(basePath, nsDir);
    }

    /**
     * Resolves a tenant-scoped namespace with sharding.
     *
     * @deprecated Use {@link #tenantRootedNamespaceDir(Path, String, String)} instead.
     *             This helper placed tenants directly under {@code namespaces/} instead of the tenant-rooted
     *             hierarchy defined by ADR-0033 §9.2 (Req R6.4).
     * @param basePath     root persistence path
     * @param tenantId     the tenant (org) identifier — sharded on this
     * @param namespaceId  the namespace (user/agent) identifier within the tenant
     * @return sharded tenant-scoped path
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    public static Path tenantNamespaceDirSharded(Path basePath, String tenantId, String namespaceId) {
        validateNamespaceId(tenantId);
        validateNamespaceId(namespaceId);
        return safeResolve(basePath, shard2(namespacesDir(basePath), tenantId).resolve(tenantId).resolve(namespaceId));
    }

    // ── Catalog and identity plane resolvers (ADR-0029) ──

    /** Directory name for account catalog entries. */
    public static final String DIR_ACCOUNTS = "accounts";

    /** Directory name for tenant catalog/identity entries. */
    public static final String DIR_TENANTS = "tenants";

    /** Filename for account identity bundles. */
    public static final String FILE_IDENTITY_BUNDLE = "identity.bundle";

    /**
     * Resolves the catalog directory for an account.
     *
     * <p>Sharded by SHA-256 of the accountId, same 2-level scheme
     * as namespace directories:</p>
     * <pre>
     *   basePath/accounts/XX/YY/accountId/
     * </pre>
     *
     * @param basePath  root persistence path
     * @param accountId the account identifier (TSID from JWT {@code sub})
     * @return sharded account catalog directory
     * @throws SpectorValidationException if {@code accountId} is invalid
     */
    public static Path accountDir(Path basePath, String accountId) {
        validateNamespaceId(accountId);
        return safeResolve(basePath, shard2(basePath.resolve(DIR_ACCOUNTS), accountId).resolve(accountId));
    }

    /**
     * @deprecated Use {@code com.spectrayan.spector.synapse.identity.IdentityPaths.accountIdentityBundle} instead.
     *             StoragePaths uses SHA-256 sharding; IdentityPaths uses character-prefix sharding (ADR-0029 §23.2).
     *             This method is retained only for FileAccountCatalog backward compatibility and must not be used
     *             for identity bundle paths.
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    public static Path accountIdentityBundle(Path basePath, String accountId) {
        return accountDir(basePath, accountId).resolve(FILE_IDENTITY_BUNDLE);
    }

    /**
     * @deprecated Use {@code com.spectrayan.spector.synapse.identity.IdentityPaths.tenantIdentityBundle} instead.
     *             StoragePaths uses SHA-256 sharding; IdentityPaths uses character-prefix sharding (ADR-0029 §23.2).
     */
    @Deprecated(since = "0.13.0", forRemoval = true)
    public static Path tenantIdentityBundle(Path basePath, String tenantId) {
        validateNamespaceId(tenantId);
        return safeResolve(basePath, shard2(basePath.resolve(DIR_TENANTS), tenantId).resolve(tenantId)
                .resolve(FILE_IDENTITY_BUNDLE));
    }

    /**
     * Computes the hex-encoded SHA-256 hash of the input string.
     *
     * @param input the string to hash
     * @return lowercase hex string of the SHA-256 digest
     */
    public static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new SpectorInternalException(ErrorCode.INTERNAL_ERROR, e, "SHA-256 not available");
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
        validateNamespaceId(namespaceId);
        validateNamespaceId(snapshotId);
        return snapshotsDir(basePath).resolve(namespaceId).resolve(snapshotId);
    }

    // ── Runtime file resolvers (V3 Legacy — global structures in runtime/) ──

    /** Resolves the manifest file path. */
    public static Path manifest(Path basePath) {
        return basePath.resolve(FILE_MANIFEST);
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
