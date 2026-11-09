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
package com.spectrayan.spector.synapse.ratelimit;

import com.spectrayan.spector.config.properties.RateLimitProperties;
import com.spectrayan.spector.config.properties.RateLimitProperties.AdmissionControlProperties;
import com.spectrayan.spector.config.properties.RateLimitProperties.EndpointPolicy;
import com.spectrayan.spector.config.properties.RateLimitProperties.RouteConcurrencyPolicy;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.springframework.util.AntPathMatcher;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces server-wide and per-route in-flight concurrency limits and JVM heap / off-heap
 * memory-pressure high-water-mark admission control for inbound Synapse HTTP/MCP requests.
 */
public class RequestAdmissionController {

    public static final String ROUTE_WRITE = "write";
    public static final String ROUTE_RECALL = "recall";
    public static final String ROUTE_MANAGEMENT = "management";

    public static final String REASON_GLOBAL_CONCURRENCY = "global_concurrency_limit_exceeded";
    public static final String REASON_ROUTE_CONCURRENCY = "route_concurrency_limit_exceeded";
    public static final String REASON_HEAP_PRESSURE = "heap_memory_pressure";
    public static final String REASON_OFF_HEAP_PRESSURE = "off_heap_memory_pressure";

    private static final List<String> MANAGEMENT_PATTERNS = List.of(
            "/api/v1/admin/**",
            "/api/v1/migration/**",
            "/api/v1/tasks",
            "/api/v1/tasks/**",
            "/api/v1/connectors",
            "/api/v1/connectors/**",
            "/api/v1/providers",
            "/api/v1/providers/**",
            "/api/v1/observability",
            "/api/v1/observability/**",
            "/api/v1/salience/rescore",
            "/api/v1/salience/rescore/**",
            "/tasks",
            "/tasks/**",
            "/providers",
            "/providers/**"
    );

    private static final List<String> RECALL_PATTERNS = List.of(
            "/api/v1/memory/recall",
            "/api/v1/memory/recall/**",
            "/api/v1/memory/search",
            "/api/v1/memory/search/**",
            "/api/v1/memory/browse",
            "/api/v1/memory/browse/**",
            "/api/v1/memory/reflect",
            "/api/v1/memory/reflect/**",
            "/api/v1/mel/**",
            "/api/v1/query",
            "/api/v1/query/**"
    );

    private static final List<String> WRITE_PATTERNS = List.of(
            "/api/v1/memory/remember",
            "/api/v1/memory/remember/**",
            "/api/v1/memory/store",
            "/api/v1/memory/store/**",
            "/api/v1/memory/batch",
            "/api/v1/memory/batch/**",
            "/api/v1/memory/ingest",
            "/api/v1/memory/ingest/**",
            "/api/v1/memory/reinforce",
            "/api/v1/memory/reinforce/**",
            "/api/v1/memory/suppress",
            "/api/v1/memory/suppress/**",
            "/api/v1/memory/resolve",
            "/api/v1/memory/resolve/**",
            "/api/v1/memory/forget",
            "/api/v1/memory/forget/**",
            "/api/v1/memory/purge",
            "/api/v1/memory/purge/**",
            "/api/v1/memory/vacuum",
            "/api/v1/memory/vacuum/**",
            "/api/v1/memory/compact",
            "/api/v1/memory/compact/**"
    );

    private final SynapseProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AtomicInteger globalInFlight = new AtomicInteger();
    private final ConcurrentHashMap<String, AtomicInteger> routeInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean heapSheddingActive = new AtomicBoolean(false);
    private final AtomicBoolean offHeapSheddingActive = new AtomicBoolean(false);
    private final AtomicLong totalShedRequests = new AtomicLong();
    private final AtomicLong concurrencyShedRequests = new AtomicLong();
    private final AtomicLong memoryPressureShedRequests = new AtomicLong();
    private volatile MemoryPressureProbe memoryPressureProbe;

    public RequestAdmissionController(SynapseProperties properties) {
        this(properties, new JvmMemoryPressureProbe());
    }

