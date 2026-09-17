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
package com.spectrayan.spector.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the dedicated gateway router (ADR-0031, ADR-0081 §8 Phase 2).
 */
@ConfigurationProperties(prefix = "spector")
public class GatewayProperties {

    private CellProperties cell = new CellProperties();
    private RoutingProperties routing = new RoutingProperties();
    private AuthProperties auth = new AuthProperties();

    public CellProperties getCell() {
        return cell;
    }

    public void setCell(CellProperties cell) {
        this.cell = cell;
    }

    public RoutingProperties getRouting() {
        return routing;
    }

    public void setRouting(RoutingProperties routing) {
        this.routing = routing;
    }

    public AuthProperties getAuth() {
        return auth;
    }

    public void setAuth(AuthProperties auth) {
        this.auth = auth;
    }

    public static class CellProperties {
        private String id = "default";
        private String role = "gateway";
        private String nodeId = "gateway-1";
        private String ringMembers = "";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getRingMembers() {
            return ringMembers;
        }

        public void setRingMembers(String ringMembers) {
            this.ringMembers = ringMembers;
        }
    }

    public static class RoutingProperties {
        private CaffeineProperties caffeine = new CaffeineProperties();
        private RedisProperties redis = new RedisProperties();
        private GatewayRoutingProperties gateway = new GatewayRoutingProperties();

        public CaffeineProperties getCaffeine() {
            return caffeine;
        }

        public void setCaffeine(CaffeineProperties caffeine) {
            this.caffeine = caffeine;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public void setRedis(RedisProperties redis) {
            this.redis = redis;
        }

        public GatewayRoutingProperties getGateway() {
            return gateway;
        }

        public void setGateway(GatewayRoutingProperties gateway) {
            this.gateway = gateway;
        }
    }

    public static class CaffeineProperties {
        private long ttlSeconds = 60;
        private long maxSize = 1000;

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

    public static class RedisProperties {
        private boolean enabled = true;
        private String uri = "redis://localhost:6379";
        private long ttlSeconds = 30;
        private long timeoutMs = 200;

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

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class GatewayRoutingProperties {
        private int retryMax = 2;
        private int maxBufferedBodyBytes = 10 * 1024 * 1024; // 10 MiB
        private Duration ownerTimeout = Duration.ofSeconds(10);

        public int getRetryMax() {
            return retryMax;
        }

        public void setRetryMax(int retryMax) {
            this.retryMax = retryMax;
        }

        public int getMaxBufferedBodyBytes() {
            return maxBufferedBodyBytes;
        }

        public void setMaxBufferedBodyBytes(int maxBufferedBodyBytes) {
            this.maxBufferedBodyBytes = maxBufferedBodyBytes;
        }

        public Duration getOwnerTimeout() {
            return ownerTimeout;
        }

        public void setOwnerTimeout(Duration ownerTimeout) {
            this.ownerTimeout = ownerTimeout;
        }
    }

    public static class AuthProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
