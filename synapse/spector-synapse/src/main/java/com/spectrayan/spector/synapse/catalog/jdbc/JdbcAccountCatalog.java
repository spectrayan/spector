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
package com.spectrayan.spector.synapse.catalog.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.exception.*;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * JDBC-backed implementation of {@link AccountCatalog} for enterprise and production
 * deployments (ADR-0029 v4 §5.2).
 *
 * <p>Persists accounts, namespace records, grants, and organizational containment in the
 * synapse SQL database (H2 for OSS, PostgreSQL for Enterprise) initialized by Flyway {@code V6__memory_catalog.sql}.</p>
 *
 * <p>Invariants:
 * <ul>
 *   <li>One OWNER per namespace record.</li>
 *   <li>Slug is unique per account (enforced by DB constraint and application checks).</li>
 *   <li>Default rememberer has {@code namespaceId == accountId} and {@code slug == "default"}.</li>
 *   <li>Auto-binds existing accounts on first access without moving physical files.</li>
 * </ul>
 * </p>
 */
public class JdbcAccountCatalog implements AccountCatalog {

    private static final Logger log = LoggerFactory.getLogger(JdbcAccountCatalog.class);

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

    private final JdbcClient jdbc;
    private final SqlQueryLoader sqlLoader;
    private final ObjectMapper objectMapper;
    private final TsidGenerator tsid;
    private final org.springframework.beans.factory.ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider;

    public JdbcAccountCatalog(
            JdbcClient jdbc,
            SqlQueryLoader sqlLoader,
            ObjectMapper objectMapper,
            TsidGenerator tsid,
            org.springframework.beans.factory.ObjectProvider<com.spectrayan.spector.commons.cache.SpectorCacheManager> cacheManagerProvider) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.sqlLoader = Objects.requireNonNull(sqlLoader, "sqlLoader must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.tsid = tsid != null ? tsid : new TsidGenerator();
        this.cacheManagerProvider = cacheManagerProvider;
    }

    public JdbcAccountCatalog(JdbcClient jdbc, SqlQueryLoader sqlLoader, ObjectMapper objectMapper, TsidGenerator tsid) {
        this(jdbc, sqlLoader, objectMapper, tsid, null);
    }

    public JdbcAccountCatalog(JdbcClient jdbc, SqlQueryLoader sqlLoader, ObjectMapper objectMapper) {
        this(jdbc, sqlLoader, objectMapper, new TsidGenerator(), null);
    }

    // ══════════════════════════════════════════════════════════════
    // Account Operations
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Account getOrCreateAccount(String accountId) {
        return getOrCreateAccount(accountId, AccountProfile.HUMAN_SOLO, PrincipalKind.HUMAN);
    }

    @Override
    @Transactional
    public Account getOrCreateAccount(String accountId, AccountProfile profile, PrincipalKind kind) {
        Objects.requireNonNull(accountId, "accountId must not be null");

        Optional<Account> existing = findAccountById(accountId);
        if (existing.isPresent()) {
            Account account = existing.get();
            ensureDefaultNamespaceExists(account);
            return account;
        }

        AccountProfile effProfile = profile != null ? profile : AccountProfile.HUMAN_SOLO;
        PrincipalKind effKind = kind != null ? kind : PrincipalKind.HUMAN;

        log.info("[JdbcAccountCatalog] Auto-provisioning account for ID: {}", accountId);
        Instant now = Instant.now();

        // Insert new user account row
        String insertUserSql = """
                INSERT INTO users (
                    user_id, username, password_hash, display_name, roles, scopes,
                    profile, kind, flags, default_namespace_id, created_at, updated_at
                ) VALUES (
                    :userId, :username, '', :displayName, 'ROLE_USER', '',
                    :profile, :kind, '{}', :defaultNamespaceId, :now, :now
                )
                """;

        jdbc.sql(insertUserSql)
                .param("userId", accountId)
                .param("username", accountId)
                .param("displayName", accountId)
                .param("profile", effProfile.name())
                .param("kind", effKind.name())
                .param("defaultNamespaceId", accountId)
                .param("now", Timestamp.from(now))
                .update();

        // Auto-create default namespace record (namespaceId == accountId, slug == "default")
        insertNamespaceRow(new NamespaceRecord(
                accountId,
                "default",
                accountId,
                NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE,
                "Default Namespace",
                "Primary personal memory store",
                null,
                now,
                now
        ));

        // Add implicit OWNER grant
        insertImplicitOwnerGrant(accountId, accountId);

        Account newAccount = new Account(
                accountId,
                effKind,
                effProfile,
                accountId,
                defaultQuotas(effProfile, null, null),
                new AccountFlags(true, true, true),
                accountId,
                now
        );

        return newAccount;
    }

