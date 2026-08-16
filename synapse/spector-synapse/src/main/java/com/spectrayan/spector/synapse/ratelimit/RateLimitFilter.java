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
package com.spectrayan.spector.synapse.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.config.properties.RateLimitProperties;
import com.spectrayan.spector.config.properties.RateLimitProperties.EndpointPolicy;
import com.spectrayan.spector.config.properties.RateLimitProperties.TierPolicy;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Spring Security / Web filter that enforces Bucket4j token bucket rate limiting
 * across all inbound HTTP and MCP requests.
 *
 * <p>Emits RFC 7807 problem details with {@code 429 Too Many Requests} and standard
 * {@code Retry-After} and {@code X-RateLimit-*} headers when limits are exceeded.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SynapseProperties properties;
    private final RateLimitStateStore stateStore;
    private final RateLimitKeyResolver keyResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(SynapseProperties properties,
                           RateLimitStateStore stateStore,
                           RateLimitKeyResolver keyResolver) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        RateLimitProperties rateLimitConfig = properties.getRateLimit();
        if (rateLimitConfig == null || !rateLimitConfig.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 1. Check exclusions (e.g. /actuator/health, static assets)
        if (isExcluded(path, rateLimitConfig.getExcludedPaths())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Resolve caller identity & tier
        RateLimitKey key = keyResolver.resolveKey(request);

        // 3. Resolve applicable policy (endpoint custom rule vs tier policy)
        Bandwidth bandwidth = resolveBandwidth(path, key, rateLimitConfig);
        long capacity = bandwidth.getCapacity();

        // 4. Resolve Bucket from Store
        String bucketKey = key.cacheKey() + ":" + resolvePathCategory(path, rateLimitConfig);
        Bucket bucket = stateStore.resolveBucket(bucketKey, bandwidth);

        // 5. Consume token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            long remaining = probe.getRemainingTokens();
            long nanosToRefill = probe.getNanosToWaitForRefill();
            long resetSeconds = Instant.now().getEpochSecond() + Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanosToRefill));

            response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));

            filterChain.doFilter(request, response);
        } else {
            long nanosToWait = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L);
            long resetSeconds = Instant.now().getEpochSecond() + retryAfterSeconds;

            log.warn("[RateLimiter] Rate limit exceeded for key='{}' path='{}' (wait={}s)",
                    key.cacheKey(), path, retryAfterSeconds);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("type", "urn:spector:error:rate-limit-exceeded");
            errorBody.put("title", "Too Many Requests");
            errorBody.put("status", 429);
            errorBody.put("detail", String.format(
                    "Rate limit exceeded for %s. Please retry after %d seconds.",
                    key.type().name().toLowerCase() + ":" + key.value(),
                    retryAfterSeconds
            ));
            errorBody.put("instance", path);
            errorBody.put("retryAfterSeconds", retryAfterSeconds);

            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(errorBody));
        }
    }

    private boolean isExcluded(String path, List<String> excludedPatterns) {
        if (excludedPatterns == null) return false;
        for (String pattern : excludedPatterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Bandwidth resolveBandwidth(String path, RateLimitKey key, RateLimitProperties config) {
        // Check endpoint-specific policy
        if (config.getEndpoints() != null) {
            for (EndpointPolicy ep : config.getEndpoints()) {
                if (ep.getPathPattern() != null && pathMatcher.match(ep.getPathPattern(), path)) {
                    return Bandwidth.classic(
                            ep.getBurstCapacity(),
                            Refill.greedy(ep.getRequestsPerMinute(), Duration.ofMinutes(1))
                    );
                }
            }
        }

        // Fall back to Tier policy
        String tierName = key.tier();
        TierPolicy tierPolicy = config.getTiers() != null ? config.getTiers().get(tierName) : null;
        if (tierPolicy == null) {
            String defaultTier = config.getDefaultTier() != null ? config.getDefaultTier() : "standard";
            tierPolicy = config.getTiers() != null ? config.getTiers().get(defaultTier) : null;
        }

        if (tierPolicy != null) {
            return Bandwidth.classic(
                    tierPolicy.getBurstCapacity(),
                    Refill.greedy(tierPolicy.getRequestsPerSecond(), Duration.ofSeconds(1))
            );
        }

        // Failsafe default: 100 rps, burst 200
        return Bandwidth.classic(200, Refill.greedy(100, Duration.ofSeconds(1)));
    }

    private String resolvePathCategory(String path, RateLimitProperties config) {
        if (config.getEndpoints() != null) {
            for (EndpointPolicy ep : config.getEndpoints()) {
                if (ep.getPathPattern() != null && pathMatcher.match(ep.getPathPattern(), path)) {
                    return ep.getPathPattern();
                }
            }
        }
        return "global";
    }

}
