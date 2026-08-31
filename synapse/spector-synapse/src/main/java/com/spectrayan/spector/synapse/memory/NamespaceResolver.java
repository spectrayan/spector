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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
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
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * 4. Bind:         cache.getOrOpen(namespaceId, () → buildInstance(namespaceId))
 * </pre>
 *
 * <p>Namespace selection via tool argument, header, or {@code namespace_switch}
 * resolves through catalog slugs. Soul stack assembly occurs at bind time via
 * {@link com.spectrayan.spector.synapse.identity.IdentityPlane}.</p>
 *
 * <p>The hot cache is keyed by {@code namespaceId} (ADR §6.3, Q7). Two principals
 * with grants on the same namespace share one {@code SpectorMemory} instance.</p>
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
    private final ObjectProvider<com.spectrayan.spector.memory.DataEncryptor> encryptorProvider;
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
            ObjectProvider<com.spectrayan.spector.memory.DataEncryptor> encryptorProvider,
            ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
            ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider,
            ObjectProvider<org.quartz.Scheduler> quartzSchedulerProvider,
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
        this.maxInstances = Math.max(1, maxInstances);
        log.info("[NamespaceResolver] initialized: maxInstances={}", this.maxInstances);
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

                // 1. Account-level hot cap check (ADR-0029 §2.6, §6.3, Q4)
                if (accountId != null) {
                    Account account = catalog.getOrCreateAccount(accountId);
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

                SpectorMemory instance = buildInstance(namespaceId);
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
        } finally {
            coldPathLock.unlock();
        }
        log.info("[NamespaceResolver] closed all cached namespace memory instances");
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

    /**
     * Builds a {@link SpectorMemory} instance for the given namespaceId.
     * Directory path: {@code StorageLayout.namespaceDirSharded(basePath, namespaceId)}.
     * Mirrors the former {@code MemoryRegistry.buildInstance(userId)} exactly —
     * since namespaceId == userId for default namespaces, the directory is identical.
     */
    private SpectorMemory buildInstance(String namespaceId) {
        Path dir = StorageLayout.namespaceDirSharded(basePath(), namespaceId);

        EmbeddingProvider embedder = embedderProvider.getIfAvailable();
        if (embedder == null) {
            throw new IllegalStateException(
                    "Cannot build namespace memory: no EmbeddingProvider bean available");
        }

        MemoryProperties memory = synapseProps.getMemory();

        var builder = DefaultSpectorMemory.builder()
                .dimensions(memory.getDimensions())
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.valueOf(memory.getPersistenceMode().name()))
                .semanticCapacity(memory.getCapacity())
                .hebbianGraphCapacity(memory.getCapacity())
                .temporalChainCapacity(memory.getCapacity())
                .entityGraphCapacity(memory.getCapacity())
                .embedBatchSize(synapseProps.getProvider().getEmbedding().getBatchSize())
                .persistence(dir)
                .bundleMode(memory.isBundleMode())
                .insulaSize(memory.getInsulaSize());

        if (memory.getAisme() != null) {
            builder.aismeConfig(com.spectrayan.spector.memory.aisme.config.AismeConfig.fromProperties(memory.getAisme()));
        }

        LlmProvider textGen = textGenProvider != null ? textGenProvider.getIfAvailable() : null;
        if (textGen != null) {
            builder.entityExtractionMode(EntityExtractionMode.LLM);
            builder.LlmProvider(textGen);
        } else {
            builder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        SalienceProfileProvider salience = salienceProvider != null ? salienceProvider.getIfAvailable() : null;
        if (salience != null) {
            builder.salienceProfileProvider(salience);
        }

        if (memory.isSpladeEnabled()) {
            builder.SparseEmbeddingProvider(new DenseDerivedSparseProvider(embedder));
        }
        if (memory.isColbertEnabled()) {
            builder.tokenEmbeddingProvider(new DenseDerivedTokenProvider(embedder));
        }

        org.springframework.cache.CacheManager springCacheManager = cacheManagerProvider != null
                ? cacheManagerProvider.getIfAvailable() : null;
        com.spectrayan.spector.memory.DataEncryptor encryptor = encryptorProvider != null
                ? encryptorProvider.getIfAvailable(() -> com.spectrayan.spector.memory.DataEncryptor.NOOP)
                : com.spectrayan.spector.memory.DataEncryptor.NOOP;
        ObjectMapper mapper = objectMapperProvider != null
                ? objectMapperProvider.getIfAvailable(ObjectMapper::new) : new ObjectMapper();

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

        io.micrometer.observation.ObservationRegistry obsRegistry = observationRegistryProvider != null
                ? observationRegistryProvider.getIfAvailable() : null;
        com.spectrayan.spector.config.ObservabilityConfig obsConfig = observabilityConfigProvider != null
                ? observabilityConfigProvider.getIfAvailable() : null;

        if (obsRegistry != null && obsConfig != null) {
            builder.observationHook(new com.spectrayan.spector.metrics.observation.MicrometerMemoryObservationHook(obsRegistry, obsConfig));
        }

        if (quartzSchedulerProvider != null) {
            org.quartz.Scheduler springQuartz = quartzSchedulerProvider.getIfAvailable();
            if (springQuartz != null) {
                builder.quartzScheduler(springQuartz);
            }
        }

        SpectorMemory built = builder.build();

        if (obsRegistry != null && obsConfig != null) {
            built = new com.spectrayan.spector.metrics.ObservedSpectorMemory(built, obsRegistry, obsConfig);
        }

        log.info("[NamespaceResolver] built namespace memory instance nsId={} (dims={}, persistenceMode={})",
                namespaceId, memory.getDimensions(), memory.getPersistenceMode());

        // INSULA fallback: only restore salience/soul from Region 24 when no identity bundle
        // exists for this namespace's owner. Post-migration, IdentityPlane supplies the soul
        // stack at bind time — Region 24 is not authoritative (ADR-0029 §23.6).
        try {
            Path idBundlePath = com.spectrayan.spector.synapse.identity.IdentityPaths.accountIdentityBundle(basePath(), namespaceId);
            if (java.nio.file.Files.exists(idBundlePath)) {
                return built;
            }

            java.util.Optional<byte[]> bytes = built.admin().insularCortex().get();
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

    private Path basePath() {
        String path = synapseProps.getMemory().getPersistencePath();
        if (path == null || path.isBlank()) {
            path = synapseProps.dataDir();
        }
        return Path.of(path);
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
