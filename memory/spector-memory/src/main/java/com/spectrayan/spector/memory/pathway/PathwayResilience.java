/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.BreakerRef;
import com.spectrayan.spector.commons.pathway.BulkheadConfig;
import com.spectrayan.spector.commons.pathway.CircuitBreakerConfig;
import com.spectrayan.spector.commons.pathway.OnOpen;
import com.spectrayan.spector.commons.pathway.OnReject;
import com.spectrayan.spector.commons.pathway.RetryPolicy;

import java.time.Duration;

/**
 * Canonical resilience policy for cognitive pathway stages — ADR-0036 §7.3, §9.3, §10.
 *
 * <p>This is the single place where breaker names, bulkhead sizes, and timeout budgets
 * are defined. Recipes reference these constants rather than inlining literals, so the
 * policy table in ADR-0036 §14 has exactly one implementation.</p>
 *
 * <h2>Breaker names are shared on purpose</h2>
 *
 * <p>{@link #EMBED_PROVIDER} is one breaker used by both Recall and Dream. That is the
 * point of named breakers: a dying embedding provider is dying for every caller, and
 * two independently-counting breakers would each need to fail 5 times before either
 * opened. Trip state is shared; the <em>reaction</em> to an open circuit is not — hence
 * {@link #embedProviderFailFast()} versus {@link #embedProviderBypass()}, which differ
 * only in {@link OnOpen} and resolve to the same {@link com.spectrayan.spector.commons.pathway.CircuitBreaker}.</p>
 *
 * <h2>Why some stages have no timeout</h2>
 *
 * <p>Per ADR-0036 §7.2/§7.3, only relays that can observe an interrupt may declare a
 * budget. {@code CorticalWriteTransactionRelay} writes to mmap'd {@code MemorySegment}
 * regions plus the WAL; those writes are not interruptible, so a budget would report a
 * timeout while the write completed on a detached thread — torn state. Writes and nested
 * Remember are therefore protected by admission control (bulkhead + breaker) only, where
 * "started or not" is a state the caller can reason about.</p>
 */
public final class PathwayResilience {

    private PathwayResilience() {
    }

    // ── Shared breaker names (ADR-0036 §9.3) ────────────────────────────────

    /** Dense embedding provider. Shared by Recall query transduction and Dream scene embedding. */
    public static final String EMBED_PROVIDER = "embed-provider";

    /** Remote reranker (ColBERT / cross-encoder). */
    public static final String RERANK_REMOTE = "rerank-remote";

    /** LLM provider — entity extraction, dream scene construction. */
    public static final String LLM_PROVIDER = "llm-provider";

    /** Pathway-level breaker for nested Remember conductions. */
    public static final String PATHWAY_REMEMBER = "pathway:remember";

    // ── Timeout budgets (ADR-0036 §7.3) ─────────────────────────────────────

    /** Dense embedding call budget. */
    public static final Duration EMBED_TIMEOUT = Duration.ofSeconds(2);

    /** Remote rerank budget — tight; recall must not stall on an optional rescore. */
    public static final Duration RERANK_TIMEOUT = Duration.ofMillis(80);

    /** LLM generation budget (entity extract, scene construct). */
    public static final Duration LLM_TIMEOUT = Duration.ofSeconds(8);

    // ── Retry policies (ADR-0036 §8) ────────────────────────────────────────

    /** Two transient retries for idempotent remote reads (embedding). */
    public static RetryPolicy transientTwice() {
        return RetryPolicy.of(3, Duration.ofMillis(50));
    }

    /** One transient retry for latency-sensitive optional stages (remote rerank). */
    public static RetryPolicy transientOnce() {
        return RetryPolicy.of(2, Duration.ofMillis(10));
    }

    // ── Breaker refs (ADR-0036 §9.1 — onOpen is per call site) ──────────────

    /**
     * Embedding breaker for callers that cannot proceed without a vector.
     *
     * @return breaker ref with {@link OnOpen#FAIL}
     */
    public static BreakerRef embedProviderFailFast() {
        return BreakerRef.of(EMBED_PROVIDER, CircuitBreakerConfig.remote(), OnOpen.FAIL);
    }

    /**
     * Embedding breaker for callers that can skip the work and continue.
     *
     * @return breaker ref with {@link OnOpen#BYPASS}
     */
    public static BreakerRef embedProviderBypass() {
        return BreakerRef.of(EMBED_PROVIDER, CircuitBreakerConfig.remote(), OnOpen.BYPASS);
    }

    /**
     * Remote rerank breaker — always bypassable, the lexical/dense ordering still stands.
     *
     * @return breaker ref with {@link OnOpen#BYPASS}
     */
    public static BreakerRef rerankRemote() {
        return BreakerRef.of(RERANK_REMOTE, CircuitBreakerConfig.remote(), OnOpen.BYPASS);
    }

    /**
     * LLM breaker — always bypassable, enrichment and dreaming are optional paths.
     *
     * @return breaker ref with {@link OnOpen#BYPASS}
     */
    public static BreakerRef llmProvider() {
        return BreakerRef.of(LLM_PROVIDER, CircuitBreakerConfig.remote(), OnOpen.BYPASS);
    }

    /**
     * Pathway-level breaker for nested Remember, tripping only when Remember itself
     * is repeatedly unhealthy — not when one of its graph relays degraded internally.
     *
     * @return breaker ref with {@link OnOpen#BYPASS}
     */
    public static BreakerRef nestedRemember() {
        return BreakerRef.of(PATHWAY_REMEMBER, CircuitBreakerConfig.inProcess(), OnOpen.BYPASS);
    }

    // ── Bulkheads (ADR-0036 §10) ────────────────────────────────────────────

    /**
     * Concurrency cap on nested Remember work.
     *
     * <p>Exists so a sleep cycle cannot enqueue hundreds of nested writes on top of
     * live user-facing Recall. Virtual threads make this a logical cap, not a pool.
     * User-facing {@code memory.remember(...)} does not pass through this bulkhead.</p>
     *
     * @return bulkhead config: 4 permits, 50ms wait, bypass on reject
     */
    public static BulkheadConfig nestedRememberBulkhead() {
        return BulkheadConfig.of(4, Duration.ofMillis(50), OnReject.BYPASS);
    }

    /**
     * Concurrency cap on LLM calls — these are the most expensive and most likely to
     * queue. Non-blocking reject: if two are already in flight, skip.
     *
     * @return bulkhead config: 2 permits, no wait, bypass on reject
     */
    public static BulkheadConfig llmBulkhead() {
        return BulkheadConfig.of(2, Duration.ZERO, OnReject.BYPASS);
    }
}