    @Override
    public Account getAccount(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        return findAccountById(accountId)
                .orElseThrow(() -> new NamespaceNotFoundException("account:" + accountId));
    }

    private Optional<Account> findAccountById(String accountId) {
        String sql = sqlLoader.load("catalog/account/find-by-id");
        return jdbc.sql(sql)
                .param("userId", accountId)
                .query(this::mapAccountRow)
                .optional();
    }

    private void ensureDefaultNamespaceExists(Account account) {
        String accountId = account.id();
        String sql = sqlLoader.load("catalog/namespaces/find-by-owner-and-slug");
        Optional<NamespaceRecord> defaultNs = jdbc.sql(sql)
                .param("ownerAccountId", accountId)
                .param("slug", "default")
                .query(this::mapNamespaceRow)
                .optional();

        if (defaultNs.isEmpty()) {
            log.info("[JdbcAccountCatalog] Auto-binding default namespace for account: {}", accountId);
            Instant now = Instant.now();
            insertNamespaceRow(new NamespaceRecord(
                    accountId,
                    "default",
                    accountId,
                    NamespaceType.DEFAULT,
                    NamespaceStatus.ACTIVE,
                    "Default Namespace",
                    "Primary personal memory store",
                    null,
                    now,
                    now
            ));
            insertImplicitOwnerGrant(accountId, accountId);

            if (account.defaultNamespaceId() == null || !account.defaultNamespaceId().equals(accountId)) {
                setDefaultNamespace(accountId, accountId);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Namespace Operations
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public NamespaceRecord createNamespace(String accountId, String slug, NamespaceType type,
                                            String displayName, String description, NamespaceBias bias) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slug, "slug must not be null");
        Objects.requireNonNull(type, "type must not be null");

        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid namespace slug: " + slug + " — must match " + SLUG_PATTERN.pattern());
        }

        Account account = getAccount(accountId);

        // Check account namespace quota
        int currentCount = countOwnedNamespaces(accountId);
        int maxAllowed = account.quotas().maxNamespaces();
        if (maxAllowed > 0 && currentCount >= maxAllowed) {
            throw new AccountQuotaExceededException(accountId, "maxNamespaces exceeded: " + currentCount + " >= " + maxAllowed);
        }

        // Check for duplicate slug
        String findSlugSql = sqlLoader.load("catalog/namespaces/find-by-owner-and-slug");
        Optional<NamespaceRecord> existingSlug = jdbc.sql(findSlugSql)
                .param("ownerAccountId", accountId)
                .param("slug", slug)
                .query(this::mapNamespaceRow)
                .optional();

        if (existingSlug.isPresent()) {
            throw new IllegalArgumentException("Slug already exists: " + slug);
        }

        String namespaceId = (type == NamespaceType.DEFAULT && slug.equals("default"))
                ? accountId
                : tsid.generate();

        Instant now = Instant.now();
        NamespaceRecord record = new NamespaceRecord(
                namespaceId,
                slug,
                accountId,
                type,
                NamespaceStatus.ACTIVE,
                displayName != null ? displayName : slug,
                description != null ? description : "",
                bias,
                now,
                now
        );

        insertNamespaceRow(record);
        insertImplicitOwnerGrant(accountId, namespaceId);

        log.info("[JdbcAccountCatalog] Created namespace: slug={}, id={}, accountId={}",
                slug, namespaceId, accountId);
        return record;
    }

