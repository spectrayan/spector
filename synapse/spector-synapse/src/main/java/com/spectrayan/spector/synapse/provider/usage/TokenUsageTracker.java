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
package com.spectrayan.spector.synapse.provider.usage;

import com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for recording, aggregating, and retrieving LLM and embedding token usage.
 *
 * <p>Persists aggregated token statistics in the Spring Cache subsystem ({@link SynapseCacheConstants#CACHE_TOKEN_USAGE})
 * partitioned across {@code user}, {@code model}, {@code session}, and {@code category} dimensions.</p>
 *
 * <p>Simultaneously emits multi-dimensional Micrometer counters for real-time Prometheus / Actuator telemetry.</p>
 */
@Service
public class TokenUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageTracker.class);

    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;

    private final Set<String> knownUsers = ConcurrentHashMap.newKeySet();
    private final Set<String> knownModels = ConcurrentHashMap.newKeySet();
    private final Set<String> knownSessions = ConcurrentHashMap.newKeySet();

    // Per-key locks avoiding synchronized blocks or string monitor synchronization (Loom virtual thread safe)
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    // In-memory fallback if Spring cache manager is null or missing cache bean in test contexts
    private final Map<String, TokenUsageStats> fallbackCache = new ConcurrentHashMap<>();

    public TokenUsageTracker(CacheManager cacheManager) {
        this(cacheManager, null);
    }

    @Autowired
    public TokenUsageTracker(CacheManager cacheManager, @Autowired(required = false) MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a token usage event, updating Spring Cache buckets and Micrometer metrics.
     *
     * @param event the token consumption event
     */
    public void record(TokenUsageEvent event) {
        if (event == null || event.totalTokens() <= 0) {
            return;
        }

        log.debug("[TokenUsage] Recording event: category={}, model={}, user={}, session={}, in={}, out={}, emb={}",
                event.category(), event.model(), event.userId(), event.sessionId(),
                event.inputTokens(), event.outputTokens(), event.embeddingTokens());

        // 1. Update Spring Cache aggregated buckets
        updateBucket("global", event);
        updateBucket("category:" + event.category().name().toLowerCase(), event);

        if (event.userId() != null && !event.userId().isBlank()) {
            knownUsers.add(event.userId());
            updateBucket("user:" + event.userId(), event);
        }

        if (event.model() != null && !event.model().isBlank()) {
            knownModels.add(event.model());
            updateBucket("model:" + event.model(), event);
        }

        if (event.sessionId() != null && !event.sessionId().isBlank()) {
            knownSessions.add(event.sessionId());
            updateBucket("session:" + event.sessionId(), event);
        }

        // 2. Emit Micrometer telemetry counters
        emitMetrics(event);
    }

    /**
     * Retrieves global engine-wide token usage stats.
     */
    public TokenUsageStats getGlobalStats() {
        return getBucket("global");
    }

    /**
     * Retrieves token usage stats for a specific user ID.
     */
    public TokenUsageStats getUserStats(String userId) {
        if (userId == null || userId.isBlank()) {
            return TokenUsageStats.empty();
        }
        return getBucket("user:" + userId);
    }

    /**
     * Retrieves token usage stats for a specific model.
     */
    public TokenUsageStats getModelStats(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return TokenUsageStats.empty();
        }
        return getBucket("model:" + modelName);
    }

    /**
     * Retrieves token usage stats for a specific session ID.
     */
    public TokenUsageStats getSessionStats(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return TokenUsageStats.empty();
        }
        return getBucket("session:" + sessionId);
    }

    /**
     * Retrieves token usage stats for a specific category.
     */
    public TokenUsageStats getCategoryStats(TokenUsageCategory category) {
        if (category == null) {
            return TokenUsageStats.empty();
        }
        return getBucket("category:" + category.name().toLowerCase());
    }

    /**
     * Produces a comprehensive summary map of global, per-model, per-user, and per-category usage.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("global", getGlobalStats());

        Map<String, TokenUsageStats> byCategory = new LinkedHashMap<>();
        for (TokenUsageCategory cat : TokenUsageCategory.values()) {
            TokenUsageStats catStats = getCategoryStats(cat);
            if (catStats.requestCount() > 0) {
                byCategory.put(cat.name().toLowerCase(), catStats);
            }
        }
        summary.put("categories", byCategory);

        Map<String, TokenUsageStats> byModel = new LinkedHashMap<>();
        for (String model : knownModels) {
            TokenUsageStats modelStats = getModelStats(model);
            if (modelStats.requestCount() > 0) {
                byModel.put(model, modelStats);
            }
        }
        summary.put("models", byModel);

        Map<String, TokenUsageStats> byUser = new LinkedHashMap<>();
        for (String user : knownUsers) {
            TokenUsageStats userStats = getUserStats(user);
            if (userStats.requestCount() > 0) {
                byUser.put(user, userStats);
            }
        }
        summary.put("users", byUser);

        Map<String, TokenUsageStats> bySession = new LinkedHashMap<>();
        for (String session : knownSessions) {
            TokenUsageStats sessionStats = getSessionStats(session);
            if (sessionStats.requestCount() > 0) {
                bySession.put(session, sessionStats);
            }
        }
        summary.put("sessions", bySession);

        return summary;
    }

    /**
     * Clears all token statistics from the Spring Cache and resets known indices.
     */
    @CacheEvict(value = SynapseCacheConstants.CACHE_TOKEN_USAGE, allEntries = true)
    public void reset() {
        log.info("[TokenUsage] Resetting all token usage caches and metrics indices");
        knownUsers.clear();
        knownModels.clear();
        knownSessions.clear();
        fallbackCache.clear();
        keyLocks.clear();

        Cache cache = resolveCache();
        if (cache != null) {
            cache.clear();
        }
    }

    public Set<String> getKnownUsers() {
        return Collections.unmodifiableSet(knownUsers);
    }

    public Set<String> getKnownModels() {
        return Collections.unmodifiableSet(knownModels);
    }

    public Set<String> getKnownSessions() {
        return Collections.unmodifiableSet(knownSessions);
    }

    // ─────────────────────────── Internal Helpers ───────────────────────────

    private void updateBucket(String key, TokenUsageEvent event) {
        Cache cache = resolveCache();
        if (cache != null) {
            ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                TokenUsageStats existing = cache.get(key, TokenUsageStats.class);
                TokenUsageStats updated = (existing != null ? existing : TokenUsageStats.empty()).accumulate(event);
                cache.put(key, updated);
            } finally {
                lock.unlock();
            }
        } else {
            fallbackCache.compute(key, (k, existing) ->
                    (existing != null ? existing : TokenUsageStats.empty()).accumulate(event));
        }
    }

    private TokenUsageStats getBucket(String key) {
        Cache cache = resolveCache();
        if (cache != null) {
            TokenUsageStats stats = cache.get(key, TokenUsageStats.class);
            return stats != null ? stats : TokenUsageStats.empty();
        }
        TokenUsageStats stats = fallbackCache.get(key);
        return stats != null ? stats : TokenUsageStats.empty();
    }

    private Cache resolveCache() {
        return cacheManager != null ? cacheManager.getCache(SynapseCacheConstants.CACHE_TOKEN_USAGE) : null;
    }

    private void emitMetrics(TokenUsageEvent event) {
        if (meterRegistry == null) {
            return;
        }
        try {
            // 1. Total counters (categorized by type and operational category)
            if (event.inputTokens() > 0) {
                Counter.builder("spector.tokens.total")
                        .tag("type", "input")
                        .tag("category", event.category().name().toLowerCase())
                        .description("Total input tokens consumed")
                        .register(meterRegistry)
                        .increment(event.inputTokens());
            }
            if (event.outputTokens() > 0) {
                Counter.builder("spector.tokens.total")
                        .tag("type", "output")
                        .tag("category", event.category().name().toLowerCase())
                        .description("Total output tokens consumed")
                        .register(meterRegistry)
                        .increment(event.outputTokens());
            }
            if (event.embeddingTokens() > 0) {
                Counter.builder("spector.tokens.total")
                        .tag("type", "embedding")
                        .tag("category", event.category().name().toLowerCase())
                        .description("Total embedding tokens consumed")
                        .register(meterRegistry)
                        .increment(event.embeddingTokens());
            }

            // 2. Per-user counters
            if (event.userId() != null && !event.userId().isBlank()) {
                if (event.inputTokens() > 0) {
                    Counter.builder("spector.tokens.user")
                            .tag("user_id", event.userId())
                            .tag("type", "input")
                            .register(meterRegistry)
                            .increment(event.inputTokens());
                }
                if (event.outputTokens() > 0) {
                    Counter.builder("spector.tokens.user")
                            .tag("user_id", event.userId())
                            .tag("type", "output")
                            .register(meterRegistry)
                            .increment(event.outputTokens());
                }
                if (event.embeddingTokens() > 0) {
                    Counter.builder("spector.tokens.user")
                            .tag("user_id", event.userId())
                            .tag("type", "embedding")
                            .register(meterRegistry)
                            .increment(event.embeddingTokens());
                }
            }

            // 3. Per-model counters
            if (event.model() != null && !event.model().isBlank()) {
                if (event.inputTokens() > 0) {
                    Counter.builder("spector.tokens.model")
                            .tag("model", event.model())
                            .tag("type", "input")
                            .register(meterRegistry)
                            .increment(event.inputTokens());
                }
                if (event.outputTokens() > 0) {
                    Counter.builder("spector.tokens.model")
                            .tag("model", event.model())
                            .tag("type", "output")
                            .register(meterRegistry)
                            .increment(event.outputTokens());
                }
                if (event.embeddingTokens() > 0) {
                    Counter.builder("spector.tokens.model")
                            .tag("model", event.model())
                            .tag("type", "embedding")
                            .register(meterRegistry)
                            .increment(event.embeddingTokens());
                }
            }

            // 4. Per-session counters
            if (event.sessionId() != null && !event.sessionId().isBlank()) {
                if (event.inputTokens() > 0) {
                    Counter.builder("spector.tokens.session")
                            .tag("session_id", event.sessionId())
                            .tag("type", "input")
                            .register(meterRegistry)
                            .increment(event.inputTokens());
                }
                if (event.outputTokens() > 0) {
                    Counter.builder("spector.tokens.session")
                            .tag("session_id", event.sessionId())
                            .tag("type", "output")
                            .register(meterRegistry)
                            .increment(event.outputTokens());
                }
                if (event.embeddingTokens() > 0) {
                    Counter.builder("spector.tokens.session")
                            .tag("session_id", event.sessionId())
                            .tag("type", "embedding")
                            .register(meterRegistry)
                            .increment(event.embeddingTokens());
                }
            }
        } catch (Exception e) {
            log.warn("[TokenUsage] Error updating Micrometer metrics: {}", e.getMessage());
        }
    }
}