    public RequestAdmissionController(SynapseProperties properties, MemoryPressureProbe memoryPressureProbe) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.memoryPressureProbe = Objects.requireNonNull(memoryPressureProbe, "memoryPressureProbe must not be null");
    }

    public void setMemoryPressureProbe(MemoryPressureProbe memoryPressureProbe) {
        this.memoryPressureProbe = Objects.requireNonNull(memoryPressureProbe, "memoryPressureProbe must not be null");
    }

    public MemoryPressureProbe getMemoryPressureProbe() {
        return memoryPressureProbe;
    }

    public boolean isExempt(String path) {
        if (path == null) {
            return false;
        }
        RateLimitProperties rateLimit = properties.getRateLimit();
        if (rateLimit == null) {
            return false;
        }
        AdmissionControlProperties admission = rateLimit.getAdmission();
        if (admission == null || admission.getExemptPaths() == null) {
            return false;
        }
        for (String pattern : admission.getExemptPaths()) {
            if (pattern != null && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    public AdmissionDecision tryAcquire(String method, String path) {
        RateLimitProperties rateLimit = properties.getRateLimit();
        if (rateLimit == null || !rateLimit.isEnabled()) {
            return AdmissionDecision.admittedNoop();
        }
        AdmissionControlProperties admission = rateLimit.getAdmission();
        if (admission == null || !admission.isEnabled()) {
            return AdmissionDecision.admittedNoop();
        }
        int retryAfter = Math.max(1, admission.getRetryAfterSeconds());

        // 1. Check JVM heap and off-heap memory pressure (with hysteresis recovery)
        if (admission.isMemoryPressureEnabled()) {
            MemorySnapshot snapshot = memoryPressureProbe.sample();
            String pressureReason = evaluateMemoryPressure(admission, snapshot);
            if (pressureReason != null) {
                totalShedRequests.incrementAndGet();
                memoryPressureShedRequests.incrementAndGet();
                String detail = REASON_HEAP_PRESSURE.equals(pressureReason)
                        ? String.format(Locale.ROOT,
                                "Server is shedding load due to JVM heap memory pressure (%.1f%% used). Please retry after %d seconds.",
                                snapshot.heapUsageRatio() * 100.0, retryAfter)
                        : String.format(Locale.ROOT,
                                "Server is shedding load due to off-heap memory pressure. Please retry after %d seconds.",
                                retryAfter);
                return AdmissionDecision.rejected(pressureReason, null, detail, retryAfter);
            }
        } else {
            heapSheddingActive.set(false);
            offHeapSheddingActive.set(false);
        }

        // 2. Check global in-flight concurrency limit
        int globalMax = admission.getMaxConcurrentRequests();
        if (!tryAcquireSlot(globalInFlight, globalMax)) {
            totalShedRequests.incrementAndGet();
            concurrencyShedRequests.incrementAndGet();
            String detail = String.format(
                    "Global in-flight concurrency limit (%d) exceeded. Please retry after %d seconds.",
                    globalMax, retryAfter);
            return AdmissionDecision.rejected(REASON_GLOBAL_CONCURRENCY, "global", detail, retryAfter);
        }

        // 3. Check per-route in-flight concurrency limit
        ResolvedRouteLimit routeLimit = resolveRouteLimit(method, path, rateLimit, admission);
        if (routeLimit != null && routeLimit.maxConcurrent() > 0) {
            AtomicInteger counter = routeInFlight.computeIfAbsent(routeLimit.routeKey(), k -> new AtomicInteger());
            if (!tryAcquireSlot(counter, routeLimit.maxConcurrent())) {
                globalInFlight.decrementAndGet();
                totalShedRequests.incrementAndGet();
                concurrencyShedRequests.incrementAndGet();
                String detail = String.format(
                        "In-flight concurrency limit (%d) exceeded for route '%s'. Please retry after %d seconds.",
                        routeLimit.maxConcurrent(), routeLimit.routeKey(), retryAfter);
                return AdmissionDecision.rejected(REASON_ROUTE_CONCURRENCY, routeLimit.routeKey(), detail, retryAfter);
            }
            return AdmissionDecision.admitted(routeLimit.routeKey(), () -> {
                counter.decrementAndGet();
                globalInFlight.decrementAndGet();
            });
        }

        return AdmissionDecision.admitted("global", globalInFlight::decrementAndGet);
    }

    public int globalInFlight() {
        return globalInFlight.get();
    }

    public int routeInFlight(String routeKey) {
        AtomicInteger counter = routeInFlight.get(routeKey);
        return counter != null ? counter.get() : 0;
    }

    public boolean isHeapSheddingActive() {
        return heapSheddingActive.get();
    }

    public boolean isOffHeapSheddingActive() {
        return offHeapSheddingActive.get();
    }

    public long totalShedRequests() {
        return totalShedRequests.get();
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        RateLimitProperties rateLimit = properties.getRateLimit();
        AdmissionControlProperties admission = rateLimit != null ? rateLimit.getAdmission() : null;
        stats.put("enabled", admission != null && admission.isEnabled());
        stats.put("globalInFlight", globalInFlight.get());
        stats.put("maxConcurrentRequests", admission != null ? admission.getMaxConcurrentRequests() : 0);
        Map<String, Integer> routes = new LinkedHashMap<>();
        routeInFlight.forEach((k, v) -> routes.put(k, v.get()));
        stats.put("routeInFlight", routes);
        stats.put("heapSheddingActive", heapSheddingActive.get());
        stats.put("offHeapSheddingActive", offHeapSheddingActive.get());
        stats.put("totalShedRequests", totalShedRequests.get());
        stats.put("concurrencyShedRequests", concurrencyShedRequests.get());
        stats.put("memoryPressureShedRequests", memoryPressureShedRequests.get());
        return stats;
    }

    private String evaluateMemoryPressure(AdmissionControlProperties admission, MemorySnapshot snapshot) {
        // Evaluate heap pressure
        double heapRatio = snapshot.heapUsageRatio();
        long heapUsed = snapshot.heapUsedBytes();
        double heapHighWater = admission.getHeapHighWaterMarkRatio();
        double heapRecovery = admission.effectiveHeapRecoveryRatio();
        long heapHighWaterBytes = admission.getHeapHighWaterMarkBytes();
        long heapRecoveryBytes = admission.effectiveHeapRecoveryBytes();

        boolean heapAboveHighWater = (heapHighWater > 0.0 && snapshot.heapMaxBytes() > 0 && heapRatio >= heapHighWater)
                || (heapHighWaterBytes > 0L && heapUsed >= heapHighWaterBytes);
        boolean heapBelowRecovery = (heapHighWater <= 0.0 || snapshot.heapMaxBytes() <= 0 || heapRatio <= heapRecovery)
                && (heapHighWaterBytes <= 0L || heapUsed <= heapRecoveryBytes);

        if (heapAboveHighWater) {
            heapSheddingActive.set(true);
            return REASON_HEAP_PRESSURE;
        } else if (heapSheddingActive.get()) {
            if (heapBelowRecovery) {
                heapSheddingActive.set(false);
            } else {
                return REASON_HEAP_PRESSURE;
            }
        }

        // Evaluate off-heap pressure
        long offHeapUsed = snapshot.offHeapUsedBytes();
        long effectiveOffHeapMax = admission.getMaxOffHeapBytes() > 0L
                ? admission.getMaxOffHeapBytes()
                : snapshot.offHeapMaxBytes();
        double offHeapRatio = effectiveOffHeapMax > 0L ? (double) offHeapUsed / effectiveOffHeapMax : 0.0;
        double offHeapHighWater = admission.getOffHeapHighWaterMarkRatio();
        double offHeapRecovery = admission.effectiveOffHeapRecoveryRatio();
        long offHeapHighWaterBytes = admission.getOffHeapHighWaterMarkBytes();
        long offHeapRecoveryBytes = admission.effectiveOffHeapRecoveryBytes();

        boolean offHeapAboveHighWater = (offHeapHighWater > 0.0 && effectiveOffHeapMax > 0L && offHeapRatio >= offHeapHighWater)
                || (offHeapHighWaterBytes > 0L && offHeapUsed >= offHeapHighWaterBytes);
        boolean offHeapBelowRecovery = (offHeapHighWater <= 0.0 || effectiveOffHeapMax <= 0L || offHeapRatio <= offHeapRecovery)
                && (offHeapHighWaterBytes <= 0L || offHeapUsed <= offHeapRecoveryBytes);

        if (offHeapAboveHighWater) {
            offHeapSheddingActive.set(true);
            return REASON_OFF_HEAP_PRESSURE;
        } else if (offHeapSheddingActive.get()) {
            if (offHeapBelowRecovery) {
                offHeapSheddingActive.set(false);
            } else {
                return REASON_OFF_HEAP_PRESSURE;
            }
        }

        return null;
    }

    private ResolvedRouteLimit resolveRouteLimit(
            String method,
            String path,
            RateLimitProperties rateLimit,
            AdmissionControlProperties admission) {
        if (path == null) {
            return null;
        }
        String normalizedMethod = method != null ? method.toUpperCase(Locale.ROOT) : "GET";

        // 1. Custom RouteConcurrencyPolicy entries
        if (admission.getRoutes() != null) {
            for (RouteConcurrencyPolicy policy : admission.getRoutes()) {
                if (policy == null || policy.getPathPattern() == null || policy.getMaxConcurrentRequests() <= 0) {
                    continue;
                }
                if (pathMatcher.match(policy.getPathPattern(), path)
                        && matchesMethod(normalizedMethod, policy.getMethods())) {
                    String routeKey = (policy.getName() != null && !policy.getName().isBlank())
                            ? policy.getName()
                            : policy.getPathPattern();
                    return new ResolvedRouteLimit(routeKey, policy.getMaxConcurrentRequests());
                }
            }
        }

        // 2. Pattern entries in routeLimits map
        Map<String, Integer> routeLimits = admission.getRouteLimits();
        if (routeLimits != null && !routeLimits.isEmpty()) {
            for (Map.Entry<String, Integer> entry : routeLimits.entrySet()) {
                String key = entry.getKey();
                Integer limit = entry.getValue();
                if (key != null && limit != null && limit > 0 && key.startsWith("/") && pathMatcher.match(key, path)) {
                    return new ResolvedRouteLimit(key, limit);
                }
            }
        }

        // 3. EndpointPolicy concurrency limit
        if (rateLimit.getEndpoints() != null) {
            for (EndpointPolicy ep : rateLimit.getEndpoints()) {
                if (ep != null && ep.getPathPattern() != null && ep.getMaxConcurrentRequests() > 0
                        && pathMatcher.match(ep.getPathPattern(), path)) {
                    return new ResolvedRouteLimit(ep.getPathPattern(), ep.getMaxConcurrentRequests());
                }
            }
        }

        // 4. Built-in route categories: management, recall, write
        if (matchesAny(path, MANAGEMENT_PATTERNS)) {
            int limit = resolveCategoryLimit(routeLimits, ROUTE_MANAGEMENT, admission.getManagementMaxConcurrentRequests());
            return limit > 0 ? new ResolvedRouteLimit(ROUTE_MANAGEMENT, limit) : null;
        }
        if (matchesAny(path, RECALL_PATTERNS)) {
            int limit = resolveCategoryLimit(routeLimits, ROUTE_RECALL, admission.getRecallMaxConcurrentRequests());
            return limit > 0 ? new ResolvedRouteLimit(ROUTE_RECALL, limit) : null;
        }
        if (matchesAny(path, WRITE_PATTERNS)) {
            int limit = resolveCategoryLimit(routeLimits, ROUTE_WRITE, admission.getWriteMaxConcurrentRequests());
            return limit > 0 ? new ResolvedRouteLimit(ROUTE_WRITE, limit) : null;
        }
        if (pathMatcher.match("/api/v1/memory/**", path) || pathMatcher.match("/api/v1/memories/**", path)
                || "/api/v1/memories".equals(path) || "/api/v1/memory".equals(path)) {
            if ("GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod)) {
                int limit = resolveCategoryLimit(routeLimits, ROUTE_RECALL, admission.getRecallMaxConcurrentRequests());
                return limit > 0 ? new ResolvedRouteLimit(ROUTE_RECALL, limit) : null;
            } else {
                int limit = resolveCategoryLimit(routeLimits, ROUTE_WRITE, admission.getWriteMaxConcurrentRequests());
                return limit > 0 ? new ResolvedRouteLimit(ROUTE_WRITE, limit) : null;
            }
        }

        return null;
    }

    private static int resolveCategoryLimit(Map<String, Integer> routeLimits, String category, int fallback) {
        if (routeLimits != null) {
            Integer configured = routeLimits.get(category);
            if (configured != null && configured > 0) {
                return configured;
            }
        }
        return fallback;
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMethod(String requestMethod, List<String> allowedMethods) {
        if (allowedMethods == null || allowedMethods.isEmpty()) {
            return true;
        }
        for (String allowed : allowedMethods) {
            if (allowed != null && allowed.trim().equalsIgnoreCase(requestMethod)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryAcquireSlot(AtomicInteger inFlight, int maxConcurrent) {
        if (maxConcurrent <= 0) {
            inFlight.incrementAndGet();
            return true;
        }
        while (true) {
            int current = inFlight.get();
            if (current >= maxConcurrent) {
                return false;
            }
            if (inFlight.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private record ResolvedRouteLimit(String routeKey, int maxConcurrent) {}

    public interface MemoryPressureProbe {
        MemorySnapshot sample();
    }

    public record MemorySnapshot(
            long heapUsedBytes,
            long heapMaxBytes,
            long offHeapUsedBytes,
            long offHeapMaxBytes
    ) {
        public double heapUsageRatio() {
            return heapMaxBytes > 0L ? (double) heapUsedBytes / heapMaxBytes : 0.0;
        }

        public double offHeapUsageRatio() {
            return offHeapMaxBytes > 0L ? (double) offHeapUsedBytes / offHeapMaxBytes : 0.0;
        }
    }

    public static final class JvmMemoryPressureProbe implements MemoryPressureProbe {

        @Override
        public MemorySnapshot sample() {
            Runtime runtime = Runtime.getRuntime();
            MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long heapUsed = Math.max(0L, heapUsage.getUsed());
            long heapMax = heapUsage.getMax() > 0L ? heapUsage.getMax() : runtime.maxMemory();

            long directUsed = 0L;
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if ("direct".equalsIgnoreCase(pool.getName())) {
                    directUsed += Math.max(0L, pool.getMemoryUsed());
                }
            }
            long directMax = runtime.maxMemory();
            return new MemorySnapshot(heapUsed, heapMax, directUsed, directMax);
        }
    }

    public static final class AdmissionLease implements AutoCloseable {
        private static final AdmissionLease NOOP = new AdmissionLease(() -> {});

        private final Runnable releaseAction;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private AdmissionLease(Runnable releaseAction) {
            this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction must not be null");
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                releaseAction.run();
            }
        }
    }

    public record AdmissionDecision(
            boolean admitted,
            String reason,
            String route,
            String detail,
            int retryAfterSeconds,
            AdmissionLease lease
    ) {
        public static AdmissionDecision admittedNoop() {
            return new AdmissionDecision(true, null, null, null, 0, AdmissionLease.NOOP);
        }

        public static AdmissionDecision admitted(String route, Runnable releaseAction) {
            return new AdmissionDecision(true, null, route, null, 0, new AdmissionLease(releaseAction));
        }

        public static AdmissionDecision rejected(String reason, String route, String detail, int retryAfterSeconds) {
            return new AdmissionDecision(false, reason, route, detail, retryAfterSeconds, AdmissionLease.NOOP);
        }
    }
}
