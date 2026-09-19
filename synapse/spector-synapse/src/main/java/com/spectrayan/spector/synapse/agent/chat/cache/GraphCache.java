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
package com.spectrayan.spector.synapse.agent.chat.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * High-performance compilation cache for LangGraph4j {@link CompiledGraph} instances
 * (Issue #263, ADR-0084).
 *
 * <p>Eliminates the 150–300ms graph compilation overhead on every turn, unlocking
 * sub-500ms TTFT across warm requests.</p>
 *
 * <p>Key format: {@code <soul.id>:<soul.soulVersion>:<toolRegistry.fingerprint()>}</p>
 */
@Component
public class GraphCache {

    private static final Logger log = LoggerFactory.getLogger(GraphCache.class);

    private final Cache<String, CompiledGraph<AgentState>> cache;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public GraphCache() {
        this(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterAccess(Duration.ofHours(24))
                .recordStats()
                .build());
    }

    public GraphCache(Cache<String, CompiledGraph<AgentState>> cache) {
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    /**
     * Computes the deterministic cache key for an agent soul and tool registry.
     */
    public static String computeKey(AgentSoul soul, ToolRegistry toolRegistry) {
        Objects.requireNonNull(soul, "soul must not be null");
        Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        return soul.id() + ":" + soul.soulVersion() + ":" + toolRegistry.fingerprint();
    }

    /**
     * Retrieves an existing compiled graph or compiles and stores a new one.
     */
    public CompiledGraph<AgentState> getOrCompile(
            AgentSoul soul,
            ToolRegistry toolRegistry,
            Supplier<CompiledGraph<AgentState>> compiler) {

        String key = computeKey(soul, toolRegistry);
        CompiledGraph<AgentState> existing = cache.getIfPresent(key);
        if (existing != null) {
            hits.incrementAndGet();
            log.debug("[GraphCache] Cache HIT for key '{}'", key);
            return existing;
        }

        misses.incrementAndGet();
        long startMs = System.currentTimeMillis();
        CompiledGraph<AgentState> compiled = compiler.get();
        long durationMs = System.currentTimeMillis() - startMs;

        cache.put(key, compiled);
        log.info("[GraphCache] Cache MISS for key '{}' — compiled in {}ms", key, durationMs);
        return compiled;
    }

    /**
     * Invalidate all compiled graphs for a specific soul (e.g. upon prompt or version edit).
     */
    public void invalidateSoul(String soulId) {
        if (soulId == null || soulId.isBlank()) return;
        String prefix = soulId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        log.info("[GraphCache] Invalidated cache entries for soul '{}'", soulId);
    }

    /**
     * Clear all cached compiled graphs.
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("[GraphCache] Evicted all cached graphs");
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public long size() {
        return cache.estimatedSize();
    }
}