    @Override
    public Optional<NamespaceRecord> resolve(String accountId, String slugOrId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        // 1. Try resolving by slug for this account
        String findBySlugSql = sqlLoader.load("catalog/namespaces/find-by-owner-and-slug");
        Optional<NamespaceRecord> bySlug = jdbc.sql(findBySlugSql)
                .param("ownerAccountId", accountId)
                .param("slug", slugOrId)
                .query(this::mapNamespaceRow)
                .optional();

        if (bySlug.isPresent()) {
            NamespaceRecord record = bySlug.get();
            if (record.status() == NamespaceStatus.TOMBSTONED) {
                return Optional.empty();
            }
            return Optional.of(record);
        }

        // 2. Try resolving by immutable namespaceId (TSID)
        String findByIdSql = sqlLoader.load("catalog/namespaces/find-by-id");
        Optional<NamespaceRecord> byId = jdbc.sql(findByIdSql)
                .param("namespaceId", slugOrId)
                .query(this::mapNamespaceRow)
                .optional();

        if (byId.isPresent()) {
            NamespaceRecord record = byId.get();
            if (record.status() == NamespaceStatus.TOMBSTONED) {
                return Optional.empty();
            }
            // Check if accessible to this account (owned or granted)
            if (record.ownerAccountId().equals(accountId) || hasActiveGrant(accountId, record.namespaceId())) {
                return Optional.of(record);
            }
        }

        return Optional.empty();
    }

    @Override
    public List<NamespaceRecord> listAccessible(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        // Ensure account default namespace is provisioned
        getOrCreateAccount(accountId);

        String sql = sqlLoader.load("catalog/namespaces/list-accessible");
        return jdbc.sql(sql)
                .param("accountId", accountId)
                .query(this::mapNamespaceRow)
                .list();
    }

    @Override
    @Transactional
    public void setDefaultNamespace(String accountId, String namespaceId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");

        NamespaceRecord record = resolve(accountId, namespaceId)
                .orElseThrow(() -> new NamespaceNotFoundException("namespace:" + namespaceId));

        if (record.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(namespaceId);
        }

        String sql = sqlLoader.load("catalog/account/update-default-namespace");
        jdbc.sql(sql)
                .param("defaultNamespaceId", record.namespaceId())
                .param("userId", accountId)
                .update();

        log.info("[JdbcAccountCatalog] Updated default namespace for account {} -> {}",
                accountId, record.namespaceId());
    }

    @Override
    @Transactional
    public NamespaceRecord updateNamespace(String accountId, String slugOrId,
                                            String displayName, String description,
                                            NamespaceType type, NamespaceBias bias) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        NamespaceRecord current = resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        if (!current.ownerAccountId().equals(accountId)) {
            Optional<Grant> adminGrant = authorize(accountId, current.namespaceId(), GrantRole.ADMIN);
            if (adminGrant.isEmpty()) {
                throw new NamespaceAccessDeniedException(current.namespaceId(), "ADMIN role required to update namespace");
            }
        }

        String newDisplayName = displayName != null ? displayName : current.displayName();
        String newDescription = description != null ? description : current.description();
        NamespaceType newType = type != null ? type : current.type();
        NamespaceBias newBias = bias != null ? bias : current.bias();

        String updateSql = sqlLoader.load("catalog/namespaces/update-namespace");
        jdbc.sql(updateSql)
                .param("displayName", newDisplayName)
                .param("description", newDescription)
                .param("type", newType.name())
                .param("biasJson", serializeBias(newBias))
                .param("namespaceId", current.namespaceId())
                .update();

