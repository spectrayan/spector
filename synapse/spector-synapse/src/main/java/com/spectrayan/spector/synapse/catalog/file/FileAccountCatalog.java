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
package com.spectrayan.spector.synapse.catalog.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed implementation of {@link AccountCatalog} using per-account JSON files
 * (ADR-0029 §4.2, §19).
 *
 * @deprecated Legacy standalone mode only. In enterprise deployments, use {@link com.spectrayan.spector.synapse.catalog.jdbc.JdbcAccountCatalog}.
 */
@Deprecated(since = "0.1.0-alpha")
public class FileAccountCatalog implements AccountCatalog {

    private static final Logger log = LoggerFactory.getLogger(FileAccountCatalog.class);

    private static final String FILE_ACCOUNT = "account.json";
    private static final String FILE_SLUGS = "slugs.json";
    private static final String FILE_NAMESPACES = "namespaces.json";
    private static final String FILE_GRANTS = "grants.jsonl";
    private static final String FILE_LOCK = "LOCK";

    private static final java.util.regex.Pattern SLUG_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

    private final Path basePath;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CatalogSnapshot> snapshotCache = new ConcurrentHashMap<>();

    public FileAccountCatalog(Path basePath, ObjectMapper objectMapper) {
        this.basePath = basePath;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════
    // Read operations (lock-free on cached snapshot)
    // ══════════════════════════════════════════════════════════════

    private CatalogSnapshot loadSnapshot(String accountId) {
        Path accountDir = StorageLayout.accountDir(basePath, accountId);
        Path accountFile = accountDir.resolve(FILE_ACCOUNT);

        if (!Files.exists(accountFile)) {
            throw new NamespaceNotFoundException("account:" + accountId);
        }

        try {
            long mtime = Files.getLastModifiedTime(accountFile).toMillis();
            CatalogSnapshot cached = snapshotCache.get(accountId);
            if (cached != null && cached.mtimeNanos() == mtime) {
                log.debug("[FileAccountCatalog] snapshot cache hit for account {}", accountId);
                return cached;
            }

            Account account = objectMapper.readValue(accountFile.toFile(), Account.class);

            Path slugsFile = accountDir.resolve(FILE_SLUGS);
            Map<String, String> slugs = new HashMap<>();
            if (Files.exists(slugsFile)) {
                slugs = objectMapper.readValue(slugsFile.toFile(),
                        new TypeReference<Map<String, String>>() {});
            }

            Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
            Map<String, NamespaceRecord> namespaces = new HashMap<>();
            if (Files.exists(namespacesFile)) {
                namespaces = objectMapper.readValue(namespacesFile.toFile(),
                        new TypeReference<Map<String, NamespaceRecord>>() {});
            }

            Path grantsFile = accountDir.resolve(FILE_GRANTS);
            List<Grant> grants = new ArrayList<>();
            if (Files.exists(grantsFile)) {
                grants = GrantLog.parseGrants(grantsFile, objectMapper);
            }

            CatalogSnapshot snapshot = new CatalogSnapshot(
                    account,
                    Collections.unmodifiableMap(slugs),
                    Collections.unmodifiableMap(namespaces),
                    Collections.unmodifiableList(grants),
                    mtime
            );
            snapshotCache.put(accountId, snapshot);
            return snapshot;
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to load snapshot for account {}", accountId, e);
            throw new RuntimeException("Failed to read account catalog snapshot", e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Write operations (JVM + file lock guarded)
    // ══════════════════════════════════════════════════════════════

    @Override
    public Account getOrCreateAccount(String accountId) {
        return getOrCreateAccount(accountId, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN);
    }

    @Override
    public Account getOrCreateAccount(String accountId, AccountProfile profile, PrincipalKind kind) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be null or blank");
        }

        AccountProfile effProfile = profile != null ? profile : AccountProfile.HUMAN_SOLO;
        PrincipalKind effKind = kind != null ? kind : PrincipalKind.HUMAN;

        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path accountFile = accountDir.resolve(FILE_ACCOUNT);

            if (Files.exists(accountFile)) {
                return objectMapper.readValue(accountFile.toFile(), Account.class);
            }

            Files.createDirectories(accountDir);
            Path lockFile = accountDir.resolve(FILE_LOCK);
            if (!Files.exists(lockFile)) {
                Files.createFile(lockFile);
            }

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                // Create account with requested profile defaults
                Account account = new Account(
                        accountId,
                        effKind,
                        effProfile,
                        null,  // displayName
                        AccountQuotas.forProfile(effProfile),
                        AccountFlags.forProfile(effProfile),
                        accountId,  // defaultNamespaceId == accountId (invariant §12)
                        Instant.now()
                );
                atomicWrite(accountFile, account);

                // Write slug map: default → accountId
                Map<String, String> slugs = Map.of("default", accountId);
                atomicWrite(accountDir.resolve(FILE_SLUGS), slugs);

                // Write initial default namespace record
                Instant now = Instant.now();
                NamespaceRecord defaultNamespace = new NamespaceRecord(
                        accountId, "default", accountId, NamespaceType.DEFAULT,
                        NamespaceStatus.ACTIVE, null, null, null, now, now
                );
                Map<String, NamespaceRecord> namespaces = Map.of(accountId, defaultNamespace);
                atomicWrite(accountDir.resolve(FILE_NAMESPACES), namespaces);

                // Write implicit OWNER grant
                Grant implicitOwner = new Grant(
                        accountId + "-owner-default",
                        GrantObjectType.NAMESPACE,
                        accountId,           // objectId = namespaceId
                        accountId,           // principalId
                        PrincipalType.ACCOUNT,
                        GrantRole.OWNER,
                        Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN),
                        accountId,           // grantedBy
                        now,
                        null,                // expiresAt
                        null                 // constraints
                );
                GrantLog.appendGrant(accountDir.resolve(FILE_GRANTS), implicitOwner, objectMapper);

                log.info("[FileAccountCatalog] created account: {}", accountId);
                return account;
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to create account {}", accountId, e);
            throw new RuntimeException("Failed to create account", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public Account getAccount(String accountId) {
        return getOrCreateAccount(accountId);
    }

    @Override
    public Optional<NamespaceRecord> resolve(String accountId, String slugOrId) {
        CatalogSnapshot snapshot;
        try {
            snapshot = loadSnapshot(accountId);
        } catch (NamespaceNotFoundException e) {
            return Optional.empty();
        }
        return snapshot.resolveNamespace(slugOrId);
    }

    @Override
    public Optional<Grant> authorize(String accountId, String namespaceId, GrantRole minimum) {
        CatalogSnapshot snapshot;
        try {
            snapshot = loadSnapshot(accountId);
        } catch (NamespaceNotFoundException e) {
            return Optional.empty();
        }
        return snapshot.findGrant(accountId, namespaceId, minimum);
    }

    @Override
    public NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type,
            String displayName, String description, NamespaceBias bias) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Invalid namespace slug: '" + slug + "'. Must be 1-63 alphanumeric/underscore/hyphen characters matching " + SLUG_PATTERN.pattern());
        }

        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Path slugsFile = accountDir.resolve(FILE_SLUGS);
                Map<String, String> slugs = new HashMap<>();
                if (Files.exists(slugsFile)) {
                    slugs = new HashMap<>(objectMapper.readValue(slugsFile.toFile(),
                            new TypeReference<Map<String, String>>() {}));
                }

                if (slugs.containsKey(slug)) {
                    throw new IllegalArgumentException("Slug already exists: " + slug);
                }

                Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
                Map<String, NamespaceRecord> namespaces = new HashMap<>();
                if (Files.exists(namespacesFile)) {
                    namespaces = new HashMap<>(objectMapper.readValue(namespacesFile.toFile(),
                            new TypeReference<Map<String, NamespaceRecord>>() {}));
                }

                // Allocate new namespaceId (UUID-based TSID-like 13-char id)
                String newNamespaceId = UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 13);

