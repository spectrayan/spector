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
package com.spectrayan.spector.synapse.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver.Layout;
import com.spectrayan.spector.kernel.storage.NamespacePathResolver.Placement;
import com.spectrayan.spector.synapse.identity.IdentityPaths;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.api.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceTombstonedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;

import org.springframework.beans.factory.ObjectProvider;

/**
 * Resolves an authenticated principal's request to a {@link SpectorMemory} instance
 * via the catalog-mediated resolution chain (ADR-0029 §6.1).
 *
 * <h3>Resolution chain</h3>
 * <pre>
 * 1. Authenticate: SecurityUtils.getUserId() → accountId
 * 2. Catalog:      AccountCatalog.getOrCreateAccount(accountId)
 *                  → account.defaultNamespaceId → namespaceId
 * 3. Authorize:    catalog.authorize(accountId, namespaceId, minimumRole)
 * 4. Place:        owner account → tenantId → NamespacePathResolver (ADR-0034 §9.2)
 * 5. Bind:         cache.getOrOpen(namespaceId, () → buildInstance(tenantId, namespaceId, owner))
 * </pre>
 *
 * <p>Namespace selection via tool argument, header, or {@code namespace_switch}
 * resolves through catalog slugs. Soul stack assembly occurs at bind time via
 * {@link com.spectrayan.spector.synapse.identity.IdentityPlane}.</p>
 *
 * <p>The hot cache is keyed by {@code namespaceId} (ADR §6.3, Q7). Two principals
 * with grants on the same namespace share one {@code SpectorMemory} instance.</p>
 *
 * <p>Because of that shared-instance rule, on-disk placement must not depend on which principal
 * opens a namespace first — it is derived from the <em>owner's</em> tenant. See
 * {@link #placementTenantIdFor(String, String, String, com.spectrayan.spector.synapse.catalog.Account)}.</p>
 *
 * @see AccountCatalog
 * @see MemoryRegistry
 */