        return new NamespaceRecord(
                current.namespaceId(),
                current.slug(),
                current.ownerAccountId(),
                newType,
                current.status(),
                newDisplayName,
                newDescription,
                newBias,
                current.createdAt(),
                Instant.now()
        );
    }

    @Override
    @Transactional
    public void resetNamespace(String accountId, String slugOrId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        NamespaceRecord record = resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        if (record.legalHold()) {
            throw new NamespaceLegalHoldException(record.namespaceId());
        }

        if (!record.ownerAccountId().equals(accountId)) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), "Only owner may reset namespace");
        }

        recordAccess(record.namespaceId());
        log.info("[JdbcAccountCatalog] Reset metadata recorded for namespace {}", record.namespaceId());
    }

    @Override
    @Transactional
    public NamespaceRecord setLegalHold(String accountId, String slugOrId, boolean legalHold) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        NamespaceRecord record = resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        if (!record.ownerAccountId().equals(accountId)) {
            boolean hasAdmin = authorize(accountId, record.namespaceId(), GrantRole.ADMIN).isPresent();
            if (!hasAdmin) {
                throw new NamespaceAccessDeniedException(record.namespaceId(), "Admin or Owner required to toggle legal hold");
            }
        }

        jdbc.sql("UPDATE namespaces SET legal_hold = :legalHold WHERE namespace_id = :namespaceId")
                .param("legalHold", legalHold)
                .param("namespaceId", record.namespaceId())
                .update();

        log.info("[JdbcAccountCatalog] Updated legal hold for namespace {}: {}", record.namespaceId(), legalHold);
        return resolve(accountId, record.namespaceId()).orElseThrow();
    }

    @Override
    @Transactional
    public void tombstone(String accountId, String namespaceId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");

        NamespaceRecord record = resolve(accountId, namespaceId)
                .orElseThrow(() -> new NamespaceNotFoundException("namespace:" + namespaceId));

        if (record.legalHold()) {
            throw new NamespaceLegalHoldException(record.namespaceId());
        }

        Account account = getAccount(accountId);

        // Invariant: default namespace cannot be deleted
        if (record.slug().equalsIgnoreCase("default")
                || record.type() == NamespaceType.DEFAULT
                || record.namespaceId().equals(account.defaultNamespaceId())
                || record.namespaceId().equals(accountId)) {
            throw new DefaultNamespaceProtectedException(record.slug());
        }

        if (!record.ownerAccountId().equals(accountId)) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), "Only owner may delete namespace");
        }

        String sql = sqlLoader.load("catalog/namespaces/tombstone");
        jdbc.sql(sql)
                .param("namespaceId", record.namespaceId())
                .update();

        log.info("[JdbcAccountCatalog] Tombstoned namespace: id={}, slug={}, accountId={}",
                record.namespaceId(), record.slug(), accountId);
    }

    @Override
    public void recordAccess(String namespaceId) {
        if (namespaceId == null) return;
        try {
            String sql = sqlLoader.load("catalog/namespaces/update-last-accessed");
            jdbc.sql(sql)
                    .param("namespaceId", namespaceId)
                    .update();
        } catch (Exception e) {
            log.warn("[JdbcAccountCatalog] Failed to record access for namespace {}: {}", namespaceId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Grant & Authorization Operations
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void addGrant(Grant grant) {
        Objects.requireNonNull(grant, "grant must not be null");
        String sql = sqlLoader.load("catalog/grants/insert-grant");
        jdbc.sql(sql)
                .param("grantId", grant.grantId())
                .param("objectType", grant.objectType().name())
                .param("objectId", grant.objectId())
                .param("principalId", grant.principalId())
                .param("principalType", grant.principalType().name())
                .param("role", grant.role() != null ? grant.role().name() : null)
                .param("actions", grant.actions() != null ? String.join(",", grant.actions().stream().map(Enum::name).toList()) : null)
                .param("grantedBy", grant.grantedBy())
                .param("grantedAt", Timestamp.from(grant.grantedAt()))
                .param("expiresAt", grant.expiresAt() != null ? Timestamp.from(grant.expiresAt()) : null)
                .param("revokedAt", null)
                .param("constraintsJson", serializeConstraints(grant.constraints()))
                .update();
        bumpMembershipVersion(grant.principalId());
        log.info("[JdbcAccountCatalog] Added grant: id={}, object={}:{}, principal={}",
                grant.grantId(), grant.objectType(), grant.objectId(), grant.principalId());
    }

    @Override
    public List<Grant> listGrants(String accountId, String slugOrId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");

        NamespaceRecord record = resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        // Require ADMIN or OWNER
        Optional<Grant> callerGrant = authorize(accountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), accountId);
        }

        String sql = sqlLoader.load("catalog/grants/list-by-object");
        return jdbc.sql(sql)
                .param("objectType", GrantObjectType.NAMESPACE.name())
                .param("objectId", record.namespaceId())
                .query(this::mapGrantRow)
                .list();
    }

    @Override
    @Transactional
    public Grant grantNamespace(String callerAccountId, String slugOrId, String granteeAccountId,
            GrantRole role, Instant expiresAt, GrantConstraints constraints) {
        Objects.requireNonNull(callerAccountId, "callerAccountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");
        Objects.requireNonNull(granteeAccountId, "granteeAccountId must not be null");
        Objects.requireNonNull(role, "role must not be null");

        if (role == GrantRole.OWNER) {
            throw new IllegalArgumentException("Cannot grant OWNER role directly; ownership transfer is required");
        }

        NamespaceRecord record = resolve(callerAccountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        // Caller must be ADMIN or OWNER
        Optional<Grant> callerGrant = authorize(callerAccountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        // Caller cannot grant role higher than their own
        if (callerGrant.get().role() != GrantRole.OWNER && callerGrant.get().role().ordinal() > role.ordinal()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        Grant grant = new Grant(
                tsid.generate(),
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
    @Transactional
    public void revokeGrant(String grantId) {
        Objects.requireNonNull(grantId, "grantId must not be null");
        try {
            String findSql = "SELECT principal_id FROM grants WHERE grant_id = :grantId";
            String principalId = jdbc.sql(findSql)
                    .param("grantId", grantId)
                    .query(String.class)
                    .optional().orElse(null);
            if (principalId != null) {
                bumpMembershipVersion(principalId);
            }
        } catch (Exception e) {
            log.warn("[JdbcAccountCatalog] Error querying principal for grant bump: {}", e.getMessage());
        }
        String sql = sqlLoader.load("catalog/grants/revoke-grant");
        jdbc.sql(sql)
                .param("grantId", grantId)
                .update();
        log.info("[JdbcAccountCatalog] Revoked grant: id={}", grantId);
    }

    @Override
    @Transactional
    public void revokeNamespaceGrant(String callerAccountId, String slugOrId, String grantId) {
        Objects.requireNonNull(callerAccountId, "callerAccountId must not be null");
        Objects.requireNonNull(slugOrId, "slugOrId must not be null");
        Objects.requireNonNull(grantId, "grantId must not be null");

        NamespaceRecord record = resolve(callerAccountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));

        // Caller must be ADMIN or OWNER
        Optional<Grant> callerGrant = authorize(callerAccountId, record.namespaceId(), GrantRole.ADMIN);
        if (callerGrant.isEmpty()) {
            throw new NamespaceAccessDeniedException(record.namespaceId(), callerAccountId);
        }

        revokeGrant(grantId);
    }

    @Override
    public Optional<Grant> authorize(String accountId, String namespaceId, GrantRole minimumRole) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(minimumRole, "minimumRole must not be null");

        long version = getMembershipVersion(accountId);
        String cacheKey = accountId + ":" + namespaceId + ":" + minimumRole.name() + ":v" + version;
        var cache = getCache(com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants.CACHE_PEP_NAMESPACE);
        if (cache != null) {
            Optional<Grant> cached = cache.get(cacheKey, Grant.class);
            if (cached != null && cached.isPresent()) {
                return cached;
            }
        }

        // 1. Check if owner in namespaces table
        String findNsSql = sqlLoader.load("catalog/namespaces/find-by-id");
        Optional<NamespaceRecord> ns = jdbc.sql(findNsSql)
                .param("namespaceId", namespaceId)
                .query(this::mapNamespaceRow)
                .optional();

        if (ns.isPresent() && ns.get().ownerAccountId().equals(accountId)) {
            Grant ownerGrant = new Grant(
                    "implicit-owner-" + accountId + "-" + namespaceId,
                    GrantObjectType.NAMESPACE,
                    namespaceId,
                    accountId,
                    PrincipalType.ACCOUNT,
                    GrantRole.OWNER,
                    Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN, GrantAction.INJECT),
                    accountId,
                    ns.get().createdAt(),
                    null,
                    null
            );
            if (cache != null) {
                cache.put(cacheKey, ownerGrant);
            }
            return Optional.of(ownerGrant);
        }

        // 2. Query active grants
        String findGrantSql = sqlLoader.load("catalog/grants/find-active-grant");
        List<Grant> grants = jdbc.sql(findGrantSql)
                .param("objectType", GrantObjectType.NAMESPACE.name())
                .param("objectId", namespaceId)
                .param("principalId", accountId)
                .query(this::mapGrantRow)
                .list();

        for (Grant grant : grants) {
            if (grant.role() != null && grant.role().ordinal() <= minimumRole.ordinal()) {
                if (cache != null) {
                    cache.put(cacheKey, grant);
                }
                return Optional.of(grant);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean authorizeIdentity(String accountId, String bundleId, String regionId, GrantAction action) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(bundleId, "bundleId must not be null");
        Objects.requireNonNull(action, "action must not be null");

        long version = getMembershipVersion(accountId);
        String cacheKey = accountId + ":" + bundleId + ":" + (regionId != null ? regionId : "*") + ":" + action.name() + ":v" + version;
        var cache = getCache(com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants.CACHE_PEP_IDENTITY);
        if (cache != null) {
            Optional<Boolean> cached = cache.get(cacheKey, Boolean.class);
            if (cached != null && cached.isPresent()) {
                return cached.get();
            }
        }

        boolean result = evaluateAuthorizeIdentity(accountId, bundleId, regionId, action);
        if (cache != null) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    private boolean evaluateAuthorizeIdentity(String accountId, String bundleId, String regionId, GrantAction action) {
        // 1. Account owner has full access to own identity bundle
        if (accountId.equals(bundleId)) {
            return true;
        }

        // 2. Direct region grant (IDENTITY_REGION objectId=bundleId:regionId)
        if (regionId != null && !regionId.isBlank()) {
            String findGrantSql = sqlLoader.load("catalog/grants/find-active-grant");
            List<Grant> regionGrants = jdbc.sql(findGrantSql)
                    .param("objectType", GrantObjectType.IDENTITY_REGION.name())
                    .param("objectId", bundleId + ":" + regionId)
                    .param("principalId", accountId)
                    .query(this::mapGrantRow)
                    .list();

            if (regionGrants.stream().anyMatch(g -> isActionPermitted(g, action))) {
                return true;
            }
        }

        // 3. Bundle-level grant (IDENTITY_BUNDLE objectId=bundleId with optional region constraints)
        String findGrantSql = sqlLoader.load("catalog/grants/find-active-grant");
        List<Grant> bundleGrants = jdbc.sql(findGrantSql)
                .param("objectType", GrantObjectType.IDENTITY_BUNDLE.name())
                .param("objectId", bundleId)
                .param("principalId", accountId)
                .query(this::mapGrantRow)
                .list();

        for (Grant grant : bundleGrants) {
            if (!isActionPermitted(grant, action)) {
                continue;
            }
            if (regionId != null && !regionId.isBlank() && grant.constraints() != null) {
                Set<String> allowedRegions = grant.constraints().regionIds();
                if (allowedRegions != null && !allowedRegions.isEmpty() && !allowedRegions.contains(regionId)) {
                    continue;
                }
            }
            return true;
        }

        return false;
    }

    private boolean isActionPermitted(Grant g, GrantAction action) {
        if (g == null || g.actions() == null) return false;
        if (action == GrantAction.INJECT) {
            // ADMIN does NOT imply INJECT. INJECT must be explicitly granted.
            return g.actions().contains(GrantAction.INJECT);
        }
        return g.actions().contains(action) || g.actions().contains(GrantAction.ADMIN);
    }

    @Override
    public List<String> orgUnitIdsForAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return List.of();
        }
        String sql = sqlLoader.load("catalog/org/list-org-units-for-account");
        return jdbc.sql(sql)
                .param("accountId", accountId)
                .query((rs, rowNum) -> rs.getString("org_unit_id"))
                .list();
    }

    @Override
    @Transactional
    public void addOrgMember(String accountId, String orgUnitId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(orgUnitId, "orgUnitId must not be null");

        // Ensure user account exists
        getOrCreateAccount(accountId);

        // Ensure org_unit exists in org_units table
        String checkOrgSql = "SELECT COUNT(*) FROM org_units WHERE org_unit_id = :orgUnitId";
        Integer count = jdbc.sql(checkOrgSql)
                .param("orgUnitId", orgUnitId)
                .query(Integer.class)
                .optional().orElse(0);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("OrgUnit '" + orgUnitId + "' does not exist in catalog");
        }

        // Insert membership (ignore duplicate)
        try {
            jdbc.sql("INSERT INTO org_unit_members (org_unit_id, account_id) VALUES (:orgUnitId, :accountId)")
                    .param("orgUnitId", orgUnitId)
                    .param("accountId", accountId)
                    .update();
        } catch (Exception e) {
            log.debug("[JdbcAccountCatalog] Org member insert duplicate or ignored: {}", e.getMessage());
        }

        bumpMembershipVersion(accountId);
    }

    private com.spectrayan.spector.commons.cache.SpectorCache getCache(String name) {
        if (cacheManagerProvider == null) return null;
        var cm = cacheManagerProvider.getIfAvailable();
        return cm != null ? cm.getCache(name) : null;
    }

    private long getMembershipVersion(String accountId) {
        if (accountId == null) return 0L;
        try {
            Long v = jdbc.sql("SELECT membership_version FROM users WHERE user_id = :userId")
                    .param("userId", accountId)
                    .query(Long.class)
                    .optional().orElse(0L);
            return v != null ? v : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    @Transactional
    public void removeOrgMember(String accountId, String orgUnitId) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(orgUnitId, "orgUnitId must not be null");

        jdbc.sql("DELETE FROM org_unit_members WHERE org_unit_id = :orgUnitId AND account_id = :accountId")
                .param("orgUnitId", orgUnitId)
                .param("accountId", accountId)
                .update();

        bumpMembershipVersion(accountId);
    }

    private void bumpMembershipVersion(String accountId) {
        if (accountId == null) return;
        try {
            jdbc.sql("UPDATE users SET membership_version = membership_version + 1 WHERE user_id = :accountId")
                    .param("accountId", accountId)
                    .update();
        } catch (Exception e) {
            log.warn("[JdbcAccountCatalog] Failed to bump membership_version: {}", e.getMessage());
        }
    }

    private boolean hasActiveGrant(String accountId, String namespaceId) {
        String findGrantSql = sqlLoader.load("catalog/grants/find-active-grant");
        List<Grant> grants = jdbc.sql(findGrantSql)
                .param("objectType", GrantObjectType.NAMESPACE.name())
                .param("objectId", namespaceId)
                .param("principalId", accountId)
                .query(this::mapGrantRow)
                .list();
        return !grants.isEmpty();
    }

    private int countOwnedNamespaces(String accountId) {
        String sql = "SELECT COUNT(*) FROM namespaces WHERE owner_account_id = :ownerAccountId AND status != 'TOMBSTONED'";
        Integer count = jdbc.sql(sql)
                .param("ownerAccountId", accountId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    private void insertNamespaceRow(NamespaceRecord record) {
        String sql = sqlLoader.load("catalog/namespaces/insert-namespace");
        jdbc.sql(sql)
                .param("namespaceId", record.namespaceId())
                .param("ownerAccountId", record.ownerAccountId())
                .param("slug", record.slug())
                .param("type", record.type().name())
                .param("status", record.status().name())
                .param("displayName", record.displayName())
                .param("description", record.description())
                .param("biasJson", serializeBias(record.bias()))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("lastAccessedAt", record.lastAccessedAt() != null ? Timestamp.from(record.lastAccessedAt()) : null)
                .param("legalHold", record.legalHold())
                .update();
    }

    private void insertImplicitOwnerGrant(String accountId, String namespaceId) {
        try {
            addGrant(new Grant(
                    tsid.generate(),
                    GrantObjectType.NAMESPACE,
                    namespaceId,
                    accountId,
                    PrincipalType.ACCOUNT,
                    GrantRole.OWNER,
                    Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN, GrantAction.INJECT),
                    accountId,
                    Instant.now(),
                    null,
                    null
            ));
        } catch (Exception e) {
            log.debug("[JdbcAccountCatalog] Implicit grant already exists or failed: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Row Mappers & Serialization Helpers
    // ══════════════════════════════════════════════════════════════

    private Account mapAccountRow(ResultSet rs, int rowNum) throws SQLException {
        String userId = rs.getString("user_id");
        String displayName = rs.getString("display_name");
        String kindStr = rs.getString("kind");
        String profileStr = rs.getString("profile");
        String flagsStr = rs.getString("flags");
        String defaultNsId = rs.getString("default_namespace_id");
        Integer maxNs = rs.getObject("max_namespaces", Integer.class);
        Integer maxHotNs = rs.getObject("max_hot_namespaces", Integer.class);
        Timestamp createdAt = rs.getTimestamp("created_at");
        String tenantId = null;
        try {
            tenantId = rs.getString("tenant_id");
        } catch (SQLException ignored) {
        }
        boolean legalHold = false;
        try {
            legalHold = rs.getBoolean("legal_hold");
        } catch (SQLException ignored) {
        }

        PrincipalKind kind = kindStr != null ? PrincipalKind.valueOf(kindStr) : PrincipalKind.HUMAN;
        AccountProfile profile = profileStr != null ? AccountProfile.valueOf(profileStr) : AccountProfile.HUMAN_SOLO;
        AccountFlags flags = parseFlags(flagsStr);
        AccountQuotas quotas = defaultQuotas(profile, maxNs, maxHotNs);

        return new Account(
                userId,
                kind,
                profile,
                displayName != null ? displayName : userId,
                quotas,
                flags,
                defaultNsId != null ? defaultNsId : userId,
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                tenantId,
                legalHold
        );
    }

    private NamespaceRecord mapNamespaceRow(ResultSet rs, int rowNum) throws SQLException {
        String namespaceId = rs.getString("namespace_id");
        String ownerAccountId = rs.getString("owner_account_id");
        String slug = rs.getString("slug");
        String typeStr = rs.getString("type");
        String statusStr = rs.getString("status");
        String displayName = rs.getString("display_name");
        String description = rs.getString("description");
        String biasJson = rs.getString("bias_json");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp lastAccessedAt = rs.getTimestamp("last_accessed_at");
        boolean legalHold = false;
        try {
            legalHold = rs.getBoolean("legal_hold");
        } catch (SQLException ignored) {
        }

        return new NamespaceRecord(
                namespaceId,
                slug,
                ownerAccountId,
                typeStr != null ? NamespaceType.valueOf(typeStr) : NamespaceType.PROJECT,
                statusStr != null ? NamespaceStatus.valueOf(statusStr) : NamespaceStatus.ACTIVE,
                displayName,
                description,
                parseBias(biasJson),
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                lastAccessedAt != null ? lastAccessedAt.toInstant() : null,
                legalHold
        );
    }

    private Grant mapGrantRow(ResultSet rs, int rowNum) throws SQLException {
        String grantId = rs.getString("grant_id");
        String objTypeStr = rs.getString("object_type");
        String objId = rs.getString("object_id");
        String principalId = rs.getString("principal_id");
        String principalTypeStr = rs.getString("principal_type");
        String roleStr = rs.getString("role");
        String actionsStr = rs.getString("actions");
        String grantedBy = rs.getString("granted_by");
        Timestamp grantedAt = rs.getTimestamp("granted_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        String constraintsJson = rs.getString("constraints_json");

        Set<GrantAction> actions = new HashSet<>();
        if (actionsStr != null && !actionsStr.isBlank()) {
            for (String a : actionsStr.split(",")) {
                if (!a.isBlank()) {
                    actions.add(GrantAction.valueOf(a.trim()));
                }
            }
        }

        return new Grant(
                grantId,
                GrantObjectType.valueOf(objTypeStr),
                objId,
                principalId,
                PrincipalType.valueOf(principalTypeStr),
                roleStr != null ? GrantRole.valueOf(roleStr) : null,
                actions,
                grantedBy,
                grantedAt != null ? grantedAt.toInstant() : Instant.now(),
                expiresAt != null ? expiresAt.toInstant() : null,
                parseConstraints(constraintsJson)
        );
    }

    private AccountQuotas defaultQuotas(AccountProfile profile, Integer customMaxNs, Integer customMaxHotNs) {
        int maxNs = customMaxNs != null ? customMaxNs : switch (profile) {
            case HUMAN_SOLO -> 4;
            case HUMAN_TEAM -> 16;
            case AGENT -> 64;
            case SERVICE -> 256;
            case UNLIMITED -> -1;
        };

        int maxHotNs = customMaxHotNs != null ? customMaxHotNs : switch (profile) {
            case HUMAN_SOLO -> 2;
            case HUMAN_TEAM -> 4;
            case AGENT -> 4;
            case SERVICE -> 8;
            case UNLIMITED -> -1;
        };

        return new AccountQuotas(maxNs, maxHotNs, -1, -1);
    }

    private AccountFlags parseFlags(String json) {
        if (json == null || json.isBlank()) {
            return new AccountFlags(true, true, true);
        }
        try {
            return objectMapper.readValue(json, AccountFlags.class);
        } catch (Exception e) {
            return new AccountFlags(true, true, true);
        }
    }

    private NamespaceBias parseBias(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, NamespaceBias.class);
        } catch (Exception e) {
            log.warn("[JdbcAccountCatalog] Failed to parse bias json: {}", e.getMessage());
            return null;
        }
    }

    private String serializeBias(NamespaceBias bias) {
        if (bias == null) return null;
        try {
            return objectMapper.writeValueAsString(bias);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private GrantConstraints parseConstraints(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, GrantConstraints.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeConstraints(GrantConstraints constraints) {
        if (constraints == null) return null;
        try {
            return objectMapper.writeValueAsString(constraints);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
