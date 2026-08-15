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
package com.spectrayan.spector.connector.sink;

import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.ingestion.IngestionTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Registry that manages isolated, tenant-specific {@link SpectorMemory} instances
 * with capacity-capped LRU eviction.
 *
 * <h3>Capacity-Capped JIT Pager</h3>
 * <p>The hot pool has a hard cap ({@code maxActive}) on simultaneously loaded
 * tenant memory instances. When a new tenant is loaded and the pool is at
 * capacity, the least-recently-used tenant is evicted (its {@code SpectorMemory}
 * is closed and mmap regions are released). This prevents mmap exhaustion
 * ({@code vm.max_map_count}) at scale.</p>
 *
 * <h3>Dual Eviction Strategy</h3>
 * <ul>
 *   <li><b>Pressure eviction:</b> Evicts LRU tenant when pool reaches {@code maxActive}.
 *       Triggered synchronously on load.</li>
 *   <li><b>Idle eviction:</b> Evicts tenants that have been inactive beyond
 *       {@code idleEvictionMs}. Triggered by periodic {@link #evictIdle()} calls.</li>
 * </ul>
 *
 * <h3>Resource Limits</h3>
 * <p>Each tenant has a configurable {@link TenantResourceConfig} controlling
 * maximum memories, segment size, ingestion rate, and idle eviction time.
 * By default, exceeding a limit logs a warning (soft enforcement).
 * Set {@code hardEnforce} to true per-tenant for HTTP 429 rejection.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses a {@link ReentrantReadWriteLock} to protect the access-ordered
 * {@link LinkedHashMap} hot pool. Read operations (get) acquire the read lock;
 * mutations (put, evict) acquire the write lock.</p>
 */
public final class TenantMemoryRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TenantMemoryRegistry.class);

    /** Default maximum number of simultaneously active (mmap'd) tenant workspaces. */
    public static final int DEFAULT_MAX_ACTIVE = 10_000;

    private final Path namespacesRoot;
    private final EmbeddingProvider embeddingProvider;
    private final int dimensions;
    private final int maxActive;
    private final boolean sharded;

    /**
     * Optional customizer applied to every per-user namespace builder.
     * Used to propagate LLM entity extraction, tag extraction, SPLADE/ColBERT
     * providers, and other cognitive features from the global configuration.
     */
    private volatile BiConsumer<SpectorMemoryBuilder, String> memoryBuilderCustomizer;

    /**
     * Optional post-load hook invoked after a namespace memory is created and loaded.
     * Used by the management layer to apply salience profiles to lazily-created namespaces.
     * The BiConsumer receives (SpectorMemory, compoundKey) where compoundKey is tenantId:namespaceId.
     */
    private volatile BiConsumer<SpectorMemory, String> postLoadHook;

    /** Optional namespace path resolver for per-user isolation (set by management layer). */
    private volatile NamespacePathResolver namespacePathResolver;

    /**
     * Hot pool: access-ordered LinkedHashMap for LRU tracking.
     * The eldest entry (least recently accessed) is always first.
     * Protected by {@link #poolLock}.
     */
    private final LinkedHashMap<String, LeasedMemory> hotPool;

    /** Read-write lock protecting the hot pool. */
    private final ReentrantReadWriteLock poolLock = new ReentrantReadWriteLock();

    /** Per-tenant resource configuration (tenantId → config). */
    private final Map<String, TenantResourceConfig> tenantConfigs = new ConcurrentHashMap<>();

    /** Global default config — applied when no per-tenant override exists. */
    private volatile TenantResourceConfig defaultConfig = TenantResourceConfig.defaults();

    /** Last access timestamp per tenant — for idle eviction. */
    private final Map<String, AtomicLong> lastAccess = new ConcurrentHashMap<>();

    /** Ingestion count in the current rate-limit window per tenant. */
    private final Map<String, AtomicLong> ingestionCounts = new ConcurrentHashMap<>();
    private volatile long rateWindowStartMs = System.currentTimeMillis();

    /** Optional cold tier rehydration hook (set by management layer). */
    private volatile ColdTierRehydratorHook coldTierHook;

    // ── Pager Metrics ──
    private final AtomicLong totalLoads = new AtomicLong(0);
    private final AtomicLong totalEvictions = new AtomicLong(0);
    private final AtomicLong pressureEvictions = new AtomicLong(0);
    private final AtomicLong idleEvictions = new AtomicLong(0);

    /**
     * L2 warm pool: metadata-only handles for evicted namespaces.
     * Allows faster re-promotion (~5-50ms) vs cold load (~50-500ms).
     * Cap is proportional to heap: min(100K, heapMB * 100).
     */
    private final LinkedHashMap<String, NamespaceHandle> warmPool;
    private final int warmPoolCap;

    /**
     * Creates a tenant memory registry rooted at the given namespaces path.
     *
     * @param namespacesRoot    root directory for tenant namespace directories
     * @param embeddingProvider shared embedding provider for all tenants
     * @param dimensions        vector dimensions for all tenant memory instances
     */
    public TenantMemoryRegistry(Path namespacesRoot, EmbeddingProvider embeddingProvider, int dimensions) {
        this(namespacesRoot, embeddingProvider, dimensions, DEFAULT_MAX_ACTIVE, false);
    }

    /**
     * Creates a tenant memory registry with a custom capacity cap.
     *
     * @param namespacesRoot    root directory for tenant namespace directories
     * @param embeddingProvider shared embedding provider for all tenants
     * @param dimensions        vector dimensions for all tenant memory instances
     * @param maxActive         maximum number of simultaneously loaded tenants
     */
    public TenantMemoryRegistry(Path namespacesRoot, EmbeddingProvider embeddingProvider,
                                int dimensions, int maxActive) {
        this(namespacesRoot, embeddingProvider, dimensions, maxActive, false);
    }

    /**
     * Creates a tenant memory registry with custom capacity and optional directory sharding.
     *
     * <p>When {@code sharded} is true, tenant directories are resolved via
     * {@link StorageLayout#namespaceDirSharded(Path, String)} which distributes
     * tenants across 65,536 (256×256) hash-bucketed subdirectories, preventing
     * {@code readdir()} degradation at scale.</p>
     *
     * @param namespacesRoot    root directory for tenant namespace directories
     * @param embeddingProvider shared embedding provider for all tenants
     * @param dimensions        vector dimensions for all tenant memory instances
     * @param maxActive         maximum number of simultaneously loaded tenants
     * @param sharded           if true, use hash-based directory sharding
     */
    public TenantMemoryRegistry(Path namespacesRoot, EmbeddingProvider embeddingProvider,
                                int dimensions, int maxActive, boolean sharded) {
        this.namespacesRoot = Objects.requireNonNull(namespacesRoot, "namespacesRoot must not be null");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider must not be null");
        this.dimensions = dimensions;
        this.maxActive = maxActive > 0 ? maxActive : DEFAULT_MAX_ACTIVE;
        this.sharded = sharded;

        // Access-ordered LinkedHashMap: eldest = least recently accessed
        this.hotPool = new LinkedHashMap<>(16, 0.75f, true);

        // Warm pool: proportional to heap — min(100K, heapMB * 100)
        long heapMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        this.warmPoolCap = (int) Math.min(100_000, heapMB * 100);
        this.warmPool = new LinkedHashMap<>(16, 0.75f, true);

        log.info("[Registry] Initialized: root={}, maxActive={}, warmCap={}, dimensions={}, sharded={}",
                namespacesRoot, this.maxActive, this.warmPoolCap, dimensions, sharded);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Configuration
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sets the global default resource config (applied to all tenants without overrides).
     */
    public void setDefaultConfig(TenantResourceConfig config) {
        this.defaultConfig = Objects.requireNonNull(config);
    }

    /**
     * Sets the cold tier rehydration hook.
     *
     * <p>When set, the registry checks cold storage before creating a new
     * empty namespace. If the tenant's data is archived, the hook rehydrates
     * it to the local path first.</p>
     *
     * @param hook the rehydration hook, or null to disable
     */
    public void setColdTierHook(ColdTierRehydratorHook hook) {
        this.coldTierHook = hook;
        log.info("[Registry] Cold tier hook {}", hook != null ? "enabled" : "disabled");
    }

    /**
     * Sets a per-tenant resource config override.
     *
     * @param tenantId the tenant ID
     * @param config   the resource config for this tenant
     */
    public void setTenantConfig(String tenantId, TenantResourceConfig config) {
        tenantConfigs.put(tenantId, config);
    }

    /**
     * Gets the effective config for a tenant (per-tenant override or global default).
     */
    public TenantResourceConfig getConfigForTenant(String tenantId) {
        return tenantConfigs.getOrDefault(tenantId, defaultConfig);
    }

    /**
     * Sets the namespace path resolver for per-user isolation routing.
     *
     * <p>When set, {@link #getMemoryForNamespace(String, String)} resolves
     * workspace paths through the resolver's deterministic layout
     * ({@code data/tenants/XX/YY/{tenantId}/namespaces/{namespaceId}/}).</p>
     *
     * @param resolver the namespace path resolver
     */
    public void setNamespacePathResolver(NamespacePathResolver resolver) {
        this.namespacePathResolver = resolver;
        log.info("[Registry] NamespacePathResolver set for per-user isolation routing");
    }

    /**
     * Sets the builder customizer that propagates global cognitive configuration
     * (entity extraction, tag extraction, SPLADE/ColBERT, encryption, etc.) to
     * per-user namespaces.
     *
     * <p>The second parameter is the tenant/namespace key (e.g., {@code tenantId}
     * or {@code tenantId:namespaceId}), enabling per-tenant encryption key
     * resolution.</p>
     *
     * @param customizer the builder customizer to apply (receives builder + tenantKey)
     */
    public void setMemoryBuilderCustomizer(BiConsumer<SpectorMemoryBuilder, String> customizer) {
        this.memoryBuilderCustomizer = customizer;
        log.info("[Registry] MemoryBuilderCustomizer set for namespace cognitive features");
    }

    /**
     * Sets a post-load hook invoked after a namespace memory is lazily created.
     *
     * <p>This is the integration point for applying salience profiles to
     * namespaces that are loaded after server startup. The hook receives
     * the newly-created {@link SpectorMemory} and the compound key
     * ({@code tenantId:namespaceId}).</p>
     *
     * @param hook the post-load hook (receives memory + compoundKey)
     */
    public void setPostLoadHook(BiConsumer<SpectorMemory, String> hook) {
        this.postLoadHook = hook;
        log.info("[Registry] PostLoadHook set for namespace salience profile resolution");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Capacity Check
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a tenant has capacity for more ingestions.
     *
     * <p>When {@code hardEnforce} is true, throws {@link TenantQuotaExceededException}.
     * Otherwise, logs a warning and allows overflow.</p>
     *
     * @param tenantId the tenant ID
     * @throws TenantQuotaExceededException if hard enforcement is enabled and quota exceeded
     */
    public void checkCapacity(String tenantId) {
        checkCapacity(tenantId, null);
    }

    /**
     * Checks capacity with optional namespace-level granularity.
     *
     * <p>When {@code namespaceId} is provided, quota is checked against the
     * compound key ({@code tenantId:namespaceId}), enabling per-user limits
     * even within the default tenant.</p>
     *
     * @param tenantId    the tenant ID
     * @param namespaceId optional namespace ID (null for tenant-level check)
     * @throws TenantQuotaExceededException if hard enforcement is enabled and quota exceeded
     */
    public void checkCapacity(String tenantId, String namespaceId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }

        String key = (namespaceId != null && !namespaceId.isBlank())
                ? tenantId + ":" + namespaceId
                : tenantId;

        TenantResourceConfig config = getConfigForTenant(tenantId);

        // Check memory count limit
        SpectorMemory memory = getFromPool(key);
        if (memory != null) {
            try {
                int count = memory.admin().index().size();
                if (count >= config.maxMemories()) {
                    String msg = String.format("Quota exceeded for '%s': %d/%d memories",
                            key, count, config.maxMemories());
                    if (config.hardEnforce()) {
                        throw new TenantQuotaExceededException(msg);
                    }
                    log.warn("[Quota] {}", msg);
                }
            } catch (TenantQuotaExceededException e) {
                throw e;
            } catch (Exception e) {
                log.debug("[Quota] Could not check memory count for {}: {}", key, e.getMessage());
            }
        }

        // Check rate limit (simple windowed counter)
        checkRateLimit(key, config);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core: Get / Load Tenant Memory
    // ═══════════════════════════════════════════════════════════════

    /**
     * Retrieves the isolated memory instance for a tenant, loading it if not in the hot pool.
     *
     * <p>If the hot pool is at capacity, the least-recently-used tenant is evicted
     * before loading the new one (pressure eviction).</p>
     *
     * <p>When a {@link com.spectrayan.spector.management.tenant.TenantNamespaceManager}
     * is set, the "default" tenant is no longer short-circuited — it is resolved
     * through the namespace manager like any other tenant.</p>
     *
     * @param tenantId the tenant identifier
     * @return the tenant's isolated SpectorMemory, or null for blank tenants
     */
    public SpectorMemory getMemoryForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        // When namespace path resolver is available, route the default tenant
        // through namespace-level resolution (default namespace = "default")
        if ("default".equalsIgnoreCase(tenantId) && namespacePathResolver == null) {
            return null;
        }

        // Update last-access timestamp
        lastAccess.computeIfAbsent(tenantId, k -> new AtomicLong()).set(System.currentTimeMillis());

        // Fast path: check if already in hot pool (read lock)
        SpectorMemory existing = getFromPool(tenantId);
        if (existing != null) {
            return existing;
        }

        // Slow path: load tenant (write lock)
        return loadTenant(tenantId);
    }

    /**
     * Retrieves the isolated memory instance for a specific namespace within a tenant.
     *
     * <p>This is the primary entry point for per-user data isolation.
     * The hot pool is keyed by {@code tenantId:namespaceId} to ensure
     * each user/agent gets a completely separate {@link SpectorMemory} instance.</p>
     *
     * <p>Requires a {@link com.spectrayan.spector.management.tenant.TenantNamespaceManager}
     * to be set for path resolution. If not set, falls back to
     * {@link #getMemoryForTenant(String)}.</p>
     *
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier (e.g., {@code user-admin})
     * @return the namespace's isolated SpectorMemory
     */
    public SpectorMemory getMemoryForNamespace(String tenantId, String namespaceId) {
        if (tenantId == null || tenantId.isBlank()
                || namespaceId == null || namespaceId.isBlank()) {
            return null;
        }
        if (namespacePathResolver == null) {
            log.debug("[Registry] No NamespacePathResolver set, falling back to tenant-level routing");
            return getMemoryForTenant(tenantId);
        }

        String compoundKey = tenantId + ":" + namespaceId;

        // Update last-access timestamp
        lastAccess.computeIfAbsent(compoundKey, k -> new AtomicLong()).set(System.currentTimeMillis());

        // Fast path: check if already in hot pool
        SpectorMemory existing = getFromPool(compoundKey);
        if (existing != null) {
            return existing;
        }

        // Slow path: resolve path via namespace manager and load
        return loadNamespace(tenantId, namespaceId, compoundKey);
    }

    /**
     * Gets the ingestion target for a tenant, or falls back to the default target.
     */
    public IngestionTarget getTargetForTenant(String tenantId, IngestionTarget defaultTarget) {
        SpectorMemory tenantMem = getMemoryForTenant(tenantId);
        if (tenantMem != null) {
            return tenantMem.target();
        }
        return defaultTarget;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Eviction
    // ═══════════════════════════════════════════════════════════════

    /**
     * Evicts tenant memory workspaces that have been idle beyond their configured threshold.
     *
     * @return list of evicted tenant IDs
     */
    public List<String> evictIdle() {
        long now = System.currentTimeMillis();
        List<String> evicted = new ArrayList<>();

        for (var entry : lastAccess.entrySet()) {
            String key = entry.getKey();
            long lastAccessMs = entry.getValue().get();
            TenantResourceConfig config = getConfigForTenant(key);

            if (now - lastAccessMs > config.idleEvictionMs()) {
                poolLock.writeLock().lock();
                try {
                    LeasedMemory leased = hotPool.get(key);
                    if (leased != null) {
                        leased.markForEviction();
                        if (leased.isEvictable()) {
                            hotPool.remove(key);
                            demoteToWarmPool(key, leased);
                            evicted.add(key);
                            idleEvictions.incrementAndGet();
                            totalEvictions.incrementAndGet();
                            log.info("[Eviction] Evicted idle namespace '{}' (idle {}ms)",
                                    key, now - lastAccessMs);
                        } else {
                            log.debug("[Eviction] Skipping '{}' — {} active leases",
                                    key, leased.activeLeases());
                        }
                    }
                } finally {
                    poolLock.writeLock().unlock();
                }
                if (evicted.contains(key)) {
                    lastAccess.remove(key);
                }
            }
        }

        return evicted;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lease Management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Releases an active lease on a tenant's memory instance.
     *
     * <p>This method MUST be called in a {@code finally} block after
     * calling {@link #getMemoryForTenant(String)} to prevent lease leaks
     * that block LRU eviction. Example:</p>
     * <pre>
     *   SpectorMemory mem = registry.getMemoryForTenant(tenantId);
     *   try {
     *       mem.remember(...);
     *   } finally {
     *       registry.releaseMemoryForTenant(tenantId);
     *   }
     * </pre>
     *
     * @param tenantId the tenant identifier
     */
    public void releaseMemoryForTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        releaseLease(tenantId);
    }

    /**
     * Releases an active lease on a namespace's memory instance.
     *
     * <p>This method MUST be called in a {@code finally} block after
     * calling {@link #getMemoryForNamespace(String, String)}.</p>
     *
     * @param tenantId    the tenant identifier
     * @param namespaceId the namespace identifier
     */
    public void releaseMemoryForNamespace(String tenantId, String namespaceId) {
        if (tenantId == null || tenantId.isBlank()
                || namespaceId == null || namespaceId.isBlank()) {
            return;
        }
        String compoundKey = tenantId + ":" + namespaceId;
        releaseLease(compoundKey);
    }

    /**
     * Releases the lease on the {@link LeasedMemory} at the given pool key.
     * No-op if the key is not in the hot pool (e.g., already evicted).
     */
    private void releaseLease(String key) {
        poolLock.readLock().lock();
        try {
            LeasedMemory leased = hotPool.get(key);
            if (leased != null) {
                leased.release();
            }
        } finally {
            poolLock.readLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Monitoring
    // ═══════════════════════════════════════════════════════════════

    /** Returns the number of active (hot) tenant workspaces. */
    public int activeTenantCount() {
        poolLock.readLock().lock();
        try {
            return hotPool.size();
        } finally {
            poolLock.readLock().unlock();
        }
    }

    /** Returns the maximum number of active tenants allowed. */
    public int maxActiveTenants() {
        return maxActive;
    }

    /** Returns the namespaces root path. */
    public Path namespacesRoot() {
        return namespacesRoot;
    }

    /** Returns whether directory sharding is enabled. */
    public boolean isSharded() {
        return sharded;
    }

    /** Returns pager-level metrics for monitoring dashboards. */
    public Map<String, Object> pagerMetrics() {
        poolLock.readLock().lock();
        try {
            var metrics = new LinkedHashMap<String, Object>();
            metrics.put("hotActive", hotPool.size());
            metrics.put("warmActive", warmPool.size());
            metrics.put("maxActive", maxActive);
            metrics.put("warmPoolCap", warmPoolCap);
            metrics.put("totalLoads", totalLoads.get());
            metrics.put("totalEvictions", totalEvictions.get());
            metrics.put("pressureEvictions", pressureEvictions.get());
            metrics.put("idleEvictions", idleEvictions.get());
            return metrics;
        } finally {
            poolLock.readLock().unlock();
        }
    }

    /** Returns metrics for a specific tenant. */
    public Map<String, Object> getTenantMetrics(String tenantId) {
        var metrics = new LinkedHashMap<String, Object>();
        metrics.put("tenantId", tenantId);

        // Check hot pool membership (without acquiring a lease)
        boolean isHot;
        poolLock.readLock().lock();
        try {
            isHot = hotPool.containsKey(tenantId);
        } finally {
            poolLock.readLock().unlock();
        }
        metrics.put("active", isHot);
        metrics.put("warm", warmPool.containsKey(tenantId));
        metrics.put("config", getConfigForTenant(tenantId));

        AtomicLong access = lastAccess.get(tenantId);
        if (access != null) {
            metrics.put("lastAccessMs", access.get());
            metrics.put("idleDurationMs", System.currentTimeMillis() - access.get());
        }

        // Only try to read memory count if hot
        if (isHot) {
            SpectorMemory memory = getFromPool(tenantId);
            if (memory != null) {
                try {
                    metrics.put("memoryCount", memory.admin().index().size());
                } catch (Exception e) {
                    metrics.put("memoryCount", "unavailable");
                }
            }
        } else {
            // Check warm pool for cached count
            NamespaceHandle handle = warmPool.get(tenantId);
            if (handle != null) {
                metrics.put("memoryCount", handle.memoryCount());
            }
        }

        return metrics;
    }

    /**
     * Returns all active SpectorMemory instances for a given tenant, mapped by namespaceId.
     * For tenant-level workspaces, the key is empty string.
     */
    public Map<String, SpectorMemory> getActiveMemoriesForTenant(String tenantId) {
        Map<String, SpectorMemory> active = new java.util.HashMap<>();
        poolLock.readLock().lock();
        try {
            for (var entry : hotPool.entrySet()) {
                String key = entry.getKey();
                String tId = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
                if (tId.equals(tenantId)) {
                    String nsId = key.contains(":") ? key.substring(key.indexOf(':') + 1) : "";
                    active.put(nsId, entry.getValue().memory());
                }
            }
        } finally {
            poolLock.readLock().unlock();
        }
        return active;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal: Pool Operations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets a tenant from the hot pool (updates access order).
     * Uses read lock — concurrent reads are allowed.
     */
    private SpectorMemory getFromPool(String key) {
        poolLock.readLock().lock();
        try {
            LeasedMemory leased = hotPool.get(key);
            if (leased != null) {
                SpectorMemory mem = leased.acquire();
                if (mem != null) {
                    return mem;
                }
                // Marked for eviction — treat as cache miss
            }
            return null;
        } finally {
            poolLock.readLock().unlock();
        }
    }

    /**
     * Removes a tenant from the hot pool.
     */
    private LeasedMemory removeFromPool(String key) {
        poolLock.writeLock().lock();
        try {
            return hotPool.remove(key);
        } finally {
            poolLock.writeLock().unlock();
        }
    }

    /**
     * Loads a tenant into the hot pool, evicting the LRU tenant if at capacity.
     */
    private SpectorMemory loadTenant(String tenantId) {
        poolLock.writeLock().lock();
        try {
            // Double-check: another thread may have loaded it
            LeasedMemory existingLeased = hotPool.get(tenantId);
            if (existingLeased != null) {
                SpectorMemory mem = existingLeased.acquire();
                if (mem != null) return mem;
            }

            // Pressure eviction: evict LRU if at capacity
            while (hotPool.size() >= maxActive) {
                if (!evictLeastRecentlyUsed()) {
                    log.warn("[Pager] Pool is at capacity ({}) but all hot tenants have active leases. Temporarily exceeding capacity limit.", maxActive);
                    break;
                }
            }

            // Create new isolated SpectorMemory
            SpectorMemory memory = createMemoryForTenant(tenantId);
            LeasedMemory leased = new LeasedMemory(memory);
            hotPool.put(tenantId, leased);
            warmPool.remove(tenantId); // promote: remove from warm if present
            totalLoads.incrementAndGet();

            log.debug("[Pager] Loaded tenant '{}' (pool={}/{})",
                    tenantId, hotPool.size(), maxActive);

            // Post-load hook: apply salience profile to newly-loaded namespace
            var hook = postLoadHook;
            if (hook != null) {
                try {
                    hook.accept(memory, tenantId);
                } catch (Exception e) {
                    log.warn("[Pager] Post-load hook failed for '{}': {}", tenantId, e.getMessage());
                }
            }

            return leased.acquire();

        } finally {
            poolLock.writeLock().unlock();
        }
    }

    /**
     * Evicts the least-recently-used tenant from the hot pool.
     * MUST be called under write lock.
     */
    private boolean evictLeastRecentlyUsed() {
        Iterator<Map.Entry<String, LeasedMemory>> it = hotPool.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LeasedMemory> entry = it.next();
            LeasedMemory leased = entry.getValue();

            // Try to mark for eviction — skip if active leases exist
            leased.markForEviction();
            if (leased.isEvictable()) {
                it.remove();
                String evictId = entry.getKey();
                demoteToWarmPool(evictId, leased);
                lastAccess.remove(evictId);

                pressureEvictions.incrementAndGet();
                totalEvictions.incrementAndGet();
                log.info("[Pager] Pressure eviction: '{}' → warm (pool={}/{})",
                        evictId, hotPool.size(), maxActive);
                return true;
            } else {
                log.debug("[Pager] Skipping eviction of '{}' — {} active leases",
                        entry.getKey(), leased.activeLeases());
            }
        }
        return false;
    }

    /**
     * Demotes an evicted hot entry to the warm pool, keeping its metadata
     * for faster re-promotion. Closes the Arena and releases mmap.
     */
    private void demoteToWarmPool(String key, LeasedMemory leased) {
        SpectorMemory mem = leased.memory();
        long memoryCount = 0;
        try {
            memoryCount = mem.admin().index().size();
        } catch (Exception e) {
            // Memory may already be partially closed
        }
        closeQuietly(key, mem);

        // Add to warm pool
        NamespaceHandle handle = NamespaceHandle.fromEviction(key, null, dimensions, memoryCount);
        warmPool.put(key, handle);

        // Trim warm pool if over capacity
        while (warmPool.size() > warmPoolCap) {
            var warmIt = warmPool.entrySet().iterator();
            if (warmIt.hasNext()) {
                var warmEldest = warmIt.next();
                warmIt.remove();
                log.debug("[Warm] Evicted handle '{}' from warm pool", warmEldest.getKey());
            }
        }
    }

    /**
     * Creates a new isolated DefaultSpectorMemory for the given tenant.
     *
     * <p>When sharding is enabled, the tenant path is resolved via
     * {@link StorageLayout#namespaceDirSharded(Path, String)} which places
     * the directory under a 2-level hash prefix (e.g., {@code a3/f7/tenant-1/}).
     * Otherwise, uses a flat layout ({@code tenant-1/}).</p>
     */
    private SpectorMemory createMemoryForTenant(String tenantId) {
        return createMemoryAtPath(resolveTenantPath(tenantId), tenantId);
    }

    /**
     * Loads a namespace into the hot pool using a compound key.
     */
    private SpectorMemory loadNamespace(String tenantId, String namespaceId, String compoundKey) {
        poolLock.writeLock().lock();
        try {
            // Double-check: another thread may have loaded it
            LeasedMemory existingLeased = hotPool.get(compoundKey);
            if (existingLeased != null) {
                SpectorMemory mem = existingLeased.acquire();
                if (mem != null) return mem;
            }

            // Pressure eviction: evict LRU if at capacity
            while (hotPool.size() >= maxActive) {
                evictLeastRecentlyUsed();
            }

            // JIT-create the namespace directory structure via NamespacePathResolver
            Path nsPath = namespacePathResolver.resolveAndProvision(tenantId, namespaceId);
            SpectorMemory memory = createMemoryAtPath(nsPath, compoundKey);
            LeasedMemory leased = new LeasedMemory(memory);
            hotPool.put(compoundKey, leased);
            warmPool.remove(compoundKey); // promote: remove from warm if present
            totalLoads.incrementAndGet();

            log.debug("[Pager] Loaded namespace '{}' at {} (pool={}/{})",
                    compoundKey, nsPath, hotPool.size(), maxActive);

            // Post-load hook: apply salience profile to newly-loaded namespace
            var hook = postLoadHook;
            if (hook != null) {
                try {
                    hook.accept(memory, compoundKey);
                } catch (Exception e) {
                    log.warn("[Pager] Post-load hook failed for '{}': {}", compoundKey, e.getMessage());
                }
            }

            return leased.acquire();

        } finally {
            poolLock.writeLock().unlock();
        }
    }

    /**
     * Resolves the directory path for a tenant (legacy tenant-level routing).
     */
    private Path resolveTenantPath(String tenantId) {
        Path tenantPath;
        if (sharded) {
            tenantPath = StorageLayout.namespaceDirSharded(namespacesRoot.getParent(), tenantId);
        } else {
            tenantPath = namespacesRoot.resolve(tenantId);
        }

        // Cold tier rehydration: if path doesn't exist and hook is available
        if (!Files.exists(tenantPath) && coldTierHook != null) {
            try {
                if (coldTierHook.isArchived(tenantId)) {
                    tenantPath = coldTierHook.rehydrate(tenantId, tenantPath);
                    log.info("[Cold->Hot] Rehydrated tenant '{}' from cold storage", tenantId);
                }
            } catch (Exception e) {
                log.warn("[Cold->Hot] Rehydration failed for tenant '{}': {} - creating fresh",
                        tenantId, e.getMessage());
            }
        }
        return tenantPath;
    }

    /**
     * Creates a new isolated DefaultSpectorMemory at the given path.
     */
    private SpectorMemory createMemoryAtPath(Path path, String label) {
        try {
            Files.createDirectories(path);

            var builder = DefaultSpectorMemory.builder()
                    .dimensions(dimensions)
                    .embeddingProvider(embeddingProvider)
                    .persistence(path);

            // Apply global cognitive feature configuration (entity extraction,
            // tag extraction, SPLADE/ColBERT, encryption, etc.) to per-user namespace
            if (memoryBuilderCustomizer != null) {
                memoryBuilderCustomizer.accept(builder, label);
            }

            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize SpectorMemory for: " + label, e);
        }
    }

    /**
     * Closes a SpectorMemory without throwing.
     */
    private void closeQuietly(String tenantId, SpectorMemory memory) {
        try {
            memory.close();
        } catch (Exception e) {
            log.warn("[Registry] Error closing tenant '{}' memory: {}",
                    tenantId, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Internal: Rate Limiting
    // ═══════════════════════════════════════════════════════════════

    private void checkRateLimit(String tenantId, TenantResourceConfig config) {
        long now = System.currentTimeMillis();

        // Reset window every 60 seconds
        if (now - rateWindowStartMs > 60_000) {
            ingestionCounts.clear();
            rateWindowStartMs = now;
        }

        AtomicLong counter = ingestionCounts.computeIfAbsent(tenantId, k -> new AtomicLong());
        long count = counter.incrementAndGet();

        if (count > config.maxIngestionsPerMin()) {
            String msg = String.format("Tenant '%s' rate limit exceeded: %d/%d per minute",
                    tenantId, count, config.maxIngestionsPerMin());
            if (config.hardEnforce()) {
                throw new TenantQuotaExceededException(msg);
            }
            if (count == config.maxIngestionsPerMin() + 1) {
                // Log only once at the transition point
                log.warn("[RateLimit] {}", msg);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Closes all active tenant workspaces and releases all resources.
     */
    @Override
    public void close() {
        poolLock.writeLock().lock();
        try {
            for (var entry : hotPool.entrySet()) {
                closeQuietly(entry.getKey(), entry.getValue().memory());
            }
            hotPool.clear();
            warmPool.clear();
            tenantConfigs.clear();
            lastAccess.clear();
            ingestionCounts.clear();
            log.info("[Registry] Closed all tenant workspaces");
        } finally {
            poolLock.writeLock().unlock();
        }
    }

    /**
     * Resets all tenant state. <b>FOR TESTING ONLY.</b>
     *
     * <p>Closes all tenant SpectorMemory instances and clears all maps.
     * Must be called between tests to ensure isolation when running
     * E2E integration tests.</p>
     */
    public void resetForTesting() {
        close();
        totalLoads.set(0);
        totalEvictions.set(0);
        pressureEvictions.set(0);
        idleEvictions.set(0);
        rateWindowStartMs = System.currentTimeMillis();
        defaultConfig = TenantResourceConfig.defaults();
        log.debug("[Registry] Reset all tenant state (testing mode)");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Exception
    // ═══════════════════════════════════════════════════════════════

    /**
     * Exception thrown when a tenant exceeds its resource quota (hard enforcement mode).
     */
    public static class TenantQuotaExceededException extends RuntimeException {
        public TenantQuotaExceededException(String message) {
            super(message);
        }
    }
}