public class NamespaceResolver implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NamespaceResolver.class);

    private final AccountCatalog catalog;
    private final SynapseProperties synapseProps;
    private final ObjectProvider<EmbeddingProvider> embedderProvider;
    private final ObjectProvider<LlmProvider> textGenProvider;
    private final ObjectProvider<SalienceProfileProvider> salienceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider;
    private final ObjectProvider<com.spectrayan.spector.memory.persist.DataEncryptor> encryptorProvider;
    private final ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider;
    private final ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider;
    private final ObjectProvider<org.quartz.Scheduler> quartzSchedulerProvider;

    /** Maximum number of concurrently-cached instances (LRU cap). */
    private final int maxInstances;

    /**
     * Instance cache keyed by {@code namespaceId} (not userId, not slug).
     * This is the Phase 1 cache rekey — since namespaceId == accountId for default
     * namespaces, the behavioral change is zero for existing users.
     */
    private final ConcurrentHashMap<String, MemoryHandle> cache = new ConcurrentHashMap<>();

    /** Guards the cold path: lazy build, LRU eviction, and shutdown close. */
    private final ReentrantLock coldPathLock = new ReentrantLock();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    // ── Composition Hoists (R12.2) ──────────────────────────────
    private volatile EmbeddingProvider hoistedEmbeddingProvider;
    private volatile ParallelEmbeddingPipeline hoistedPipeline;
    private volatile SpectorRuntime runtime;

    private final Path basePath;

    private final AtomicLong fallbackCounter = new AtomicLong(0);
    private final MeterRegistry meterRegistry;

    public ParallelEmbeddingPipeline hoistedPipeline() {
        return hoistedPipeline;
    }

    public EmbeddingProvider hoistedEmbeddingProvider() {
        return hoistedEmbeddingProvider;
    }

    public SpectorRuntime runtime() {
        return runtime;
    }

    public void setRuntime(SpectorRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Creates a new namespace resolver.
     *
     * @param catalog               the catalog SPI (file-backed in Phase 1)
     * @param synapseProps          synapse configuration
     * @param embedderProvider      embedding provider
     * @param textGenProvider       LLM provider (optional)
     * @param salienceProvider      salience profile provider (optional)
     * @param objectMapperProvider  Jackson ObjectMapper
     * @param cacheManagerProvider  Spring CacheManager (optional)
     * @param encryptorProvider     data encryptor (optional)
     * @param observationRegistryProvider  micrometer observation (optional)
     * @param observabilityConfigProvider  observability config (optional)
     * @param quartzSchedulerProvider      quartz scheduler (optional)
     * @param maxInstances          LRU cap for cached instances
     */
    public NamespaceResolver(
            AccountCatalog catalog,
            SynapseProperties synapseProps,
            ObjectProvider<EmbeddingProvider> embedderProvider,
            ObjectProvider<LlmProvider> textGenProvider,
            ObjectProvider<SalienceProfileProvider> salienceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider,
            ObjectProvider<com.spectrayan.spector.memory.persist.DataEncryptor> encryptorProvider,
            ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
            ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider,
            ObjectProvider<org.quartz.Scheduler> quartzSchedulerProvider,
            int maxInstances) {
        this(catalog, synapseProps, embedderProvider, textGenProvider, salienceProvider,
                objectMapperProvider, cacheManagerProvider, encryptorProvider,
                observationRegistryProvider, observabilityConfigProvider,
                quartzSchedulerProvider, null, maxInstances);
    }

    public NamespaceResolver(
            AccountCatalog catalog,
            SynapseProperties synapseProps,
            ObjectProvider<EmbeddingProvider> embedderProvider,
            ObjectProvider<LlmProvider> textGenProvider,
            ObjectProvider<SalienceProfileProvider> salienceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider,
            ObjectProvider<com.spectrayan.spector.memory.persist.DataEncryptor> encryptorProvider,
            ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
            ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider,
            ObjectProvider<org.quartz.Scheduler> quartzSchedulerProvider,
            ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider,
            int maxInstances) {
        this.catalog = catalog;
        this.synapseProps = synapseProps;
        this.embedderProvider = embedderProvider;
        this.textGenProvider = textGenProvider;
        this.salienceProvider = salienceProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.cacheManagerProvider = cacheManagerProvider;
        this.encryptorProvider = encryptorProvider;
        this.observationRegistryProvider = observationRegistryProvider;
        this.observabilityConfigProvider = observabilityConfigProvider;
        this.quartzSchedulerProvider = quartzSchedulerProvider;
        this.meterRegistry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
        this.maxInstances = Math.max(1, maxInstances);
        // Canonical rememberer root (Req R3.1) — shared with the migrator, detector, and CLI.
        this.basePath = synapseProps.remembererRoot();
        log.info("[NamespaceResolver] initialized: maxInstances={}, remembererRoot={}",
                this.maxInstances, this.basePath);
    }

    /**
     * Resolves an authenticated principal to their default namespace's {@link SpectorMemory}.
     *
     * <p>Chain: accountId → catalog → defaultNamespaceId → cached engine.</p>
     *
     * @param accountId the authenticated principal's TSID (from JWT sub)
     * @return the cached or newly-built SpectorMemory for the account's default namespace
     */
    public SpectorMemory resolve(String accountId) {
        // Step 1: Catalog — lazy create account + default namespace binding
        Account account = catalog.getOrCreateAccount(accountId);
        String namespaceId = account.defaultNamespaceId();

        // Step 2: Bind — cache lookup by namespaceId (not userId, not slug)
        return getOrBuild(accountId, namespaceId, accountId);
    }

    /**
     * Resolves an authenticated principal and explicit namespace selector (slug or namespaceId)
     * to the corresponding {@link SpectorMemory} instance.
     *
     * <p>Resolution chain (ADR-0029 §6.1):</p>
     * <pre>
     * (accountId, slugOrId) → catalog.resolve → NamespaceRecord → getOrBuild(namespaceId)
     * </pre>
     *
     * @param accountId the authenticated principal's TSID
     * @param slugOrId  the namespace slug or namespace identifier; null/blank resolves default
     * @return the cached or newly-built SpectorMemory
     * @throws NamespaceNotFoundException if the namespace is not found
     * @throws NamespaceTombstonedException if the namespace is soft-deleted
     */
    public SpectorMemory resolve(String accountId, String slugOrId) {
        if (slugOrId == null || slugOrId.isBlank()) {
            return resolve(accountId);
        }
        // Ensures the caller's account exists before slug resolution. The returned Account is not
        // used for placement: that is derived from the namespace owner in placementTenantIdFor.
        catalog.getOrCreateAccount(accountId);
        NamespaceRecord record = catalog.resolve(accountId, slugOrId)
                .orElseThrow(() -> new NamespaceNotFoundException(slugOrId));
        if (record.status() == NamespaceStatus.TOMBSTONED) {
            throw new NamespaceTombstonedException(record.namespaceId());
        }
        catalog.recordAccess(record.namespaceId());
        return getOrBuild(accountId, record.namespaceId(), record.ownerAccountId());
    }

    /**
     * Evicts a namespace instance from the cache and closes it.
     *
     * @param namespaceId the namespace identifier
     */
    public void evict(String namespaceId) {
        if (namespaceId == null) return;
        MemoryHandle handle = cache.remove(namespaceId);
        if (handle != null) {
            closeQuietly(handle.memory);
        }
    }

    /**
     * Checks if a namespace instance is currently cached and hot.
     *
     * @param namespaceId the namespace identifier
     * @return true if currently in the active instance cache
     */
    public boolean isHot(String namespaceId) {
        if (namespaceId == null) return false;
        return cache.containsKey(namespaceId);
    }

    /**
     * Gets or builds the SpectorMemory instance for the given namespaceId with hot cap
     * and lease-aware eviction checks.
     *
     * @param accountId       the principal requesting resolution
     * @param namespaceId     the globally unique namespace identifier
     * @param ownerAccountId  the owner account of the namespace
     * @return the cached or newly-built SpectorMemory
     */
    private SpectorMemory getOrBuild(String accountId, String namespaceId, String ownerAccountId) {
        // Fast path: lock-free cache hit
        MemoryHandle handle = cache.get(namespaceId);
        if (handle != null) {
            handle.touch(accountId);
            return handle.memory;
        }

        MemoryHandle evicted = null;
        try {
            coldPathLock.lock();
            try {
                handle = cache.get(namespaceId);
                if (handle != null) {
                    handle.touch(accountId);
                    return handle.memory;
                }

                Account account = null;
                // 1. Account-level hot cap check (ADR-0029 §2.6, §6.3, Q4)
                if (accountId != null) {
                    account = catalog.getOrCreateAccount(accountId);
                    int maxHot = account.quotas().maxHotNamespaces();
                    if (maxHot > 0) {
                        long currentAccountHot = cache.values().stream()
                                .filter(h -> h.associatedWith(accountId))
                                .count();
                        if (currentAccountHot >= maxHot) {
                            MemoryHandle accountEvicted = evictOldestAccountUnleasedLocked(accountId);
                            if (accountEvicted == null) {
                                throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceHotCapExceededException(
                                        accountId, maxHot);
                            }
                            evicted = accountEvicted;
                        }
                    }
                }

                // 2. Process-wide instance cap check (ADR-0029 §6.3)
                if (cache.size() >= maxInstances) {
                    MemoryHandle processEvicted = evictOldestProcessUnleasedLocked();
                    if (processEvicted == null) {
                        throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceHotCapExceededException(
                                "process", maxInstances);
                    }
                    if (evicted != null && evicted != processEvicted) {
                        closeQuietly(evicted.memory);
                    }
                    evicted = processEvicted;
                }

                boolean tenantRooted = synapseProps != null
                        && synapseProps.getNamespace() != null
                        && synapseProps.getNamespace().isTenantRootedEnabled();
                String tenantId = tenantRooted
                        ? placementTenantIdFor(namespaceId, ownerAccountId, accountId, account)
                        : null;
                SpectorMemory instance = buildInstance(tenantId, namespaceId, ownerAccountId != null ? ownerAccountId : accountId);
                handle = new MemoryHandle(namespaceId, ownerAccountId, accountId, instance);
                cache.put(namespaceId, handle);
            } finally {
                coldPathLock.unlock();
            }
        } finally {
            if (evicted != null) {
                closeQuietly(evicted.memory);
            }
        }
        handle.touch(accountId);
        return handle.memory;
    }

    /**
     * Closes every cached instance exactly once.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        coldPathLock.lock();
        try {
            for (MemoryHandle handle : cache.values()) {
                closeQuietly(handle.memory);
            }
            cache.clear();
            if (runtime != null) {
                try {
                    runtime.close();
                } catch (Exception e) {
                    log.warn("[NamespaceResolver] error closing SpectorRuntime: {}", e.getMessage());
                }
            }
        } finally {
            coldPathLock.unlock();
        }
        log.info("[NamespaceResolver] closed all cached namespace memory instances and runtime");
    }

    /** @return the number of currently-cached instances. */
    public int cachedInstanceCount() {
        return cache.size();
    }

    /** Returns a snapshot of all currently cached SpectorMemory instances. */
    public java.util.List<SpectorMemory> cachedInstances() {
        return cache.values().stream()
                .map(h -> h.memory)
                .toList();
    }

    /** Returns the underlying AccountCatalog (test/admin). */
    public AccountCatalog catalog() {
        return catalog;
    }

    // ══════════════════════════════════════════════════════════════
    // Instance building — same logic as former MemoryRegistry
    // ══════════════════════════════════════════════════════════════

    private void ensureHoistedEmbeddingPipeline(EmbeddingProvider rawEmbedder, com.spectrayan.spector.config.SpectorProperties spectorProps) {
        if (this.hoistedPipeline == null && rawEmbedder != null) {
            synchronized (this) {
                if (this.hoistedPipeline == null) {
                    org.springframework.cache.CacheManager springCacheManager = cacheManagerProvider != null
                            ? cacheManagerProvider.getIfAvailable() : null;
                    com.spectrayan.spector.commons.cache.SpectorCacheManager globalCacheManager;
                    if (springCacheManager != null) {
                        globalCacheManager = com.spectrayan.spector.spring.cache.SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                                .errorHandler(com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler.LOGGING)
                                .build();
                    } else {
                        globalCacheManager = com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager.defaultManager();
                    }
                    this.hoistedEmbeddingProvider = com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider.wrap(
                            rawEmbedder, globalCacheManager);

                    boolean sequential = spectorProps != null
                            && spectorProps.provider() != null
                            && spectorProps.provider().getEmbedding() != null
                            && spectorProps.provider().getEmbedding().isSequential();
                    this.hoistedPipeline = new ParallelEmbeddingPipeline(this.hoistedEmbeddingProvider, sequential);
                }
            }
        }
    }

    private void ensureSpectorRuntime(EmbeddingProvider embedder, com.spectrayan.spector.config.SpectorProperties spectorProps) {
        if (this.runtime == null && embedder != null) {
            synchronized (this) {
                if (this.runtime == null) {
                    ensureHoistedEmbeddingPipeline(embedder, spectorProps);
                    LlmProvider textGen = textGenProvider != null ? textGenProvider.getIfAvailable() : null;
                    this.runtime = SpectorRuntime.builder()
                            .properties(spectorProps)
                            .embeddingProvider(hoistedEmbeddingProvider != null ? hoistedEmbeddingProvider : embedder)
                            .parallelEmbeddingPipeline(hoistedPipeline)
                            .llmProvider(textGen)
                            .build();
                }
            }
        }
    }

    /**
     * Resolves the tenant that determines a namespace's on-disk placement.
     *
     * <p><strong>Placement is a property of the namespace, never of the caller.</strong> A namespace
     * belongs to its owning account, so the tenant segment of its directory must come from the
     * owner's account — not from whichever principal happens to open it first.</p>
     *
     * <p>Deriving it from the caller produced two defects. Because the instance cache is keyed by
     * {@code namespaceId} alone (Invariant I4), the directory a shared namespace landed in depended
     * on open order: if a grantee in another tenant opened it first, the namespace was created empty
     * under <em>that</em> tenant's prefix while the owner's data sat untouched under its own, and a
     * subsequent write produced two divergent directories for one {@code namespaceId}. It also broke
     * the tenant-wipe guarantee (Req R9.2), since a grantee's tenant prefix could contain another
     * tenant's namespace.</p>
     *
     * <p>{@code FileAccountCatalog} creates the directory using the owner's tenant, so any other
     * choice here also desynchronises create from open (Req R6.1, risk K1).</p>
     *
     * @param namespaceId      the namespace being opened, for diagnostics
     * @param ownerAccountId   the owning account, or {@code null} for an ownerless record
     * @param callerAccountId  the requesting principal
     * @param callerAccount    the already-loaded caller account, reused when caller == owner
     * @return the owner's tenant, or {@code null} to select the flat layout
     */
    private String placementTenantIdFor(String namespaceId, String ownerAccountId,
            String callerAccountId, Account callerAccount) {
        if (ownerAccountId == null) {
            // An ownerless record (e.g. a SHARED namespace with no single parent account) has no
            // account to inherit a tenant from, and NamespaceRecord carries no tenantId of its own.
            // Fall back to the flat layout: it is at least deterministic, being a function of
            // namespaceId alone. The trade-off is that such a namespace sits outside every tenant
            // prefix, so a tenant wipe will not reach it — see the spec's follow-up on denormalising
            // tenantId onto NamespaceRecord.
            log.warn("[NamespaceResolver] namespace '{}' has no owner account; placing it on the flat "
                    + "layout because no tenant can be derived deterministically. It will not be "
                    + "covered by a tenant-prefix wipe.", namespaceId);
            return null;
        }
        if (ownerAccountId.equals(callerAccountId) && callerAccount != null) {
            // Common case: the caller owns the namespace. Reuse the account already loaded for the
            // hot-cap check rather than issuing a second catalog lookup.
            return callerAccount.tenantId();
        }
        try {
            Account owner = catalog.getAccount(ownerAccountId);
            if (owner == null) {
                throw new IllegalStateException("catalog returned no account for owner '" + ownerAccountId + "'");
            }
            return owner.tenantId();
        } catch (RuntimeException e) {
            // Guessing here is not an option: defaulting to the flat layout would strand a tenanted
            // namespace outside its tenant prefix, and using the caller's tenant is the defect this
            // method exists to prevent. Fail loud instead.
            throw new IllegalStateException(String.format(
                    "Cannot determine tenant placement for namespace '%s': owner account '%s' is not "
                            + "resolvable in the catalog. Refusing to open it rather than risk placing it "
                            + "under the wrong tenant.", namespaceId, ownerAccountId), e);
        }
    }

    /**
     * Builds a {@link SpectorMemory} instance for the given tenant and namespace.
     *
     * <p>Directory resolution is governed by {@link NamespacePathResolver}:
     * <ul>
     *   <li>When {@code tenantId == null}: resolves to the flat sharded layout (Layout A:
     *       {@code namespaces/XX/YY/namespaceId}).</li>
     *   <li>When {@code tenantId != null}: resolves to the tenant-rooted sharded layout (Layout B:
     *       {@code tenants/XX/YY/tenantId/namespaces/ZZ/WW/namespaceId}).</li>
     * </ul>
     * </p>
     *
     * @param tenantId the tenant TSID or null for untenanted/legacy layout
     * @param namespaceId the globally unique namespace TSID
     * @param ownerAccountId the owning account TSID for identity bundle probe
     * @return the newly-built and attached SpectorMemory instance
     */
    private SpectorMemory buildInstance(String tenantId, String namespaceId, String ownerAccountId) {
        Placement placement = NamespacePathResolver.resolve(basePath(), tenantId, namespaceId);
        Path dir = placement.dir();

        // Dual-read fallback (Task 4.4, Req R5.3, Invariant I6)
        boolean dualReadEnabled = synapseProps != null
                && synapseProps.getNamespace() != null
                && synapseProps.getNamespace().isDualReadEnabled();
        if (tenantId != null && !tenantId.isBlank() && dualReadEnabled) {
            Path marker = dir.resolve(StoragePaths.FILE_NAMESPACE);
            if (!Files.exists(marker)) {
                Placement fallbackPlacement = NamespacePathResolver.resolve(basePath(), null, namespaceId);
                Path fallbackMarker = fallbackPlacement.dir().resolve(StoragePaths.FILE_NAMESPACE);
                if (Files.exists(fallbackMarker)) {
                    log.info("[NamespaceResolver] dual-read fallback for nsId='{}' tenant='{}': falling back to layout A at {}",
                            namespaceId, tenantId, fallbackPlacement.dir());
                    placement = fallbackPlacement;
                    dir = placement.dir();
                    fallbackCounter.incrementAndGet();
                    if (meterRegistry != null) {
                        try {
                            meterRegistry.counter("spector.namespace.layout.fallback").increment();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // R8.1, R8.2, R8.3: Verify layout marker if present, or create on fresh directory
        Path markerFile = dir.resolve(StoragePaths.FILE_NAMESPACE);
        ObjectMapper jsonMapper = null;
        if (objectMapperProvider != null) {
            try {
                jsonMapper = objectMapperProvider.getIfAvailable();
            } catch (Exception ignored) {}
        }
        if (jsonMapper == null) {
            jsonMapper = new ObjectMapper();
        }
        if (Files.exists(markerFile)) {
            // A marker that cannot be read or understood must not be shrugged off. The marker is the
            // backstop against opening a namespace under the wrong layout and silently re-initialising
            // it as empty, so degrading to a warning here would hand back exactly the failure it
            // exists to prevent (Req R8.2).
            Layout foundLayout;
            String recordedLayout;
            try {
                JsonNode node = jsonMapper.readTree(markerFile.toFile());
                String recorded = null;
                if (node.has("layout")) {
                    recorded = node.get("layout").asText();
                } else if (node.has("pathHelper")) {
                    recorded = node.get("pathHelper").asText();
                }
                if (recorded == null || recorded.isBlank()) {
                    // An absent field is the single permitted inference: markers predate this field,
                    // and every such namespace is on the flat layout.
                    foundLayout = Layout.FLAT_SHA256;
                    recordedLayout = Layout.FLAT_SHA256.id();
                } else {
                    foundLayout = Layout.fromId(recorded);
                    recordedLayout = recorded;
                }
            } catch (IOException | IllegalArgumentException e) {
                throw new IllegalStateException(String.format(
                        "Namespace '%s' has an unreadable or unrecognised layout marker at %s. Refusing to "
                                + "open it: the recorded layout cannot be compared against the expected %s, and "
                                + "proceeding risks re-initialising the namespace as empty.",
                        namespaceId, markerFile, placement.layout().id()), e);
            }
            if (foundLayout != placement.layout()) {
                throw new IllegalStateException(String.format(
                        "Namespace layout mismatch for namespace '%s': expected %s (%s), found %s (%s)",
                        namespaceId, placement.layout(), placement.layout().id(), foundLayout, recordedLayout));
            }
        } else {
            try {
                Files.createDirectories(dir);
                Map<String, Object> markerData = new LinkedHashMap<>();
                markerData.put("layout", placement.layout().id());
                markerData.put("pathHelper", placement.layout().id());
                markerData.put("tenantId", placement.tenantId());
                markerData.put("namespaceId", placement.namespaceId());
                jsonMapper.writeValue(markerFile.toFile(), markerData);
            } catch (IOException e) {
                // An unmarked namespace is indistinguishable from a flat-layout one on the next open,
                // so a namespace that cannot record its layout must not be served (Req R8.1).
                throw new IllegalStateException(String.format(
                        "Could not record the layout marker for namespace '%s' at %s. Refusing to open it: "
                                + "an unmarked directory would be read as flat-layout on the next open.",
                        namespaceId, markerFile), e);
            }
        }

        EmbeddingProvider embedder = embedderProvider.getIfAvailable();
        if (embedder == null) {
            throw new IllegalStateException(
                    "Cannot build namespace memory: no EmbeddingProvider bean available");
        }

        var spectorProps = synapseProps.toSpectorProperties();
        LlmProvider textGen = textGenProvider != null ? textGenProvider.getIfAvailable() : null;
        if (spectorProps.memory() != null && spectorProps.memory().getGraph() != null
                && spectorProps.memory().getGraph().getEntity() != null) {
            var entityCfg = spectorProps.memory().getGraph().getEntity();
            if (textGen != null) {
                if ("NONE".equalsIgnoreCase(entityCfg.getExtractionMode())) {
                    entityCfg.setExtractionMode(EntityExtractionMode.LLM.name());
                }
            } else {
                if ("LLM".equalsIgnoreCase(entityCfg.getExtractionMode()) || "NONE".equalsIgnoreCase(entityCfg.getExtractionMode())) {
                    entityCfg.setExtractionMode(EntityExtractionMode.DICTIONARY.name());
                }
            }
        }

        ensureSpectorRuntime(embedder, spectorProps);

        MemoryProperties memory = synapseProps.getMemory();
        SalienceProfileProvider salience = salienceProvider != null ? salienceProvider.getIfAvailable() : null;
        org.springframework.cache.CacheManager springCacheManager = cacheManagerProvider != null
                ? cacheManagerProvider.getIfAvailable() : null;
        com.spectrayan.spector.memory.persist.DataEncryptor encryptor = encryptorProvider != null
                ? encryptorProvider.getIfAvailable(() -> com.spectrayan.spector.memory.persist.DataEncryptor.NOOP)
                : com.spectrayan.spector.memory.persist.DataEncryptor.NOOP;
        ObjectMapper mapper = jsonMapper;

        io.micrometer.observation.ObservationRegistry obsRegistry = observationRegistryProvider != null
                ? observationRegistryProvider.getIfAvailable() : null;
        com.spectrayan.spector.config.ObservabilityConfig obsConfig = observabilityConfigProvider != null
                ? observabilityConfigProvider.getIfAvailable() : null;
        org.quartz.Scheduler springQuartz = quartzSchedulerProvider != null
                ? quartzSchedulerProvider.getIfAvailable() : null;

        final Path effectiveDir = dir;
        SpectorMemory built = runtime.attach(namespaceId, builder -> {
            builder.persistence(effectiveDir);
            if (textGen != null) {
                builder.llmProvider(textGen);
            }
            if (salience != null) {
                builder.salienceProfileProvider(salience);
            }
            if (memory != null && memory.isSpladeEnabled()) {
                builder.sparseEmbeddingProvider(new DenseDerivedSparseProvider(embedder));
            }
            if (memory != null && memory.isColbertEnabled()) {
                builder.tokenEmbeddingProvider(new DenseDerivedTokenProvider(embedder));
            }
            if (springCacheManager != null) {
                var cacheBuilder = com.spectrayan.spector.spring.cache.SpringSpectorCacheManagerAdapter.builder(springCacheManager)
                        .keyGenerator(com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator.forNamespace(namespaceId))
                        .errorHandler(com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler.LOGGING);
                if (encryptor != null && encryptor.isEnabled()) {
                    cacheBuilder.serializer(new com.spectrayan.spector.spring.cache.EncryptingJsonCacheSerializer(mapper, encryptor));
                }
                builder.cacheManager(cacheBuilder.build());
            } else {
                builder.cacheManager(com.spectrayan.spector.commons.cache.SpectorCacheManager.builder()
                        .keyGenerator(com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator.forNamespace(namespaceId))
                        .build());
            }
            if (obsRegistry != null && obsConfig != null) {
                builder.observationHook(new com.spectrayan.spector.metrics.observation.MicrometerMemoryObservationHook(obsRegistry, obsConfig));
            }
            if (springQuartz != null) {
                builder.quartzScheduler(springQuartz);
            }
        });

        if (obsRegistry != null && obsConfig != null) {
            built = new com.spectrayan.spector.metrics.ObservedSpectorMemory(built, obsRegistry, obsConfig);
        }

        String tenantLog = (tenantId != null && !tenantId.isBlank()) ? tenantId : "none";
        log.info("[NamespaceResolver] built namespace memory instance nsId={} tenant={} layout={} (dims={}, persistenceMode={}) via SpectorRuntime",
                namespaceId, tenantLog, placement.layout().id(), memory.getDimensions(), memory.getPersistenceMode());

        // INSULA fallback: only restore salience/soul from Region 24 when no identity bundle
        // exists for this namespace's owner. Post-migration, IdentityPlane supplies the soul
        // stack at bind time — Region 24 is not authoritative (ADR-0029 §23.6).
        try {
            String probeAccountId = ownerAccountId != null ? ownerAccountId : namespaceId;
            StoragePaths.validateNamespaceId(probeAccountId);
            Path idBundlePath;
            if (tenantId != null && !tenantId.isBlank()) {
                idBundlePath = IdentityPaths.tenantAccountIdentityBundle(identityRoot(), tenantId, probeAccountId);
            } else {
                idBundlePath = IdentityPaths.accountIdentityBundle(identityRoot(), probeAccountId);
            }
            if (Files.exists(idBundlePath)) {
                return built;
            }

            Optional<byte[]> bytes = built.admin().insularCortex().get();
            if (bytes.isPresent() && mapper != null) {
                InsulaSelfModel model = mapper.readValue(bytes.get(), InsulaSelfModel.class);
                if (model != null && model.salience() != null) {
                    built.setSalienceProfile(model.salience());
                    log.debug("[NamespaceResolver] restored salience from INSULA fallback for nsId={} "
                            + "(identity bundle should be authoritative post-migration)", namespaceId);
                }
                if (model != null && model.soul() != null) {
                    built.setSoulVersion(model.soul().soulVersion());
                    log.debug("[NamespaceResolver] restored soul version {} from INSULA fallback for nsId={}",
                            model.soul().soulVersion(), namespaceId);
                }
            }
        } catch (Exception e) {
            log.warn("[NamespaceResolver] failed to restore salience from INSULA fallback: {}", e.getMessage());
        }

        return built;
    }

    /** The rememberer root — see {@link SynapseProperties#remembererRoot()} (Req R3.1). */
    Path basePath() {
        return this.basePath;
    }

    /** The identity-plane root — see {@link SynapseProperties#identityRoot()} (Req R3.1). */
    Path identityRoot() {
        return synapseProps != null
                ? synapseProps.identityRoot()
                : Path.of(SynapseProperties.DEFAULT_DATA_DIR);
    }

    private MemoryHandle evictOldestAccountUnleasedLocked(String accountId) {
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<String, MemoryHandle> entry : cache.entrySet()) {
            MemoryHandle h = entry.getValue();
            if (h.associatedWith(accountId) && !isLeased(h.memory)) {
                if (h.lastAccessNanos < oldestAccess) {
                    oldestAccess = h.lastAccessNanos;
                    oldestKey = entry.getKey();
                }
            }
        }
        if (oldestKey != null) {
            MemoryHandle ev = cache.remove(oldestKey);
            if (ev != null) {
                log.info("[NamespaceResolver] Evicting unleased hot namespace '{}' for account '{}' (hot cap reached)",
                        oldestKey, accountId);
                return ev;
            }
        }
        return null;
    }

    private MemoryHandle evictOldestProcessUnleasedLocked() {
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;
        for (Map.Entry<String, MemoryHandle> entry : cache.entrySet()) {
            MemoryHandle h = entry.getValue();
            if (!isLeased(h.memory)) {
                if (h.lastAccessNanos < oldestAccess) {
                    oldestAccess = h.lastAccessNanos;
                    oldestKey = entry.getKey();
                }
            }
        }
        if (oldestKey != null) {
            MemoryHandle ev = cache.remove(oldestKey);
            if (ev != null) {
                log.info("[NamespaceResolver] Evicting unleased hot namespace '{}' (process capacity={})",
                        oldestKey, maxInstances);
                return ev;
            }
        }
        return null;
    }

    MemoryHandle evictOldestLocked() {
        return evictOldestProcessUnleasedLocked();
    }

    public boolean isNamespaceLeased(String namespaceId) {
        if (namespaceId == null) return false;
        MemoryHandle handle = cache.get(namespaceId);
        return handle != null && isLeased(handle.memory);
    }

    public boolean isNamespaceOpen(String namespaceId) {
        if (namespaceId == null) return false;
        return cache.containsKey(namespaceId);
    }

    public long fallbackCount() {
        return fallbackCounter.get();
    }

    private static boolean isLeased(SpectorMemory memory) {
        DefaultSpectorMemory dsm = unwrapDefaultMemory(memory);
        return dsm != null && dsm.hasActiveLeases();
    }

    private static DefaultSpectorMemory unwrapDefaultMemory(SpectorMemory mem) {
        if (mem instanceof DefaultSpectorMemory dsm) {
            return dsm;
        }
        if (mem instanceof com.spectrayan.spector.metrics.ObservedSpectorMemory osm) {
            return unwrapDefaultMemory(osm.unwrap());
        }
        return null;
    }

    private static void closeQuietly(SpectorMemory memory) {
        if (memory == null) return;
        try {
            memory.close();
        } catch (RuntimeException e) {
            log.warn("[NamespaceResolver] error closing namespace memory instance: {}", e.getMessage());
            log.debug("[NamespaceResolver] close failure", e);
        }
    }

    /** Cache entry pairing a namespace instance with its accessing accounts and access time. */
    private static final class MemoryHandle {
        /** Maximum tracked accessing accounts per handle — prevents unbounded growth. */
        private static final int MAX_ACCESSING_ACCOUNTS = 64;

        private final String namespaceId;
        private final String ownerAccountId;
        private final java.util.Set<String> accessingAccounts = ConcurrentHashMap.newKeySet();
        private final SpectorMemory memory;
        private volatile long lastAccessNanos;

        MemoryHandle(SpectorMemory memory) {
            this(null, null, null, memory);
        }

        MemoryHandle(String namespaceId, String ownerAccountId, String initialAccountId, SpectorMemory memory) {
            this.namespaceId = namespaceId;
            this.ownerAccountId = ownerAccountId != null ? ownerAccountId : initialAccountId;
            if (initialAccountId != null) {
                this.accessingAccounts.add(initialAccountId);
            }
            this.memory = memory;
            this.lastAccessNanos = System.nanoTime();
        }

        void touch(String accountId) {
            this.lastAccessNanos = System.nanoTime();
            if (accountId != null && accessingAccounts.size() < MAX_ACCESSING_ACCOUNTS) {
                this.accessingAccounts.add(accountId);
            }
        }

        /** Removes an account from the accessing set (e.g., on grant revoke). */
        void removeAccessing(String accountId) {
            if (accountId != null) {
                this.accessingAccounts.remove(accountId);
            }
        }

        boolean associatedWith(String accountId) {
            if (accountId == null) return false;
            return accountId.equals(ownerAccountId) || accessingAccounts.contains(accountId);
        }
    }
}
