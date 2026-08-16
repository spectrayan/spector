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
package com.spectrayan.spector.synapse.provider;

import com.spectrayan.spector.config.properties.RateLimitProperties.LlmProviderPolicy;
import com.spectrayan.spector.config.properties.RateLimitProperties.LlmRateLimitProperties;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.ChatMessage;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Resilient, rate-limited decorator for {@link LlmProvider} instances.
 *
 * <p>Enforces dual-dimension token bucket rate limiting (RPM + TPM with pre-flight estimation
 * and post-call settlement), concurrency semaphores, and jittered exponential retry backoff.</p>
 */
public class ResilientRateLimitedLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimitedLlmProvider.class);

    private final LlmProvider delegate;
    private final SynapseProperties properties;

    private final Map<String, Bucket> rpmBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> tpmBuckets = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    public ResilientRateLimitedLlmProvider(LlmProvider delegate, SynapseProperties properties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate provider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public LlmResponse generate(LlmRequest request, GenerationOptions options) {
        LlmRateLimitProperties config = properties.getRateLimit() != null ? properties.getRateLimit().getLlm() : null;
        if (config == null || !config.isEnabled()) {
            return delegate.generate(request, options);
        }

        String model = modelName();
        LlmProviderPolicy policy = resolvePolicy(model, config);
        long queueTimeoutMs = config.getQueueTimeoutMs();

        // 1. Dual-dimension Bucket4j checks (RPM + TPM)
        Bucket rpmBucket = getRpmBucket(model, policy);
        Bucket tpmBucket = getTpmBucket(model, policy);
        Semaphore semaphore = getSemaphore(model, policy);

        long estimatedTokens = estimateTokens(request, options);

        try {
            // Wait for RPM token
            boolean rpmAcquired = rpmBucket.asBlocking().tryConsume(1, Duration.ofMillis(queueTimeoutMs));
            if (!rpmAcquired) {
                throw new GenerationException("LLM RPM rate limit exceeded for model '" + model + "'");
            }

            // Wait for TPM tokens
            boolean tpmAcquired = tpmBucket.asBlocking().tryConsume(estimatedTokens, Duration.ofMillis(queueTimeoutMs));
            if (!tpmAcquired) {
                // Refund RPM token
                rpmBucket.addTokens(1);
                throw new GenerationException("LLM TPM token limit exceeded for model '" + model + "'");
            }

            // Concurrency bulkhead
            boolean permitAcquired = semaphore.tryAcquire(queueTimeoutMs, TimeUnit.MILLISECONDS);
            if (!permitAcquired) {
                rpmBucket.addTokens(1);
                tpmBucket.addTokens(estimatedTokens);
                throw new GenerationException("LLM concurrency limit reached for model '" + model + "'");
            }

            try {
                LlmResponse response = executeWithRetry(request, options, config.getMaxRetries());

                // Post-call reconciliation
                reconcileTokens(tpmBucket, estimatedTokens, response);

                return response;
            } finally {
                semaphore.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationException("LLM generation interrupted while waiting for rate limit tokens", e);
        }
    }

    @Override
    public String generate(String prompt) {
        return generate(prompt, GenerationOptions.DEFAULT);
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        LlmRequest req = LlmRequest.fromPrompt(prompt);
        LlmResponse resp = generate(req, options);
        return resp.text();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    private LlmResponse executeWithRetry(LlmRequest request, GenerationOptions options, int maxRetries) {
        int attempt = 0;
        long backoffMs = 500;

        while (true) {
            try {
                return delegate.generate(request, options);
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    throw (e instanceof GenerationException ge) ? ge : new GenerationException("LLM call failed after " + attempt + " attempts: " + e.getMessage(), e);
                }

                log.warn("[ResilientLLM] Upstream error on attempt {}/{}, backing off {}ms: {}",
                        attempt, maxRetries, backoffMs, e.getMessage());

                try {
                    // Full jitter exponential backoff
                    long jitter = (long) (Math.random() * backoffMs);
                    Thread.sleep(backoffMs + jitter);
                    backoffMs = Math.min(backoffMs * 2, 5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GenerationException("Interrupted during retry backoff", ie);
                }
            }
        }
    }

    private void reconcileTokens(Bucket tpmBucket, long estimatedTokens, LlmResponse response) {
        if (response == null) return;
        long actualTokens = response.inputTokens() + response.outputTokens();

        if (actualTokens > 0) {
            long diff = actualTokens - estimatedTokens;
            if (diff > 0) {
                // Debit extra consumed tokens
                tpmBucket.tryConsume(diff);
            } else if (diff < 0) {
                // Refund over-estimated tokens
                tpmBucket.addTokens(-diff);
            }
        }
    }

    private long estimateTokens(LlmRequest request, GenerationOptions options) {
        long charCount = 0;
        if (request != null && request.messages() != null) {
            for (ChatMessage msg : request.messages()) {
                if (msg != null && msg.text() != null) {
                    charCount += msg.text().length();
                }
            }
        }
        long estimatedPromptTokens = Math.max(10, charCount / 4);
        long maxOutputTokens = (options != null && options.maxTokens() > 0) ? options.maxTokens() : 1000;
        return estimatedPromptTokens + maxOutputTokens;
    }

    private LlmProviderPolicy resolvePolicy(String model, LlmRateLimitProperties config) {
        if (config.getProviders() != null) {
            String lower = model != null ? model.toLowerCase() : "";
            for (Map.Entry<String, LlmProviderPolicy> entry : config.getProviders().entrySet()) {
                if (lower.contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }
        return config.getDefaultPolicy();
    }

    private Bucket getRpmBucket(String model, LlmProviderPolicy policy) {
        return rpmBuckets.computeIfAbsent(model, m ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(policy.requestsPerMinute(),
                                Refill.greedy(policy.requestsPerMinute(), Duration.ofMinutes(1))))
                        .build());
    }

    private Bucket getTpmBucket(String model, LlmProviderPolicy policy) {
        return tpmBuckets.computeIfAbsent(model, m ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(policy.tokensPerMinute(),
                                Refill.greedy(policy.tokensPerMinute(), Duration.ofMinutes(1))))
                        .build());
    }

    private Semaphore getSemaphore(String model, LlmProviderPolicy policy) {
        return semaphores.computeIfAbsent(model, m ->
                new Semaphore(policy.maxConcurrentCalls(), true));
    }
}
