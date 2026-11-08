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
package com.spectrayan.spector.synapse.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /**
     * Per-namespace single-flight cold-open table.
     * Prevents holding {@link #coldPathLock} across {@link #buildInstance} I/O while
     * coalescing concurrent cold opens for the same {@code namespaceId}.
     */
    private final ConcurrentHashMap<String, InFlightOpen> inFlightOpens = new ConcurrentHashMap<>();

    /** Guards cache and in-flight admission/eviction state transitions, not buildInstance I/O. */
    private final ReentrantLock coldPathLock = new ReentrantLock();

    /** Guards runtime and embedding pipeline lazy initialization without virtual-thread pinning. */
    private final ReentrantLock runtimeInitLock = new ReentrantLock();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Per-thread depth counter tracking active {@link #buildInstance} execution to prevent re-entrant resolution. */
    private final ThreadLocal<Integer> buildingDepth = ThreadLocal.withInitial(() -> 0);

    /**
     * Returns {@code true} when the current thread is actively assembling a namespace memory instance
     * inside {@link #buildInstance(String, String, String)}.
     */
    public boolean isBuildingOnCurrentThread() {
        return buildingDepth.get() > 0;
    }

    // ── Composition Hoists (R12.2) ──────────────────────────────
    private volatile EmbeddingProvider hoistedEmbeddingProvider;
    private volatile ParallelEmbeddingPipeline hoistedPipeline;
    private volatile SpectorRuntime runtime;

    /**
     * Embedding providers shared by configuration rather than by namespace.
     *
     * <p>The hoisted fields above remain the process default, used when a namespace resolves to no
     * override. This pool serves namespaces whose resolved configuration differs, sharing one instance
     * per distinct configuration so that N namespaces on one model do not become N HTTP clients.</p>
     */
    private final EmbeddingProviderPool providerPool = new EmbeddingProviderPool();

    /** Per-namespace embedding pipelines, keyed by provider fingerprint. */
    private final ConcurrentHashMap<com.spectrayan.spector.provider.ProviderFingerprint, ParallelEmbeddingPipeline>
            pipelinesByFingerprint = new ConcurrentHashMap<>();

    /** Fingerprint each open namespace holds a pool reference for, so eviction can release it. */
    private final ConcurrentHashMap<String, com.spectrayan.spector.provider.ProviderFingerprint>
            fingerprintsByNamespace = new ConcurrentHashMap<>();

    /** Resolves the effective embedding configuration for a namespace; null means "no overrides". */
    private volatile EmbeddingConfigResolver embeddingConfigResolver;

    /**
     * LLM providers shared by configuration rather than by namespace.
     *
     * <p>Separate from the process-wide {@code ProviderRegistry}, which keeps one active-generation name and
     * therefore cannot express a per-tenant choice.</p>
     */
    private final LlmProviderPool llmPool = new LlmProviderPool();

    /** Fingerprint each open namespace holds an LLM pool reference for, so eviction can release it. */
    private final ConcurrentHashMap<String, com.spectrayan.spector.provider.ProviderFingerprint>
            llmFingerprintsByNamespace = new ConcurrentHashMap<>();

    /** Resolves the effective LLM configuration for a namespace; null means "no overrides". */
    private volatile EmbeddingConfigResolver llmConfigResolver;

    /**
     * Installs the resolver that supplies per-namespace LLM configuration.
     *
     * <p>Until it is set, every namespace uses the process-wide LLM bean, which is the pre-existing
     * behaviour and remains correct for a system-wide default.</p>
     *
     * @param resolver the resolver, or {@code null} to disable per-namespace LLM resolution
     */
    public void setLlmConfigResolver(EmbeddingConfigResolver resolver) {
        this.llmConfigResolver = resolver;
        log.info("[NamespaceResolver] per-namespace LLM config resolver {}",
                resolver != null ? "installed" : "cleared");
    }

    /** @return the LLM provider pool, for metrics and tests. */
    public LlmProviderPool llmPool() {
        return llmPool;
    }

    /**
     * Supplies the effective embedding configuration for a namespace.
     *
     * <p>Defined as an interface here rather than depending on {@code ConfigResolutionService} directly so
     * that {@code NamespaceResolver} keeps working with no config plane at all — the embedded and
     * single-tenant cases — and so tests can resolve without a database.</p>
     */
    @FunctionalInterface
    public interface EmbeddingConfigResolver {
        /**
         * Returns the effective embedding configuration for a namespace, or {@code null} to use the
         * process default.
         *
         * @param tenantId    the tenant, may be {@code null} on the flat layout
         * @param namespaceId the namespace being opened
         * @return the effective provider configuration, or {@code null}
         */
        com.spectrayan.spector.provider.ProviderConfig resolve(String tenantId, String namespaceId);
    }

    /**
     * Installs the resolver that supplies per-namespace embedding configuration.
     *
     * <p>Wired by the config plane at startup. Until it is set, every namespace uses the process-default
     * embedder, which is the pre-existing behaviour.</p>
     *
     * @param resolver the resolver, or {@code null} to disable per-namespace resolution
     */
    public void setEmbeddingConfigResolver(EmbeddingConfigResolver resolver) {
        this.embeddingConfigResolver = resolver;
        log.info("[NamespaceResolver] per-namespace embedding config resolver {}",
                resolver != null ? "installed" : "cleared");
    }

    /** @return the provider pool, for metrics and tests. */
    public EmbeddingProviderPool providerPool() {
        return providerPool;
    }

    private final Path basePath;

    private final AtomicLong fallbackCounter = new AtomicLong(0);
    private final MeterRegistry meterRegistry;
    private volatile com.spectrayan.spector.cluster.OwnershipResolver ownershipResolver;
    private volatile com.spectrayan.spector.cluster.fencing.FenceTokenManager fenceTokenManager;

    public void setOwnershipResolver(com.spectrayan.spector.cluster.OwnershipResolver ownershipResolver) {
        this.ownershipResolver = ownershipResolver;
    }

    public com.spectrayan.spector.cluster.OwnershipResolver ownershipResolver() {
        return ownershipResolver;
    }

    public void setFenceTokenManager(com.spectrayan.spector.cluster.fencing.FenceTokenManager fenceTokenManager) {
        this.fenceTokenManager = fenceTokenManager;
    }

    public com.spectrayan.spector.cluster.fencing.FenceTokenManager fenceTokenManager() {
        return fenceTokenManager;
    }

    long resolveCurrentFenceEpoch(String tenantId, String namespaceId) {
        com.spectrayan.spector.cluster.OwnershipResolver resolver = this.ownershipResolver;
        com.spectrayan.spector.cluster.fencing.FenceTokenManager ftm = this.fenceTokenManager;
        boolean clustered = resolver != null
                && resolver.identity().role() != com.spectrayan.spector.cluster.node.NodeRole.STANDALONE;

        long routeEpoch = -1L;
        if (clustered) {
            String cellId = (synapseProps != null && synapseProps.cell() != null && synapseProps.cell().getId() != null)
                    ? synapseProps.cell().getId()
                    : resolver.identity().cellId();
            com.spectrayan.spector.cluster.routing.RoutingKey routingKey =
                    new com.spectrayan.spector.cluster.routing.RoutingKey(cellId, tenantId, namespaceId);
            if (!resolver.ownsLocally(routingKey)) {
                return Long.MAX_VALUE;
            }
            routeEpoch = Math.max(0L, resolver.resolve(routingKey).epoch());
        }

        if (ftm != null) {
            if (ftm.isSurrendered(namespaceId)) {
                return Long.MAX_VALUE;
            }
            if (ftm.hasLocalFence(namespaceId)) {
                if (ftm.isFenceExpired(namespaceId)) {
                    return Long.MAX_VALUE;
                }
                return ftm.getLocalFence(namespaceId);
            }
        }

        return clustered ? routeEpoch : -1L;
    }

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
        if (this.meterRegistry != null) {
            com.spectrayan.spector.metrics.observation.SpectorHostGauges.instance().bindTo(this.meterRegistry);
            // Distinct live embedding configurations. This is the number that must stay flat as namespaces
            // are added when they share a configuration; if it tracks namespace count, the pool is keyed
            // wrong and the deployment is paying for one client per namespace.
            io.micrometer.core.instrument.Gauge
                    .builder("spector.embedding.provider.configurations", providerPool,
                            EmbeddingProviderPool::distinctConfigurations)
                    .description("Distinct live embedding provider configurations held by the pool")
                    .register(this.meterRegistry);
        }
        this.maxInstances = resolveEffectiveMaxInstances(maxInstances, synapseProps);
        if (synapseProps != null && synapseProps.cell() != null) {
            try {
                this.ownershipResolver = synapseProps.cell().toOwnershipResolver();
            } catch (RuntimeException ignored) {
                // Explicit OwnershipResolver bean may be wired separately when ring properties are not inline
            }
        }
        // Canonical rememberer root (Req R3.1) — shared with the migrator, detector, and CLI.
        this.basePath = synapseProps.remembererRoot();
        log.info("[NamespaceResolver] initialized: maxInstances={}, remembererRoot={}",
                this.maxInstances, this.basePath);
    }

    /** @return the effective process-wide hot namespace cap. */
    public int maxInstances() {
        return maxInstances;
    }

    /**
     * Resolves the effective hot-namespace cap.
     *
     * <p>Precedence:
     * <ol>
     *   <li>Explicit positive constructor argument {@code configuredMaxInstances &gt; 0}.</li>
     *   <li>Explicit positive property {@code spector.auth.memory.max-instances &gt; 0}.</li>
     *   <li>Derived from memory budget ({@code spector.auth.memory.budget-bytes} or 50% of JVM max
     *       memory) divided by {@link #estimateNamespaceFootprintBytes(MemoryProperties)}, clamped to
     *       {@code [16, 4096]}.</li>
     * </ol>
     */
    public static int resolveEffectiveMaxInstances(int configuredMaxInstances, SynapseProperties synapseProps) {
        if (configuredMaxInstances > 0) {
            return configuredMaxInstances;
        }
        if (synapseProps != null && synapseProps.getAuth() != null && synapseProps.getAuth().getMemory() != null) {
            int propMax = synapseProps.getAuth().getMemory().getMaxInstances();
            if (propMax > 0) {
                return propMax;
            }
        }
        long budgetBytes = defaultNamespaceBudgetBytes(synapseProps);
        MemoryProperties memProps = synapseProps != null ? synapseProps.getMemory() : null;
        return deriveMaxInstancesFromBudget(budgetBytes, memProps);
    }

    /**
     * Derives the maximum active namespace count from a memory budget in bytes and the configured
     * per-namespace memory layout.
     */
    public static int deriveMaxInstancesFromBudget(long budgetBytes, MemoryProperties memProps) {
        long effectiveBudget = budgetBytes > 0L ? budgetBytes : Math.max(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 2L);
        long footprintBytes = Math.max(1L, estimateNamespaceFootprintBytes(memProps));
        long raw = effectiveBudget / footprintBytes;
        return (int) Math.clamp(raw, 16L, 4_096L);
    }

    /**
     * Estimates the steady-state per-namespace memory footprint in bytes from configured capacities
     * and vector dimensions.
     */
    public static long estimateNamespaceFootprintBytes(MemoryProperties memProps) {
        int dims = memProps != null && memProps.getDimensions() > 0 ? memProps.getDimensions() : 384;
        long semanticCap = memProps != null && memProps.getSemanticCapacity() > 0 ? memProps.getSemanticCapacity() : 10_000L;
        long episodicCap = memProps != null && memProps.getEpisodicPartitionCapacity() > 0 ? memProps.getEpisodicPartitionCapacity() : 1_000L;
        long proceduralCap = memProps != null && memProps.getProceduralCapacity() > 0 ? memProps.getProceduralCapacity() : 1_000L;
        long workingCap = memProps != null && memProps.getWorkingCapacity() > 0 ? memProps.getWorkingCapacity() : 100L;
        long graphCap = memProps != null && memProps.getEntityGraphCapacity() > 0 ? memProps.getEntityGraphCapacity() : 50_000L;

        // Per-record active indexing & vector metadata footprint (~8% average active occupancy across hot namespaces)
        long vectorBytesPerRecord = (long) dims * Float.BYTES + 128L;
        long totalRecordCap = semanticCap + episodicCap + proceduralCap + workingCap;
        long estimatedActiveRecords = Math.max(64L, totalRecordCap / 12L);
        long vectorAndRecordBytes = estimatedActiveRecords * vectorBytesPerRecord;
        long graphBytes = Math.max(16_384L, (graphCap / 16L) * 64L);
        long baseOverheadBytes = 256L * 1024L;
        return baseOverheadBytes + vectorAndRecordBytes + graphBytes;
    }

    private static long defaultNamespaceBudgetBytes(SynapseProperties synapseProps) {
        if (synapseProps != null && synapseProps.getAuth() != null && synapseProps.getAuth().getMemory() != null) {
            long configuredBudget = synapseProps.getAuth().getMemory().getBudgetBytes();
            if (configuredBudget > 0L) {
                return configuredBudget;
            }
        }
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (maxHeap <= 0L || maxHeap == Long.MAX_VALUE) {
            return 512L * 1024L * 1024L;
        }
        return Math.max(64L * 1024L * 1024L, maxHeap / 2L);
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
            unbindNamespaceMeters(namespaceId);
            releasePooledEmbedding(namespaceId);
            closeQuietly(handle.memory);
        }
    }

    /**
     * Evicts and closes all cached namespace memory instances belonging to {@code tenantId},
     * zeroizing off-heap encrypted memory segments upon closure (ADR-0088 §8.2, Issue #1072).
     *
     * @param tenantId the tenant identifier
     * @return number of evicted namespace instances
     */
    public int evictTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return 0;
        }
        int evictedCount = 0;
        for (var entry : new java.util.ArrayList<>(cache.entrySet())) {
            String nsId = entry.getKey();
            MemoryHandle handle = entry.getValue();
            String handleTenant = handle.tenantId;
            if ((handleTenant == null || handleTenant.isBlank()) && handle.ownerAccountId != null) {
                try {
                    Account owner = catalog.getAccount(handle.ownerAccountId);
                    if (owner != null) {
                        handleTenant = owner.tenantId();
                    }
                } catch (Exception ignored) {
                }
            }
            if (tenantId.equals(handleTenant)) {
                evict(nsId);
                evictedCount++;
            }
        }
        return evictedCount;
    }

    /**
     * Resolves the on-disk directory path for a namespace given its tenant and namespaceId,
     * honoring dual-read fallback when applicable.
     */
    public Path resolveNamespaceDirectory(String tenantId, String namespaceId) {
        boolean tenantRooted = synapseProps != null
                && synapseProps.getNamespace() != null
                && synapseProps.getNamespace().isTenantRootedEnabled();
        String effectiveTenant = (tenantRooted && tenantId != null && !tenantId.isBlank()) ? tenantId : null;
        Placement placement = NamespacePathResolver.resolve(basePath(), effectiveTenant, namespaceId);
        Path dir = placement.dir();
        if (effectiveTenant != null && !Files.exists(dir)) {
            Placement flatPlacement = NamespacePathResolver.resolve(basePath(), null, namespaceId);
            if (Files.exists(flatPlacement.dir())) {
                return flatPlacement.dir();
            }
        }
        return dir;
    }

    @FunctionalInterface
    public interface DataEncryptorResolver {
        com.spectrayan.spector.memory.persist.DataEncryptor resolve(String tenantId, String namespaceId);
    }

    private volatile DataEncryptorResolver dataEncryptorResolver;

    public void setDataEncryptorResolver(DataEncryptorResolver dataEncryptorResolver) {
        this.dataEncryptorResolver = dataEncryptorResolver;
    }

    private final java.util.List<java.util.function.Consumer<String>> evictionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface NamespaceOpenListener {
        void onNamespaceOpened(String tenantId, String namespaceId, SpectorMemory memory);
    }

    private final java.util.List<NamespaceOpenListener> openListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Registers a listener to be notified when a namespace is instantiated and added to the hot cache.
     *
     * @param listener consumer receiving (tenantId, namespaceId, memory)
     */
    public void addOpenListener(NamespaceOpenListener listener) {
        if (listener != null) {
            this.openListeners.add(listener);
        }
    }

    private void notifyOpen(String tenantId, String namespaceId, SpectorMemory memory) {
        for (var listener : openListeners) {
            try {
                listener.onNamespaceOpened(tenantId, namespaceId, memory);
            } catch (Exception e) {
                log.warn("[NamespaceResolver] Open listener error for ns={}: {}", namespaceId, e.getMessage());
            }
        }
    }

    /**
     * Registers a listener to be notified when a namespace is evicted from the hot cache.
     *
     * @param listener consumer receiving the evicted namespaceId
     */
    public void addEvictionListener(java.util.function.Consumer<String> listener) {
        if (listener != null) {
            this.evictionListeners.add(listener);
        }
    }

    private void notifyEviction(String namespaceId) {
        for (var listener : evictionListeners) {
            try {
                listener.accept(namespaceId);
            } catch (Exception e) {
                log.warn("[NamespaceResolver] Eviction listener error for ns={}: {}", namespaceId, e.getMessage());
            }
        }
    }

    /**
     * Drops this namespace's reference to its pooled embedding provider.
     *
     * <p>Called on every path that removes a namespace from the cache. Without it the pool would only
     * ever grow, and a namespace evicted under the hot cap would hold a provider forever.</p>
     */
    private void releasePooledEmbedding(String namespaceId) {
        var fingerprint = fingerprintsByNamespace.remove(namespaceId);
        if (fingerprint != null && providerPool.release(fingerprint)) {
            // Last reference gone, so the pipeline wrapping it is dead too.
            pipelinesByFingerprint.remove(fingerprint);
        }
        var llmFingerprint = llmFingerprintsByNamespace.remove(namespaceId);
        if (llmFingerprint != null) {
            llmPool.release(llmFingerprint);
        }
    }

    private void unbindNamespaceMeters(String namespaceId) {
        if (namespaceId != null) {
            notifyEviction(namespaceId);
            if (meterRegistry != null) {
                java.util.List<io.micrometer.core.instrument.Meter> toRemove = meterRegistry.getMeters().stream()
                        .filter(meter -> namespaceId.equals(meter.getId().getTag("spector.namespace")))
                        .toList();
                for (var meter : toRemove) {
                    meterRegistry.remove(meter);
                }
                log.debug("[NamespaceResolver] Unbound {} meters for namespace: ns={}", toRemove.size(), namespaceId);
            }
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

        while (true) {
            if (closed.get()) {
                throw new IllegalStateException("NamespaceResolver is closed");
            }

            java.util.List<SpectorMemory> toClose = new java.util.ArrayList<>(2);
            InFlightOpen joinOpen = null;
            InFlightOpen waitForCapacityOpen = null;
            InFlightOpen leaderOpen = null;

            coldPathLock.lock();
            try {
                handle = cache.get(namespaceId);
                if (handle != null) {
                    handle.touch(accountId);
                    return handle.memory;
                }

                InFlightOpen existingOpen = inFlightOpens.get(namespaceId);
                if (existingOpen != null) {
                    if (existingOpen.isOwnedByCurrentThread()) {
                        throw new IllegalStateException(
                                "Re-entrant cold open for namespace '" + namespaceId + "' on thread '"
                                        + Thread.currentThread().getName() + "'");
                    }
                    existingOpen.associateAccount(accountId);
                    joinOpen = existingOpen;
                } else {
                    Account account = null;
                    // 1. Account-level hot cap check (ADR-0029 §2.6, §6.3, Q4)
                    if (accountId != null) {
                        account = catalog.getOrCreateAccount(accountId);
                        int maxHot = account != null && account.quotas() != null
                                ? account.quotas().maxHotNamespaces() : 0;
                        if (maxHot > 0) {
                            long currentAccountHot = countAccountHotAndInFlightLocked(accountId);
                            while (currentAccountHot >= maxHot) {
                                MemoryHandle accountEvicted = evictOldestAccountUnleasedLocked(accountId);
                                if (accountEvicted != null) {
                                    toClose.add(accountEvicted.memory);
                                    currentAccountHot--;
                                    continue;
                                }
                                InFlightOpen inFlightAccountOpen = findAnyAccountInFlightOpenLocked(accountId);
                                if (inFlightAccountOpen != null) {
                                    waitForCapacityOpen = inFlightAccountOpen;
                                    break;
                                }
                                throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceHotCapExceededException(
                                        accountId, maxHot);
                            }
                        }
                    }

                    // 2. Process-wide instance cap check (ADR-0029 §6.3)
                    if (waitForCapacityOpen == null) {
                        while (cache.size() + countNonCurrentThreadInFlightLocked() >= maxInstances) {
                            MemoryHandle processEvicted = evictOldestProcessUnleasedLocked();
                            if (processEvicted != null) {
                                toClose.add(processEvicted.memory);
                                continue;
                            }
                            InFlightOpen anyInFlight = inFlightOpens.values().stream()
                                    .filter(o -> !o.isOwnedByCurrentThread())
                                    .findFirst()
                                    .orElse(null);
                            if (anyInFlight != null) {
                                waitForCapacityOpen = anyInFlight;
                                break;
                            }
                            throw new com.spectrayan.spector.synapse.catalog.exception.NamespaceHotCapExceededException(
                                    "process", maxInstances);
                        }
                    }

                    if (waitForCapacityOpen == null) {
                        leaderOpen = new InFlightOpen(namespaceId, ownerAccountId, accountId, account);
                        inFlightOpens.put(namespaceId, leaderOpen);
                    }
                }
            } finally {
                coldPathLock.unlock();
                for (SpectorMemory evictedMemory : toClose) {
                    closeQuietly(evictedMemory);
                }
            }

            if (joinOpen != null) {
                MemoryHandle built = joinOpen.awaitHandle();
                built.touch(accountId);
                return built.memory;
            }

            if (waitForCapacityOpen != null) {
                waitForCapacityOpen.awaitCompletionQuietly();
                continue;
            }

            // Leader cold-open path executes outside coldPathLock so distinct namespaces open in parallel
            MemoryHandle builtHandle;
            String tenantId = null;
            String resolvedOwnerTenant = null;
            SpectorMemory instance = null;
            try {
                boolean tenantRooted = synapseProps != null
                        && synapseProps.getNamespace() != null
                        && synapseProps.getNamespace().isTenantRootedEnabled();
                try {
                    resolvedOwnerTenant = placementTenantIdFor(
                            namespaceId, ownerAccountId, accountId, leaderOpen.initialAccount);
                } catch (Exception e) {
                    if (tenantRooted) {
                        throw e;
                    }
                }
                tenantId = tenantRooted ? resolvedOwnerTenant : null;
                int prevDepth = buildingDepth.get();
                buildingDepth.set(prevDepth + 1);
                try {
                    instance = buildInstance(tenantId, namespaceId, ownerAccountId != null ? ownerAccountId : accountId);
                } finally {
                    if (prevDepth == 0) {
                        buildingDepth.remove();
                    } else {
                        buildingDepth.set(prevDepth);
                    }
                }

                coldPathLock.lock();
                try {
                    inFlightOpens.remove(namespaceId, leaderOpen);
                    if (closed.get()) {
                        closeQuietly(instance);
                        throw new IllegalStateException("NamespaceResolver closed during namespace build");
                    }
                    builtHandle = new MemoryHandle(namespaceId, ownerAccountId, resolvedOwnerTenant, accountId, instance);
                    for (String acc : leaderOpen.accessingAccounts) {
                        builtHandle.touch(acc);
                    }
                    cache.put(namespaceId, builtHandle);
                } finally {
                    coldPathLock.unlock();
                }

                notifyOpen(tenantId, namespaceId, instance);
                if (meterRegistry != null) {
                    String metricTenantId = resolvedOwnerTenant != null && !resolvedOwnerTenant.isBlank()
                            ? resolvedOwnerTenant : "default";
                    new com.spectrayan.spector.metrics.observation.SpectorMemoryGauges(
                            builtHandle.memory, namespaceId, metricTenantId)
                            .bindTo(meterRegistry);
                }
                leaderOpen.future.complete(builtHandle);
                builtHandle.touch(accountId);
                return builtHandle.memory;
            } catch (Throwable t) {
                coldPathLock.lock();
                try {
                    inFlightOpens.remove(namespaceId, leaderOpen);
                } finally {
                    coldPathLock.unlock();
                }
                leaderOpen.future.completeExceptionally(t);
                if (t instanceof RuntimeException re) {
                    throw re;
                }
                if (t instanceof Error err) {
                    throw err;
                }
                throw new RuntimeException(t);
            }
        }
    }

    private long countNonCurrentThreadInFlightLocked() {
        return inFlightOpens.values().stream()
                .filter(o -> !o.isOwnedByCurrentThread())
                .count();
    }

    private long countAccountHotAndInFlightLocked(String accountId) {
        long cachedCount = cache.values().stream()
                .filter(h -> h.associatedWith(accountId))
                .count();
        long inFlightCount = inFlightOpens.values().stream()
                .filter(o -> !o.isOwnedByCurrentThread() && o.associatedWith(accountId))
                .count();
        return cachedCount + inFlightCount;
    }

    private InFlightOpen findAnyAccountInFlightOpenLocked(String accountId) {
        for (InFlightOpen open : inFlightOpens.values()) {
            if (!open.isOwnedByCurrentThread() && open.associatedWith(accountId)) {
                return open;
            }
        }
        return null;
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
            for (InFlightOpen open : inFlightOpens.values()) {
                open.future.cancel(false);
            }
            inFlightOpens.clear();
            for (MemoryHandle handle : cache.values()) {
                closeQuietly(handle.memory);
            }
            cache.clear();
            // Close pooled providers only after every memory using them is closed, and only here where
            // no namespace will be served again.
            fingerprintsByNamespace.clear();
            pipelinesByFingerprint.clear();
            llmFingerprintsByNamespace.clear();
            try {
                providerPool.close();
            } catch (Exception e) {
                log.warn("[NamespaceResolver] error closing the embedding provider pool: {}", e.getMessage());
            }
            try {
                llmPool.close();
            } catch (Exception e) {
                log.warn("[NamespaceResolver] error closing the LLM provider pool: {}", e.getMessage());
            }
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

    /** Returns a snapshot of all cached namespace IDs and their SpectorMemory instances. */
    public java.util.Map<String, SpectorMemory> cachedEntries() {
        var snapshot = new java.util.LinkedHashMap<String, SpectorMemory>();
        cache.forEach((nsId, handle) -> snapshot.put(nsId, handle.memory));
        return java.util.Collections.unmodifiableMap(snapshot);
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
            runtimeInitLock.lock();
            try {
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
                    // Scope cache keys to the embedder's model identity. This hoisted provider is shared
                    // by every namespace in the process today, so an unscoped key would become a
                    // cross-model collision the moment namespaces can differ in model (see spec group 3).
                    this.hoistedEmbeddingProvider = com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider.wrap(
                            rawEmbedder, globalCacheManager,
                            com.spectrayan.spector.provider.ProviderFingerprint.ofProvider(rawEmbedder));

                    boolean sequential = spectorProps != null
                            && spectorProps.provider() != null
                            && spectorProps.provider().getEmbedding() != null
                            && spectorProps.provider().getEmbedding().isSequential();
                    this.hoistedPipeline = new ParallelEmbeddingPipeline(this.hoistedEmbeddingProvider, sequential);
                }
            } finally {
                runtimeInitLock.unlock();
            }
        }
    }

    private void ensureSpectorRuntime(EmbeddingProvider embedder, com.spectrayan.spector.config.SpectorProperties spectorProps) {
        if (this.runtime == null && embedder != null) {
            runtimeInitLock.lock();
            try {
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
            } finally {
                runtimeInitLock.unlock();
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
    public String placementTenantIdFor(String namespaceId, String ownerAccountId,
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

        // An independent deep copy per namespace. This is mutated just below, and until
        // SpectorConfigProperties.toSpectorProperties() began deep-copying, that mutation wrote straight
        // into the shared Spring bean — so the first namespace opened decided the entity-extraction mode
        // for every namespace opened after it.
        var spectorProps = synapseProps.toSpectorProperties();

        // Resolve the LLM before the entity-extraction decision below, which branches on whether an LLM is
        // available. Deciding from the process-wide bean and then handing the namespace a different LLM
        // would pick an extractor for a provider the namespace does not use.
        LlmProvider textGen = resolveLlmFor(tenantId, namespaceId);

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

        // Resolve this namespace's embedding provider *before* attach. The vector store is dimensioned
        // when the memory is built, so a provider chosen afterwards (as the open-listener overlay does)
        // is already too late to change dimensionality — which is why the config schema has to mark
        // dimensions as REBUILD.
        ResolvedEmbedding resolved = resolveEmbeddingFor(tenantId, namespaceId, spectorProps);

        // Record the embedder identity on first open and compare it on every later open. Swapping the
        // configured model for another at the same width would otherwise make every stored vector
        // incomparable with every new one, with no error anywhere: all DIMENSIONS_MISMATCH checks in the
        // index and quantizer layers are width-only, so a same-width swap passes all of them.
        reconcileEmbeddingIdentity(markerFile, jsonMapper, namespaceId, resolved);

        MemoryProperties memory = synapseProps.getMemory();
        SalienceProfileProvider salience = salienceProvider != null ? salienceProvider.getIfAvailable() : null;
        org.springframework.cache.CacheManager springCacheManager = cacheManagerProvider != null
                ? cacheManagerProvider.getIfAvailable() : null;
        String encryptorTenantId = tenantId;
        if (encryptorTenantId == null || encryptorTenantId.isBlank()) {
            try {
                encryptorTenantId = placementTenantIdFor(namespaceId, ownerAccountId, ownerAccountId, null);
            } catch (Exception ignored) {
            }
        }
        com.spectrayan.spector.memory.persist.DataEncryptor resolvedByokEncryptor = null;
        DataEncryptorResolver resolver = this.dataEncryptorResolver;
        if (resolver != null && encryptorTenantId != null && !encryptorTenantId.isBlank()) {
            resolvedByokEncryptor = resolver.resolve(encryptorTenantId, namespaceId);
        }
        com.spectrayan.spector.memory.persist.DataEncryptor encryptor =
                (resolvedByokEncryptor != null && resolvedByokEncryptor.isEnabled())
                        ? resolvedByokEncryptor
                        : (encryptorProvider != null
                                ? encryptorProvider.getIfAvailable(() -> com.spectrayan.spector.memory.persist.DataEncryptor.NOOP)
                                : com.spectrayan.spector.memory.persist.DataEncryptor.NOOP);
        ObjectMapper mapper = jsonMapper;

        io.micrometer.observation.ObservationRegistry obsRegistry = observationRegistryProvider != null
                ? observationRegistryProvider.getIfAvailable() : null;
        com.spectrayan.spector.config.ObservabilityConfig obsConfig = observabilityConfigProvider != null
                ? observabilityConfigProvider.getIfAvailable() : null;
        org.quartz.Scheduler springQuartz = quartzSchedulerProvider != null
                ? quartzSchedulerProvider.getIfAvailable() : null;

        final Path effectiveDir = dir;
        final com.spectrayan.spector.config.SpectorProperties effectiveProps = spectorProps;
        SpectorMemory built = runtime.attach(namespaceId, builder -> {
            // Re-seed from *this* namespace's snapshot. SpectorRuntime seeds the builder from the
            // properties of whichever namespace bound first in the process, so without this every later
            // namespace silently inherits the first one's memory, recall, graph and entity-extraction
            // configuration — including the extraction mode decided just above from this namespace's own
            // LLM availability.
            //
            // Safe to call here: fromProperties assigns properties and derives recall options, chunk
            // config, ICNU weights and the graph scoring policy. It touches no provider, pathway,
            // persistence or cache state, so nothing SpectorRuntime.attach set before the customizer is
            // clobbered — except namespaceId, which fromProperties re-reads from the properties and which
            // attach sets *after* its own fromProperties call. Restore it explicitly; getting this wrong
            // would open the namespace under the configured default id instead of its own.
            builder.fromProperties(effectiveProps);
            builder.namespaceId(namespaceId);

            // Override the runtime's process-default embedder when this namespace resolved to its own
            // configuration. SpectorRuntime.attach applies its single embeddingProvider field before the
            // customizer runs, and the builder setters are last-write-wins, so setting them here wins.
            //
            // The pipeline must be overridden alongside the provider: a ParallelEmbeddingPipeline wrapping
            // the process-default embedder would quietly defeat the whole change, since ingestion embeds
            // through the pipeline rather than the provider.
            if (resolved.isOverride()) {
                builder.embeddingProvider(resolved.provider());
                builder.parallelEmbeddingPipeline(resolved.pipeline());
            }

            builder.persistence(effectiveDir);
            if (encryptor != null && encryptor.isEnabled()) {
                builder.dataEncryptor(encryptor);
            }
            // Legal hold and cluster write fencing are enforced inside the engine, not only at the API boundary.
            // Record mutations and deletions reach the engine through REST endpoints, MCP tools, the embedded Java API,
            // and background pathways; a check on any one boundary alone is not enforcement.
            java.util.List<com.spectrayan.spector.memory.policy.MutationPolicy> policies =
                    new java.util.ArrayList<>(2);
            if (ownerAccountId != null && !ownerAccountId.isBlank()) {
                policies.add(new com.spectrayan.spector.synapse.catalog.CatalogMutationPolicy(catalog, ownerAccountId));
            } else {
                log.warn("[NamespaceResolver] namespace {} has no owner account — legal hold cannot be "
                        + "enforced on its deletions", namespaceId);
            }
            policies.add(new com.spectrayan.spector.memory.policy.FencingMutationPolicy(
                    () -> resolveCurrentFenceEpoch(tenantId, namespaceId)));
            builder.mutationPolicy(policies.size() == 1
                    ? policies.getFirst()
                    : new com.spectrayan.spector.memory.policy.CompositeMutationPolicy(policies));
            if (textGen != null) {
                builder.llmProvider(textGen);
            }
            if (salience != null) {
                if (salience instanceof com.spectrayan.spector.synapse.config.SynapseSalienceProvider ssp) {
                    com.spectrayan.spector.memory.model.SalienceProfile configured = ssp.configuredProfile();
                    if (configured != null) {
                        builder.salienceProfile(configured);
                    }
                    builder.salienceProfileProvider(ssp::configuredProfile);
                } else {
                    builder.salienceProfileProvider(salience);
                }
            }
            // Derived from this namespace's dense embedder, not the process default: a sparse or token
            // provider deriving from a different model than the dense vectors were produced with would
            // score against an unrelated space.
            if (memory != null && memory.isSpladeEnabled()) {
                builder.sparseEmbeddingProvider(new DenseDerivedSparseProvider(resolved.provider()));
            }
            if (memory != null && memory.isColbertEnabled()) {
                builder.tokenEmbeddingProvider(new DenseDerivedTokenProvider(resolved.provider()));
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
            built = new com.spectrayan.spector.metrics.ObservedSpectorMemory(built, obsRegistry, obsConfig, meterRegistry);
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

    /** Marker field recording the embedding provider type a namespace's vectors were produced with. */
    static final String MARKER_EMBEDDING_PROVIDER = "embeddingProvider";

    /** Marker field recording the embedding model a namespace's vectors were produced with. */
    static final String MARKER_EMBEDDING_MODEL = "embeddingModel";

    /** Marker field recording the dimensionality a namespace's vectors were produced at. */
    static final String MARKER_EMBEDDING_DIMENSIONS = "embeddingDimensions";

    /**
     * Records the resolved embedder identity in the namespace marker, or refuses to open a namespace
     * whose recorded identity disagrees with what is now configured.
     *
     * <p>Follows the precedent set by the layout check in {@code buildInstance}: recorded on create,
     * compared on open, refused on mismatch. That is the right shape for this problem too, and it needs no
     * kernel change, no new bundle region and no format version bump.</p>
     *
     * <p>A marker written before this field existed has no recorded identity. Such a namespace still
     * opens, and the absence is reported rather than guessed — stamping the currently-configured model
     * onto an existing corpus would assert something unknown to be true, which is worse than admitting
     * ignorance. The identity is backfilled so the next open can check it.</p>
     */
    private void reconcileEmbeddingIdentity(Path markerFile, ObjectMapper jsonMapper, String namespaceId,
            ResolvedEmbedding resolved) {
        EmbeddingProvider provider = resolved.provider();
        if (provider == null) {
            return;
        }
        String model;
        int dims;
        try {
            model = provider.modelName();
            dims = provider.dimensions();
        } catch (RuntimeException e) {
            // An offline provider cannot report its identity. Recording "unknown" would poison the marker
            // for every later open, so skip rather than guess.
            log.warn("[NamespaceResolver] ns={} could not report its embedding identity; skipping the "
                    + "marker identity check: {}", namespaceId, e.getMessage());
            return;
        }
        String providerType = resolved.fingerprint() != null ? resolved.fingerprint().type() : "default";
        if (model == null || model.isBlank()) {
            return;
        }

        try {
            JsonNode node = Files.exists(markerFile) ? jsonMapper.readTree(markerFile.toFile()) : null;
            if (node == null) {
                return;
            }
            String recordedModel = node.hasNonNull(MARKER_EMBEDDING_MODEL)
                    ? node.get(MARKER_EMBEDDING_MODEL).asText() : null;

            if (recordedModel == null || recordedModel.isBlank()) {
                java.util.OptionalInt storedDims = com.spectrayan.spector.memory.persist.StoreEmbeddingIdentity
                        .inferStoredDimensions(markerFile.getParent(), null);
                if (storedDims.isPresent() && dims > 0 && storedDims.getAsInt() != dims) {
                    throw new com.spectrayan.spector.commons.error.SpectorConfigException(
                            com.spectrayan.spector.commons.error.ErrorCode.EMBEDDING_FINGERPRINT_MISMATCH,
                            namespaceId,
                            "unknown (" + storedDims.getAsInt() + "d)",
                            com.spectrayan.spector.memory.persist.StoreEmbeddingIdentity
                                    .formatFingerprint(providerType, model, dims),
                            markerFile.getParent());
                }
                // Pre-change marker, or a fresh one written moments ago by the block above.
                writeEmbeddingIdentity(markerFile, jsonMapper, node, providerType, model, dims);
                log.info("[NamespaceResolver] ns={} recorded embedding identity provider={} model={} dims={}",
                        namespaceId, providerType, model, dims);
                return;
            }

            int recordedDims = node.hasNonNull(MARKER_EMBEDDING_DIMENSIONS)
                    ? node.get(MARKER_EMBEDDING_DIMENSIONS).asInt() : -1;
            boolean modelChanged = !recordedModel.equals(model);
            boolean dimsChanged = recordedDims > 0 && recordedDims != dims;
            if (modelChanged || dimsChanged) {
                throw new IllegalStateException(String.format(
                        "Namespace '%s' was written with embedding model '%s' at %s dimensions, but is now "
                                + "configured for model '%s' at %d dimensions. Refusing to open it: every stored "
                                + "vector was produced by the recorded model, so mixing in vectors from a "
                                + "different one makes all similarity scores between them meaningless — and "
                                + "nothing downstream would report it, because the dimension checks in the index "
                                + "and quantizer layers compare width only. Either restore the recorded model in "
                                + "configuration, or re-embed the namespace against the new one.",
                        namespaceId, recordedModel,
                        recordedDims > 0 ? String.valueOf(recordedDims) : "an unrecorded number of",
                        model, dims));
            }
        } catch (IOException e) {
            log.warn("[NamespaceResolver] ns={} could not read the marker for the embedding identity "
                    + "check: {}", namespaceId, e.getMessage());
        }
    }

    private void writeEmbeddingIdentity(Path markerFile, ObjectMapper jsonMapper, JsonNode existing,
            String providerType, String model, int dims) {
        try {
            Map<String, Object> markerData = jsonMapper.convertValue(existing,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
            markerData.put(MARKER_EMBEDDING_PROVIDER, providerType);
            markerData.put(MARKER_EMBEDDING_MODEL, model);
            markerData.put(MARKER_EMBEDDING_DIMENSIONS, dims);
            jsonMapper.writeValue(markerFile.toFile(), markerData);
        } catch (IOException | IllegalArgumentException e) {
            // Not fatal: failing to *record* the identity loses a future check, whereas refusing to open
            // would deny service over a bookkeeping write. The next open retries.
            log.warn("[NamespaceResolver] could not record the embedding identity in {}: {}",
                    markerFile, e.getMessage());
        }
    }

    /**
     * The embedding provider and pipeline a namespace will use.
     *
     * @param provider    the dense embedder
     * @param pipeline    the pipeline wrapping {@code provider}
     * @param fingerprint the pooled configuration identity, or {@code null} for the process default
     */
    record ResolvedEmbedding(EmbeddingProvider provider,
                             ParallelEmbeddingPipeline pipeline,
                             com.spectrayan.spector.provider.ProviderFingerprint fingerprint) {

        /** @return whether this namespace overrides the runtime default and must set it on the builder. */
        boolean isOverride() {
            return fingerprint != null;
        }
    }

    /**
     * Resolves the LLM provider for one namespace.
     *
     * <p>Returns the process-wide bean when no resolver is installed, when the resolver declines, or when the
     * resolved configuration names the provider already active — so the common single-model deployment is
     * unchanged and takes no pool reference.</p>
     *
     * <p>Otherwise takes a reference on the LLM pool. This is the half of scoped LLM overrides that
     * {@code ConfigApplicator} deliberately stopped doing through the global {@code ProviderRegistry}: the
     * registry holds one active-generation name, so activating a tenant's model there changed the LLM for
     * every namespace in the process. Without this method a scoped override took effect nowhere.</p>
     */
    private LlmProvider resolveLlmFor(String tenantId, String namespaceId) {
        LlmProvider processDefault = textGenProvider != null ? textGenProvider.getIfAvailable() : null;

        EmbeddingConfigResolver resolver = this.llmConfigResolver;
        if (resolver == null) {
            return processDefault;
        }

        com.spectrayan.spector.provider.ProviderConfig config;
        try {
            config = resolver.resolve(tenantId, namespaceId);
        } catch (RuntimeException e) {
            // A config plane that cannot answer must not stop a namespace opening. Failing here would make an
            // unrelated database hiccup look like memory corruption.
            log.warn("[NamespaceResolver] could not resolve LLM config for ns={}; using the process-wide "
                    + "provider: {}", namespaceId, e.getMessage());
            return processDefault;
        }
        if (config == null) {
            return processDefault;
        }

        var fingerprint = com.spectrayan.spector.provider.ProviderFingerprint.of(config);
        try {
            LlmProvider pooled = llmPool.acquire(fingerprint, fp -> createLlmProvider(config, fp));
            llmFingerprintsByNamespace.put(namespaceId, fingerprint);
            log.info("[NamespaceResolver] ns={} uses its own LLM configuration {} (pool holds {} distinct)",
                    namespaceId, fingerprint.toLogString(), llmPool.distinctConfigurations());
            return pooled;
        } catch (RuntimeException e) {
            // Falling back is right for the LLM but would be wrong for the embedder. An LLM is used for
            // entity extraction and generation, where degrading to the process default is a quality change;
            // an embedder decides the vector space, where the same fallback would silently corrupt recall.
            log.warn("[NamespaceResolver] could not build the configured LLM for ns={} ({}); falling back to "
                    + "the process-wide provider: {}", namespaceId, fingerprint.toLogString(), e.getMessage());
            return processDefault;
        }
    }

    /**
     * Builds an LLM provider from a resolved configuration via the {@link ProviderFactory} SPI.
     */
    private LlmProvider createLlmProvider(com.spectrayan.spector.provider.ProviderConfig config,
            com.spectrayan.spector.provider.ProviderFingerprint fingerprint) {
        for (com.spectrayan.spector.provider.ProviderFactory factory
                : java.util.ServiceLoader.load(com.spectrayan.spector.provider.ProviderFactory.class)) {
            if (factory.name().equalsIgnoreCase(config.type()) && factory.supportsGeneration()) {
                return factory.createGenerationProvider(config).orElseThrow(() -> new IllegalStateException(
                        "Provider factory '" + factory.name() + "' produced no LLM provider for "
                                + fingerprint.toLogString()));
            }
        }
        throw new IllegalStateException(
                "No LLM provider factory found for type '" + config.type() + "'");
    }

    /**
     * Resolves the embedding provider for one namespace.
     *
     * <p>Returns the process-default hoisted provider when no resolver is installed, when the resolver
     * declines, or when the resolved configuration matches the default — so the common single-model
     * deployment keeps exactly one provider and one pipeline, as before.</p>
     *
     * <p>Otherwise takes a reference on the pool for the resolved configuration. Identical configurations
     * across namespaces share one provider instance; that sharing is the difference between this design
     * and a naive per-namespace one, and it is what keeps the client count proportional to distinct
     * configurations rather than to namespace count.</p>
     */
    private ResolvedEmbedding resolveEmbeddingFor(String tenantId, String namespaceId,
            com.spectrayan.spector.config.SpectorProperties spectorProps) {
        EmbeddingProvider processDefault = hoistedEmbeddingProvider != null
                ? hoistedEmbeddingProvider
                : embedderProvider.getIfAvailable();
        ResolvedEmbedding fallback = new ResolvedEmbedding(processDefault, hoistedPipeline, null);

        EmbeddingConfigResolver resolver = this.embeddingConfigResolver;
        if (resolver == null) {
            return fallback;
        }

        com.spectrayan.spector.provider.ProviderConfig config;
        try {
            config = resolver.resolve(tenantId, namespaceId);
        } catch (RuntimeException e) {
            // A config plane that cannot answer must not stop a namespace opening on the default
            // embedder. Failing here would make an unrelated database hiccup look like memory corruption.
            log.warn("[NamespaceResolver] could not resolve embedding config for ns={}; using the process "
                    + "default embedder: {}", namespaceId, e.getMessage());
            return fallback;
        }
        if (config == null) {
            return fallback;
        }

        var fingerprint = com.spectrayan.spector.provider.ProviderFingerprint.of(config);
        var defaultFingerprint = com.spectrayan.spector.provider.ProviderFingerprint.ofProvider(processDefault);
        if (defaultFingerprint != null
                && fingerprint.model().equals(defaultFingerprint.model())
                && fingerprint.dimensions() == defaultFingerprint.dimensions()
                && fingerprint.secretDigest().equals(defaultFingerprint.secretDigest())) {
            // Same model at the same width on the same credential: the resolved config describes what the
            // process default already is, so pooling a second instance would buy nothing.
            log.debug("[NamespaceResolver] ns={} resolved to the process-default embedding config", namespaceId);
            return fallback;
        }

        EmbeddingProvider pooled = providerPool.acquire(fingerprint, fp -> createEmbeddingProvider(config, fp));
        fingerprintsByNamespace.put(namespaceId, fingerprint);

        boolean sequential = spectorProps != null
                && spectorProps.provider() != null
                && spectorProps.provider().getEmbedding() != null
                && spectorProps.provider().getEmbedding().isSequential();
        ParallelEmbeddingPipeline pipeline = pipelinesByFingerprint.computeIfAbsent(
                fingerprint, fp -> new ParallelEmbeddingPipeline(pooled, sequential));

        log.info("[NamespaceResolver] ns={} uses its own embedding configuration {} (pool holds {} distinct)",
                namespaceId, fingerprint.toLogString(), providerPool.distinctConfigurations());
        return new ResolvedEmbedding(pooled, pipeline, fingerprint);
    }

    /**
     * Builds an embedding provider from a resolved configuration via the {@link ProviderFactory} SPI.
     *
     * <p>Wrapped with the fingerprint-scoped cache by {@code AbstractProviderFactory}, so two namespaces
     * on different models cannot read each other's cached vectors.</p>
     */
    private EmbeddingProvider createEmbeddingProvider(com.spectrayan.spector.provider.ProviderConfig config,
            com.spectrayan.spector.provider.ProviderFingerprint fingerprint) {
        for (com.spectrayan.spector.provider.ProviderFactory factory
                : java.util.ServiceLoader.load(com.spectrayan.spector.provider.ProviderFactory.class)) {
            if (factory.name().equalsIgnoreCase(config.type()) && factory.supportsEmbedding()) {
                return factory.createEmbeddingProvider(config).orElseThrow(() -> new IllegalStateException(
                        "Provider factory '" + factory.name() + "' produced no embedding provider for "
                                + fingerprint.toLogString()));
            }
        }
        // Refusing is correct: silently substituting the process default would give this namespace a
        // different model than its configuration asks for, which is the defect this spec removes.
        throw new IllegalStateException(String.format(
                "No embedding provider factory found for type '%s' (configuration %s). The namespace "
                        + "configured this provider, so opening it on a different one would silently embed "
                        + "into the wrong vector space.",
                config.type(), fingerprint.toLogString()));
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
                unbindNamespaceMeters(oldestKey);
                releasePooledEmbedding(oldestKey);
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
                unbindNamespaceMeters(oldestKey);
                releasePooledEmbedding(oldestKey);
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
        private final String tenantId;
        private final java.util.Set<String> accessingAccounts = ConcurrentHashMap.newKeySet();
        private final SpectorMemory memory;
        private volatile long lastAccessNanos;

        MemoryHandle(SpectorMemory memory) {
            this(null, null, null, null, memory);
        }

        MemoryHandle(String namespaceId, String ownerAccountId, String initialAccountId, SpectorMemory memory) {
            this(namespaceId, ownerAccountId, null, initialAccountId, memory);
        }

        MemoryHandle(String namespaceId, String ownerAccountId, String tenantId, String initialAccountId, SpectorMemory memory) {
            this.namespaceId = namespaceId;
            this.ownerAccountId = ownerAccountId != null ? ownerAccountId : initialAccountId;
            this.tenantId = tenantId;
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

    /** Tracks an in-flight per-namespace cold open for single-flight coordination. */
    private static final class InFlightOpen {
        private static final long OPEN_AWAIT_TIMEOUT_SECONDS = 60L;

        private final String namespaceId;
        private final String ownerAccountId;
        private final Account initialAccount;
        private final Thread ownerThread = Thread.currentThread();
        private final java.util.Set<String> accessingAccounts = ConcurrentHashMap.newKeySet();
        private final CompletableFuture<MemoryHandle> future = new CompletableFuture<>();

        InFlightOpen(String namespaceId, String ownerAccountId, String initialAccountId, Account initialAccount) {
            this.namespaceId = namespaceId;
            this.ownerAccountId = ownerAccountId != null ? ownerAccountId : initialAccountId;
            this.initialAccount = initialAccount;
            if (initialAccountId != null) {
                this.accessingAccounts.add(initialAccountId);
            }
        }

        boolean isOwnedByCurrentThread() {
            return Thread.currentThread() == ownerThread;
        }

        void associateAccount(String accountId) {
            if (accountId != null && accessingAccounts.size() < MemoryHandle.MAX_ACCESSING_ACCOUNTS) {
                accessingAccounts.add(accountId);
            }
        }

        boolean associatedWith(String accountId) {
            if (accountId == null) return false;
            return accountId.equals(ownerAccountId) || accessingAccounts.contains(accountId);
        }

        MemoryHandle awaitHandle() {
            if (isOwnedByCurrentThread() && !future.isDone()) {
                throw new IllegalStateException(
                        "Re-entrant cold open for namespace '" + namespaceId + "' on thread '"
                                + ownerThread.getName() + "'");
            }
            try {
                return future.get(OPEN_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while awaiting cold open for namespace '" + namespaceId + "'", e);
            } catch (TimeoutException e) {
                throw new IllegalStateException(
                        "Timed out awaiting cold open for namespace '" + namespaceId + "' after "
                                + OPEN_AWAIT_TIMEOUT_SECONDS + "s", e);
            } catch (ExecutionException | CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw new RuntimeException(cause != null ? cause : e);
            }
        }

        void awaitCompletionQuietly() {
            if (isOwnedByCurrentThread() && !future.isDone()) {
                return;
            }
            try {
                future.get(OPEN_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
            }
        }
    }
}
