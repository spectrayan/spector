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
package com.spectrayan.spector.synapse.config.routing;

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;

/**
 * Configuration properties for the Cell Routing Cache, Pub/Sub Invalidation, and Gateway Resilience
 * (ADR-0034 §8, §15.6, Phase 2, Req R1, R3, R5, R8).
 *
 * <p>Prefix: {@code spector.routing.*}</p>
 */
public class RoutingProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private RedisProperties redis = new RedisProperties();
    private CaffeineProperties caffeine = new CaffeineProperties();
    private GatewayProperties gateway = new GatewayProperties();

    public RoutingProperties() {}

    public RedisProperties getRedis() {
        return redis;
    }

    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    public CaffeineProperties getCaffeine() {
        return caffeine;
    }

    public void setCaffeine(CaffeineProperties caffeine) {
        this.caffeine = caffeine;
    }

    public GatewayProperties getGateway() {
        return gateway;
    }

    public void setGateway(GatewayProperties gateway) {
        this.gateway = gateway;
    }

    public static class RedisProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_ENABLED;
        private String uri = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_URI;
        private long timeoutMs = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_TIMEOUT_MS;
        private long ttlSeconds = SpectorPropertyConstants.DEFAULT_ROUTING_REDIS_TTL_SECONDS;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class CaffeineProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private long ttlSeconds = SpectorPropertyConstants.DEFAULT_ROUTING_CAFFEINE_TTL_SECONDS;
        private long maxSize = SpectorPropertyConstants.DEFAULT_ROUTING_CAFFEINE_MAX_SIZE;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class GatewayProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private int retryMax = SpectorPropertyConstants.DEFAULT_ROUTING_GATEWAY_RETRY_MAX;

        public int getRetryMax() {
            return retryMax;
        }

        public void setRetryMax(int retryMax) {
            this.retryMax = retryMax;
        }
    }
}