                slugs.put(slug, newNamespaceId);
                atomicWrite(slugsFile, slugs);

                Instant now = Instant.now();
                NamespaceRecord newRecord = new NamespaceRecord(
                        newNamespaceId,
                        slug,
                        accountId,
                        type != null ? type : NamespaceType.PROJECT,
                        NamespaceStatus.ACTIVE,
                        displayName,
                        description,
                        bias,
                        now,
                        now
                );
                namespaces.put(newNamespaceId, newRecord);
                atomicWrite(namespacesFile, namespaces);

                // Create data-plane directory
                Path namespaceDir = StorageLayout.namespaceDirSharded(basePath, newNamespaceId);
                Files.createDirectories(namespaceDir);

                // Write implicit OWNER grant for new namespace
                Grant implicitOwner = new Grant(
                        newNamespaceId + "-owner",
                        GrantObjectType.NAMESPACE,
                        newNamespaceId,
                        accountId,
                        PrincipalType.ACCOUNT,
                        GrantRole.OWNER,
                        Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN),
                        accountId,
                        now,
                        null,
                        null
                );
                GrantLog.appendGrant(accountDir.resolve(FILE_GRANTS), implicitOwner, objectMapper);

                // Invalidate snapshot cache
                snapshotCache.remove(accountId);

                log.info("[FileAccountCatalog] created namespace: {} (slug={}) for account {}",
                        newNamespaceId, slug, accountId);
                return newRecord;
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to create namespace {} for account {}",
                    slug, accountId, e);
            throw new RuntimeException("Failed to create namespace", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public NamespaceRecord updateNamespace(String accountId, String slugOrId,
            String displayName, String description, NamespaceType type, NamespaceBias bias) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Path slugsFile = accountDir.resolve(FILE_SLUGS);
                Map<String, String> slugs = new HashMap<>();
                if (Files.exists(slugsFile)) {
                    slugs = objectMapper.readValue(slugsFile.toFile(),
                            new TypeReference<Map<String, String>>() {});
                }

                String namespaceId = slugs.get(slugOrId);
                if (namespaceId == null) {
                    if (slugs.containsValue(slugOrId)) {
                        namespaceId = slugOrId;
                    } else {
                        throw new NamespaceNotFoundException(slugOrId);
                    }
                }

                Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
                Map<String, NamespaceRecord> namespaces = new HashMap<>();
                if (Files.exists(namespacesFile)) {
                    namespaces = new HashMap<>(objectMapper.readValue(namespacesFile.toFile(),
                            new TypeReference<Map<String, NamespaceRecord>>() {}));
                }

                NamespaceRecord existing = namespaces.get(namespaceId);
                String slug = existing != null ? existing.slug() : slugOrId;
                Instant createdAt = existing != null ? existing.createdAt() : Instant.now();
                NamespaceType currentType = type != null ? type : (existing != null ? existing.type() : NamespaceType.PROJECT);

                NamespaceRecord updated = new NamespaceRecord(
                        namespaceId,
                        slug,
                        accountId,
                        currentType,
                        existing != null ? existing.status() : NamespaceStatus.ACTIVE,
                        displayName != null ? displayName : (existing != null ? existing.displayName() : null),
                        description != null ? description : (existing != null ? existing.description() : null),
                        bias != null ? bias : (existing != null ? existing.bias() : null),
                        createdAt,
                        Instant.now()
                );

                namespaces.put(namespaceId, updated);
                atomicWrite(namespacesFile, namespaces);
                snapshotCache.remove(accountId);

                log.info("[FileAccountCatalog] updated namespace: {} for account {}", namespaceId, accountId);
                return updated;
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to update namespace {} for account {}",
                    slugOrId, accountId, e);
            throw new RuntimeException("Failed to update namespace", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public void resetNamespace(String accountId, String slugOrId) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Path slugsFile = accountDir.resolve(FILE_SLUGS);
                Map<String, String> slugs = new HashMap<>();
                if (Files.exists(slugsFile)) {
                    slugs = objectMapper.readValue(slugsFile.toFile(),
                            new TypeReference<Map<String, String>>() {});
                }

                String namespaceId = slugs.get(slugOrId);
                if (namespaceId == null) {
                    if (slugs.containsValue(slugOrId)) {
                        namespaceId = slugOrId;
                    } else {
                        throw new NamespaceNotFoundException(slugOrId);
                    }
                }

                Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
                if (Files.exists(namespacesFile)) {
                    Map<String, NamespaceRecord> namespaces = objectMapper.readValue(
                            namespacesFile.toFile(),
                            new TypeReference<Map<String, NamespaceRecord>>() {});
                    NamespaceRecord existing = namespaces.get(namespaceId);
                    if (existing != null && existing.legalHold()) {
                        throw new NamespaceLegalHoldException(namespaceId);
                    }
                }

                // Reset data-plane directory: delete bundle/index files and recreate
                Path namespaceDir = StorageLayout.namespaceDirSharded(basePath, namespaceId);
                if (Files.exists(namespaceDir)) {
                    try (var stream = Files.walk(namespaceDir)) {
                        stream.sorted(Comparator.reverseOrder())
                                .filter(p -> !p.equals(namespaceDir))
                                .forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                                });
                    }
                }
                Files.createDirectories(namespaceDir);
                snapshotCache.remove(accountId);
                log.info("[FileAccountCatalog] reset namespace data directory: {}", namespaceId);
            }
        } catch (NamespaceLegalHoldException e) {
            throw e;
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to reset namespace {} for account {}",
                    slugOrId, accountId, e);
            throw new RuntimeException("Failed to reset namespace", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public NamespaceRecord setLegalHold(String accountId, String slugOrId, boolean legalHold) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Path slugsFile = accountDir.resolve(FILE_SLUGS);
                Map<String, String> slugs = new HashMap<>();
                if (Files.exists(slugsFile)) {
                    slugs = objectMapper.readValue(slugsFile.toFile(),
                            new TypeReference<Map<String, String>>() {});
                }

                String namespaceId = slugs.get(slugOrId);
                if (namespaceId == null) {
                    if (slugs.containsValue(slugOrId)) {
                        namespaceId = slugOrId;
                    } else {
                        throw new NamespaceNotFoundException(slugOrId);
                    }
                }

                Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
                Map<String, NamespaceRecord> namespaces = new HashMap<>();
                if (Files.exists(namespacesFile)) {
                    namespaces = new HashMap<>(objectMapper.readValue(namespacesFile.toFile(),
                            new TypeReference<Map<String, NamespaceRecord>>() {}));
                }

                NamespaceRecord existing = namespaces.get(namespaceId);
                if (existing == null) {
                    throw new NamespaceNotFoundException(slugOrId);
                }

                NamespaceRecord updated = new NamespaceRecord(
                        existing.namespaceId(),
                        existing.slug(),
                        existing.ownerAccountId(),
                        existing.type(),
                        existing.status(),
                        existing.displayName(),
                        existing.description(),
                        existing.bias(),
                        existing.createdAt(),
                        Instant.now(),
                        legalHold
                );

                namespaces.put(namespaceId, updated);
                atomicWrite(namespacesFile, namespaces);
                snapshotCache.remove(accountId);

                log.info("[FileAccountCatalog] updated legal hold for namespace {}: {}", namespaceId, legalHold);
                return updated;
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to set legal hold on namespace {} for account {}",
                    slugOrId, accountId, e);
            throw new RuntimeException("Failed to set legal hold", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public List<NamespaceRecord> listAccessible(String accountId) {
        CatalogSnapshot snapshot;
        try {
            snapshot = loadSnapshot(accountId);
        } catch (NamespaceNotFoundException e) {
            return List.of();
        }
        return snapshot.accessibleNamespaces(accountId);
    }

    @Override
    public void setDefaultNamespace(String accountId, String namespaceId) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);
            Path accountFile = accountDir.resolve(FILE_ACCOUNT);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Account account = objectMapper.readValue(accountFile.toFile(), Account.class);
                Account updated = new Account(
                        account.id(),
                        account.kind(),
                        account.profile(),
                        account.displayName(),
                        account.quotas(),
                        account.flags(),
                        namespaceId,
                        account.createdAt()
                );
                atomicWrite(accountFile, updated);
                snapshotCache.remove(accountId);
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to set default namespace for account {}",
                    accountId, e);
            throw new RuntimeException("Failed to set default namespace", e);
        } finally {
            jvmLock.unlock();
        }
    }

    private void appendGrantToAccount(String accountId, Grant grant) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            if (!Files.exists(accountDir)) {
                return;
            }
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                GrantLog.appendGrant(accountDir.resolve(FILE_GRANTS), grant, objectMapper);
                snapshotCache.remove(accountId);
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to append grant {} to account {}", grant.grantId(), accountId, e);
            throw new RuntimeException("Failed to add grant", e);
        } finally {
            jvmLock.unlock();
        }
    }

    private void appendRevokeToAccount(String accountId, String grantId) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            if (!Files.exists(accountDir)) {
                return;
            }
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                GrantLog.appendRevoke(accountDir.resolve(FILE_GRANTS), grantId, objectMapper);
                snapshotCache.remove(accountId);
            }
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to revoke grant {} in account {}", grantId, accountId, e);
            throw new RuntimeException("Failed to revoke grant", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public void addGrant(Grant grant) {
        if (grant.grantedBy() != null) {
            appendGrantToAccount(grant.grantedBy(), grant);
        }
        if (grant.principalId() != null && (grant.grantedBy() == null || !grant.grantedBy().equals(grant.principalId()))) {
            appendGrantToAccount(grant.principalId(), grant);
        }
    }

    @Override
    public void revokeGrant(String grantId) {
        log.info("[FileAccountCatalog] revokeGrant: {}", grantId);
    }

    @Override
    public List<Grant> listGrants(String accountId, String slugOrId) {
        NamespaceRecord record = resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        Optional<Grant> callerGrant = authorize(accountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), accountId);
        }

        CatalogSnapshot snapshot = loadSnapshot(record.ownerAccountId());
        return snapshot.liveGrants().stream()
                .filter(g -> record.namespaceId().equals(g.objectId()) && !g.isExpired())
                .toList();
    }

    @Override
    public Grant grantNamespace(String callerAccountId, String slugOrId, String granteeAccountId,
            GrantRole role, Instant expiresAt, GrantConstraints constraints) {
        if (role == GrantRole.OWNER) {
            throw new IllegalArgumentException("Cannot grant OWNER role directly; ownership transfer is required");
        }

        NamespaceRecord record = resolve(callerAccountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        Optional<Grant> callerGrant = authorize(callerAccountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        if (callerGrant.get().role() != GrantRole.OWNER && callerGrant.get().role().ordinal() > role.ordinal()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        Grant grant = new Grant(
                new com.spectrayan.spector.memory.id.TsidGenerator().generate(),
                GrantObjectType.NAMESPACE,
                record.namespaceId(),
                granteeAccountId,
                PrincipalType.ACCOUNT,
                role,
                null,
                callerAccountId,
                Instant.now(),
                expiresAt,
                constraints
        );

        addGrant(grant);
        return grant;
    }

    @Override
    public void revokeNamespaceGrant(String callerAccountId, String slugOrId, String grantId) {
        NamespaceRecord record = resolve(callerAccountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        Optional<Grant> callerGrant = authorize(callerAccountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        List<Grant> activeGrants = listGrants(callerAccountId, slugOrId);
        Grant target = activeGrants.stream().filter(g -> g.grantId().equals(grantId)).findFirst().orElse(null);

        appendRevokeToAccount(record.ownerAccountId(), grantId);
        if (target != null && !record.ownerAccountId().equals(target.principalId())) {
            appendRevokeToAccount(target.principalId(), grantId);
        }
    }

    @Override
    public boolean authorizeIdentity(String accountId, String bundleId,
            String regionId, GrantAction action) {
        // PEP: own-account identity access is always permitted (ADR-0029 §24)
        if (accountId != null && accountId.equals(bundleId)) {
            return true;
        }
        // Cross-account identity access denied by default in file-backed catalog
        return false;
    }

    @Override
    public void tombstone(String accountId, String namespaceId) {
        ReentrantLock jvmLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
        jvmLock.lock();
        try {
            Path accountDir = StorageLayout.accountDir(basePath, accountId);
            Path lockFile = accountDir.resolve(FILE_LOCK);

            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = channel.lock()) {

                Path slugsFile = accountDir.resolve(FILE_SLUGS);
                String resolvedNamespaceId = namespaceId;
                String slugToRemove = null;
                Map<String, String> slugs = new HashMap<>();

                if (Files.exists(slugsFile)) {
                    slugs = new HashMap<>(objectMapper.readValue(
                            slugsFile.toFile(),
                            new TypeReference<Map<String, String>>() {}));

                    if (slugs.containsKey(namespaceId)) {
                        slugToRemove = namespaceId;
                        resolvedNamespaceId = slugs.get(namespaceId);
                    } else {
                        for (Map.Entry<String, String> entry : slugs.entrySet()) {
                            if (entry.getValue().equals(namespaceId)) {
                                slugToRemove = entry.getKey();
                                break;
                            }
                        }
                    }

                    if ("default".equals(slugToRemove) || accountId.equals(resolvedNamespaceId)) {
                        throw new DefaultNamespaceProtectedException(resolvedNamespaceId);
                    }
                }

                Path namespacesFile = accountDir.resolve(FILE_NAMESPACES);
                if (Files.exists(namespacesFile)) {
                    Map<String, NamespaceRecord> namespaces = new HashMap<>(objectMapper.readValue(
                            namespacesFile.toFile(),
                            new TypeReference<Map<String, NamespaceRecord>>() {}));
                    NamespaceRecord existing = namespaces.get(resolvedNamespaceId);
                    if (existing != null) {
                        if (existing.legalHold()) {
                            throw new NamespaceLegalHoldException(resolvedNamespaceId);
                        }
                        NamespaceRecord tombstoned = new NamespaceRecord(
                                existing.namespaceId(),
                                existing.slug(),
                                existing.ownerAccountId(),
                                existing.type(),
                                NamespaceStatus.TOMBSTONED,
                                existing.displayName(),
                                existing.description(),
                                existing.bias(),
                                existing.createdAt(),
                                Instant.now(),
                                existing.legalHold()
                        );
                        namespaces.put(resolvedNamespaceId, tombstoned);
                        atomicWrite(namespacesFile, namespaces);
                    }
                }

                if (slugToRemove != null && Files.exists(slugsFile)) {
                    slugs.remove(slugToRemove);
                    atomicWrite(slugsFile, slugs);
                }

                snapshotCache.remove(accountId);
                log.info("[FileAccountCatalog] tombstoned namespace {} for account {}", resolvedNamespaceId, accountId);
            }
        } catch (DefaultNamespaceProtectedException | NamespaceLegalHoldException e) {
            throw e;
        } catch (IOException e) {
            log.error("[FileAccountCatalog] failed to tombstone namespace {} for account {}",
                    namespaceId, accountId, e);
            throw new RuntimeException("Failed to tombstone namespace", e);
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public void recordAccess(String namespaceId) {
        // Phase 3: update lastAccessedAt in the catalog record
    }

    // ══════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════

    private void atomicWrite(Path target, Object data) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        objectMapper.writeValue(temp.toFile(), data);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
