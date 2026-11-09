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
package com.spectrayan.spector.config.properties;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties POJO for unified rate limiting and traffic shaping across Spector.
 *
 * <p>Supports multi-tiered API rate limits, per-endpoint custom rules, outbound LLM token/request
 * throttling, Camel connector throttling, and messaging channel anti-flood protections.</p>
 */
public class RateLimitProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private String backend = "in-memory"; // "in-memory" or "redis"
    private String redisUri = "redis://localhost:6379/0";
    private String defaultTier = "standard";

    private Map<String, TierPolicy> tiers = new HashMap<>();
    private List<EndpointPolicy> endpoints = new ArrayList<>();
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/metrics/**",
            "/actuator/prometheus",
            "/actuator/ratelimits",
            "/actuator/ratelimits/**",
            "/health",
            "/health/**",
            "/api/v1/system/health",
            "/api/v1/system/metrics",
            "/index.html",
            "/assets/**",
            "/*.js",
            "/*.css",
            "/*.ico",
            "/*.png",
            "/*.svg"
    ));

    private AdmissionControlProperties admission = new AdmissionControlProperties();
    private LlmRateLimitProperties llm = new LlmRateLimitProperties();
    private ChannelRateLimitProperties channels = new ChannelRateLimitProperties();
    private ConnectorRateLimitProperties connectors = new ConnectorRateLimitProperties();

    public RateLimitProperties() {
        initDefaultTiers();
    }

    private void initDefaultTiers() {
        tiers.put("anonymous", new TierPolicy(10, 20));
        tiers.put("standard", new TierPolicy(100, 200));
        tiers.put("premium", new TierPolicy(500, 1000));
        tiers.put("system", new TierPolicy(2000, 5000));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBackend() { return backend; }
    public void setBackend(String backend) { if (backend != null) this.backend = backend; }

    public String getRedisUri() { return redisUri; }
    public void setRedisUri(String redisUri) { if (redisUri != null) this.redisUri = redisUri; }

    public String getDefaultTier() { return defaultTier; }
    public void setDefaultTier(String defaultTier) { if (defaultTier != null) this.defaultTier = defaultTier; }

    public Map<String, TierPolicy> getTiers() { return tiers; }
    public void setTiers(Map<String, TierPolicy> tiers) { if (tiers != null) this.tiers = tiers; }

    public List<EndpointPolicy> getEndpoints() { return endpoints; }
    public void setEndpoints(List<EndpointPolicy> endpoints) { if (endpoints != null) this.endpoints = endpoints; }

    public List<String> getExcludedPaths() { return excludedPaths; }
    public void setExcludedPaths(List<String> excludedPaths) { if (excludedPaths != null) this.excludedPaths = excludedPaths; }

    public AdmissionControlProperties getAdmission() { return admission; }
    public void setAdmission(AdmissionControlProperties admission) { if (admission != null) this.admission = admission; }

    public LlmRateLimitProperties getLlm() { return llm; }
    public void setLlm(LlmRateLimitProperties llm) { if (llm != null) this.llm = llm; }

    public ChannelRateLimitProperties getChannels() { return channels; }
    public void setChannels(ChannelRateLimitProperties channels) { if (channels != null) this.channels = channels; }

    public ConnectorRateLimitProperties getConnectors() { return connectors; }
    public void setConnectors(ConnectorRateLimitProperties connectors) { if (connectors != null) this.connectors = connectors; }

    // Compatibility record-style accessors
    public boolean enabled() { return isEnabled(); }
    public String backend() { return getBackend(); }
    public String defaultTier() { return getDefaultTier(); }
    public Map<String, TierPolicy> tiers() { return getTiers(); }
    public List<EndpointPolicy> endpoints() { return getEndpoints(); }
    public List<String> excludedPaths() { return getExcludedPaths(); }
    public AdmissionControlProperties admission() { return getAdmission(); }
    public LlmRateLimitProperties llm() { return getLlm(); }
    public ChannelRateLimitProperties channels() { return getChannels(); }
    public ConnectorRateLimitProperties connectors() { return getConnectors(); }

    /**
     * Tier rate limit policy definition.
     */
    public static class TierPolicy implements Serializable {
        private static final long serialVersionUID = 1L;

        private int requestsPerSecond = 100;
        private int burstCapacity = 200;

        public TierPolicy() {}

        public TierPolicy(int requestsPerSecond, int burstCapacity) {
            this.requestsPerSecond = Math.max(1, requestsPerSecond);
            this.burstCapacity = Math.max(this.requestsPerSecond, burstCapacity);
        }

        public int getRequestsPerSecond() { return requestsPerSecond; }
        public void setRequestsPerSecond(int rps) { if (rps > 0) this.requestsPerSecond = rps; }

        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burst) { if (burst > 0) this.burstCapacity = burst; }

        public int requestsPerSecond() { return getRequestsPerSecond(); }
        public int burstCapacity() { return getBurstCapacity(); }
    }

    /**
     * Specific endpoint rate limit policy.
     */
    public static class EndpointPolicy implements Serializable {
        private static final long serialVersionUID = 1L;

        private String pathPattern;
        private int requestsPerMinute = 60;
        private int burstCapacity = 10;
        private int maxConcurrentRequests = 0;

        public EndpointPolicy() {}

        public EndpointPolicy(String pathPattern, int requestsPerMinute, int burstCapacity) {
            this.pathPattern = pathPattern;
            this.requestsPerMinute = Math.max(1, requestsPerMinute);
            this.burstCapacity = Math.max(1, burstCapacity);
        }

        public EndpointPolicy(String pathPattern, int requestsPerMinute, int burstCapacity, int maxConcurrentRequests) {
            this(pathPattern, requestsPerMinute, burstCapacity);
            this.maxConcurrentRequests = Math.max(0, maxConcurrentRequests);
        }

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int rpm) { if (rpm > 0) this.requestsPerMinute = rpm; }

        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burst) { if (burst > 0) this.burstCapacity = burst; }

        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = Math.max(0, maxConcurrentRequests);
        }

        public String pathPattern() { return getPathPattern(); }
        public int requestsPerMinute() { return getRequestsPerMinute(); }
        public int burstCapacity() { return getBurstCapacity(); }
        public int maxConcurrentRequests() { return getMaxConcurrentRequests(); }
    }

    /**
     * In-flight concurrency and JVM heap / off-heap memory-pressure admission control settings.
     */
    public static class AdmissionControlProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private int maxConcurrentRequests = 256;
        private int writeMaxConcurrentRequests = 64;
        private int recallMaxConcurrentRequests = 128;
        private int managementMaxConcurrentRequests = 32;
        private int retryAfterSeconds = 2;

        private boolean memoryPressureEnabled = true;
        private double heapHighWaterMarkRatio = 0.95;
        private double heapRecoveryRatio = 0.85;
        private boolean heapRecoveryRatioExplicit = false;

        private double offHeapHighWaterMarkRatio = 0.95;
        private double offHeapRecoveryRatio = 0.85;
        private boolean offHeapRecoveryRatioExplicit = false;

        private long maxOffHeapBytes = 0L;
        private long heapHighWaterMarkBytes = 0L;
        private long heapRecoveryBytes = 0L;
        private long offHeapHighWaterMarkBytes = 0L;
        private long offHeapRecoveryBytes = 0L;

        private List<RouteConcurrencyPolicy> routes = new ArrayList<>();
        private Map<String, Integer> routeLimits = new HashMap<>();
        private List<String> exemptPaths = new ArrayList<>(List.of(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/metrics",
                "/actuator/metrics/**",
                "/actuator/prometheus",
                "/actuator/ratelimits",
                "/actuator/ratelimits/**",
                "/health",
                "/health/**",
                "/api/v1/system/health",
                "/api/v1/system/metrics"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) {
            this.maxConcurrentRequests = Math.max(0, maxConcurrentRequests);
        }

        public int getWriteMaxConcurrentRequests() { return writeMaxConcurrentRequests; }
        public void setWriteMaxConcurrentRequests(int writeMaxConcurrentRequests) {
            this.writeMaxConcurrentRequests = Math.max(0, writeMaxConcurrentRequests);
        }

        public int getRecallMaxConcurrentRequests() { return recallMaxConcurrentRequests; }
        public void setRecallMaxConcurrentRequests(int recallMaxConcurrentRequests) {
            this.recallMaxConcurrentRequests = Math.max(0, recallMaxConcurrentRequests);
        }

        public int getManagementMaxConcurrentRequests() { return managementMaxConcurrentRequests; }
        public void setManagementMaxConcurrentRequests(int managementMaxConcurrentRequests) {
            this.managementMaxConcurrentRequests = Math.max(0, managementMaxConcurrentRequests);
        }

        public int getRetryAfterSeconds() { return retryAfterSeconds; }
        public void setRetryAfterSeconds(int retryAfterSeconds) {
            this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        }

        public boolean isMemoryPressureEnabled() { return memoryPressureEnabled; }
        public void setMemoryPressureEnabled(boolean memoryPressureEnabled) {
            this.memoryPressureEnabled = memoryPressureEnabled;
        }

        public double getHeapHighWaterMarkRatio() { return heapHighWaterMarkRatio; }
        public void setHeapHighWaterMarkRatio(double heapHighWaterMarkRatio) {
            this.heapHighWaterMarkRatio = clampRatio(heapHighWaterMarkRatio);
        }

        public double getHeapRecoveryRatio() { return heapRecoveryRatio; }
        public void setHeapRecoveryRatio(double heapRecoveryRatio) {
            this.heapRecoveryRatio = clampRatio(heapRecoveryRatio);
            this.heapRecoveryRatioExplicit = true;
        }

        public double effectiveHeapRecoveryRatio() {
            if (heapRecoveryRatioExplicit) {
                return Math.min(heapRecoveryRatio, heapHighWaterMarkRatio);
            }
            return Math.min(heapRecoveryRatio, Math.max(0.0, heapHighWaterMarkRatio - 0.05));
        }

        public double getOffHeapHighWaterMarkRatio() { return offHeapHighWaterMarkRatio; }
        public void setOffHeapHighWaterMarkRatio(double offHeapHighWaterMarkRatio) {
            this.offHeapHighWaterMarkRatio = clampRatio(offHeapHighWaterMarkRatio);
        }

        public double getOffHeapRecoveryRatio() { return offHeapRecoveryRatio; }
        public void setOffHeapRecoveryRatio(double offHeapRecoveryRatio) {
            this.offHeapRecoveryRatio = clampRatio(offHeapRecoveryRatio);
            this.offHeapRecoveryRatioExplicit = true;
        }

        public double effectiveOffHeapRecoveryRatio() {
            if (offHeapRecoveryRatioExplicit) {
                return Math.min(offHeapRecoveryRatio, offHeapHighWaterMarkRatio);
            }
            return Math.min(offHeapRecoveryRatio, Math.max(0.0, offHeapHighWaterMarkRatio - 0.05));
        }

        public long getMaxOffHeapBytes() { return maxOffHeapBytes; }
        public void setMaxOffHeapBytes(long maxOffHeapBytes) {
            this.maxOffHeapBytes = Math.max(0L, maxOffHeapBytes);
        }

        public long getHeapHighWaterMarkBytes() { return heapHighWaterMarkBytes; }
        public void setHeapHighWaterMarkBytes(long heapHighWaterMarkBytes) {
            this.heapHighWaterMarkBytes = Math.max(0L, heapHighWaterMarkBytes);
        }

        public long getHeapRecoveryBytes() { return heapRecoveryBytes; }
        public void setHeapRecoveryBytes(long heapRecoveryBytes) {
            this.heapRecoveryBytes = Math.max(0L, heapRecoveryBytes);
        }

        public long effectiveHeapRecoveryBytes() {
            if (heapHighWaterMarkBytes <= 0L) {
                return 0L;
            }
            if (heapRecoveryBytes > 0L) {
                return Math.min(heapRecoveryBytes, heapHighWaterMarkBytes);
            }
            return (long) (heapHighWaterMarkBytes * 0.90);
        }

        public long getOffHeapHighWaterMarkBytes() { return offHeapHighWaterMarkBytes; }
        public void setOffHeapHighWaterMarkBytes(long offHeapHighWaterMarkBytes) {
            this.offHeapHighWaterMarkBytes = Math.max(0L, offHeapHighWaterMarkBytes);
        }

        public long getOffHeapRecoveryBytes() { return offHeapRecoveryBytes; }
        public void setOffHeapRecoveryBytes(long offHeapRecoveryBytes) {
            this.offHeapRecoveryBytes = Math.max(0L, offHeapRecoveryBytes);
        }

        public long effectiveOffHeapRecoveryBytes() {
            if (offHeapHighWaterMarkBytes <= 0L) {
                return 0L;
            }
            if (offHeapRecoveryBytes > 0L) {
                return Math.min(offHeapRecoveryBytes, offHeapHighWaterMarkBytes);
            }
            return (long) (offHeapHighWaterMarkBytes * 0.90);
        }

        public List<RouteConcurrencyPolicy> getRoutes() { return routes; }
        public void setRoutes(List<RouteConcurrencyPolicy> routes) {
            if (routes != null) this.routes = routes;
        }

        public Map<String, Integer> getRouteLimits() { return routeLimits; }
        public void setRouteLimits(Map<String, Integer> routeLimits) {
            if (routeLimits != null) this.routeLimits = routeLimits;
        }

        public List<String> getExemptPaths() { return exemptPaths; }
        public void setExemptPaths(List<String> exemptPaths) {
            if (exemptPaths != null) this.exemptPaths = exemptPaths;
        }

        public boolean enabled() { return isEnabled(); }
        public int maxConcurrentRequests() { return getMaxConcurrentRequests(); }
        public int writeMaxConcurrentRequests() { return getWriteMaxConcurrentRequests(); }
        public int recallMaxConcurrentRequests() { return getRecallMaxConcurrentRequests(); }
        public int managementMaxConcurrentRequests() { return getManagementMaxConcurrentRequests(); }
        public int retryAfterSeconds() { return getRetryAfterSeconds(); }
        public boolean memoryPressureEnabled() { return isMemoryPressureEnabled(); }
        public double heapHighWaterMarkRatio() { return getHeapHighWaterMarkRatio(); }
        public double heapRecoveryRatio() { return getHeapRecoveryRatio(); }
        public double offHeapHighWaterMarkRatio() { return getOffHeapHighWaterMarkRatio(); }
        public double offHeapRecoveryRatio() { return getOffHeapRecoveryRatio(); }
        public long maxOffHeapBytes() { return getMaxOffHeapBytes(); }
        public List<RouteConcurrencyPolicy> routes() { return getRoutes(); }
        public Map<String, Integer> routeLimits() { return getRouteLimits(); }
        public List<String> exemptPaths() { return getExemptPaths(); }

        private static double clampRatio(double ratio) {
            if (Double.isNaN(ratio) || ratio < 0.0) {
                return 0.0;
            }
            return Math.min(1.0, ratio);
        }
    }

    /**
     * Per-route in-flight concurrency limit definition.
     */
    public static class RouteConcurrencyPolicy implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String pathPattern;
        private List<String> methods = new ArrayList<>();
        private int maxConcurrentRequests = 64;

        public RouteConcurrencyPolicy() {}

        public RouteConcurrencyPolicy(String pathPattern, int maxConcurrentRequests) {
            this(pathPattern, pathPattern, List.of(), maxConcurrentRequests);
        }

        public RouteConcurrencyPolicy(String name, String pathPattern, int maxConcurrentRequests) {
            this(name, pathPattern, List.of(), maxConcurrentRequests);
        }

        public RouteConcurrencyPolicy(String name, String pathPattern, List<String> methods, int maxConcurrentRequests) {
            this.name = name;
            this.pathPattern = pathPattern;
            if (methods != null) {
                this.methods = new ArrayList<>(methods);
            }
            this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) {
            if (methods != null) this.methods = methods;
        }

        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int maxConcurrentRequests) {
            if (maxConcurrentRequests > 0) {
                this.maxConcurrentRequests = maxConcurrentRequests;
            }
        }

        public String name() { return getName(); }
        public String pathPattern() { return getPathPattern(); }
        public List<String> methods() { return getMethods(); }
        public int maxConcurrentRequests() { return getMaxConcurrentRequests(); }
    }

    /**
     * Outbound LLM provider rate limit settings (RPM, TPM, Concurrency, Queue).
     */
    public static class LlmRateLimitProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private long queueTimeoutMs = 5000;
        private int maxRetries = 3;
        private LlmProviderPolicy defaultPolicy = new LlmProviderPolicy(60, 100_000, 10);
        private Map<String, LlmProviderPolicy> providers = new HashMap<>();

        public LlmRateLimitProperties() {
            providers.put("openai", new LlmProviderPolicy(500, 200_000, 20));
            providers.put("anthropic", new LlmProviderPolicy(300, 150_000, 15));
            providers.put("gemini", new LlmProviderPolicy(300, 200_000, 15));
            providers.put("ollama", new LlmProviderPolicy(60, 50_000, 2));
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getQueueTimeoutMs() { return queueTimeoutMs; }
        public void setQueueTimeoutMs(long queueTimeoutMs) { this.queueTimeoutMs = queueTimeoutMs; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public LlmProviderPolicy getDefaultPolicy() { return defaultPolicy; }
        public void setDefaultPolicy(LlmProviderPolicy defaultPolicy) { if (defaultPolicy != null) this.defaultPolicy = defaultPolicy; }

        public Map<String, LlmProviderPolicy> getProviders() { return providers; }
        public void setProviders(Map<String, LlmProviderPolicy> providers) { if (providers != null) this.providers = providers; }

        public boolean enabled() { return isEnabled(); }
        public long queueTimeoutMs() { return getQueueTimeoutMs(); }
        public int maxRetries() { return getMaxRetries(); }
        public LlmProviderPolicy defaultPolicy() { return getDefaultPolicy(); }
        public Map<String, LlmProviderPolicy> providers() { return getProviders(); }
    }

    /**
     * Policy for a specific LLM provider.
     */
    public static class LlmProviderPolicy implements Serializable {
        private static final long serialVersionUID = 1L;

        private int requestsPerMinute = 60;
        private long tokensPerMinute = 100_000;
        private int maxConcurrentCalls = 10;

        public LlmProviderPolicy() {}

        public LlmProviderPolicy(int requestsPerMinute, long tokensPerMinute, int maxConcurrentCalls) {
            this.requestsPerMinute = Math.max(1, requestsPerMinute);
            this.tokensPerMinute = Math.max(1000, tokensPerMinute);
            this.maxConcurrentCalls = Math.max(1, maxConcurrentCalls);
        }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int rpm) { if (rpm > 0) this.requestsPerMinute = rpm; }

        public long getTokensPerMinute() { return tokensPerMinute; }
        public void setTokensPerMinute(long tpm) { if (tpm > 0) this.tokensPerMinute = tpm; }

        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public void setMaxConcurrentCalls(int maxConcurrentCalls) { if (maxConcurrentCalls > 0) this.maxConcurrentCalls = maxConcurrentCalls; }

        public int requestsPerMinute() { return getRequestsPerMinute(); }
        public long tokensPerMinute() { return getTokensPerMinute(); }
        public int maxConcurrentCalls() { return getMaxConcurrentCalls(); }
    }

    /**
     * Messaging channel rate limit properties (inbound anti-flood + outbound pacing).
     */
    public static class ChannelRateLimitProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private int inboundUserRpm = 30;
        private int inboundUserBurst = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getInboundUserRpm() { return inboundUserRpm; }
        public void setInboundUserRpm(int rpm) { if (rpm > 0) this.inboundUserRpm = rpm; }

        public int getInboundUserBurst() { return inboundUserBurst; }
        public void setInboundUserBurst(int burst) { if (burst > 0) this.inboundUserBurst = burst; }

        public boolean enabled() { return isEnabled(); }
        public int inboundUserRpm() { return getInboundUserRpm(); }
        public int inboundUserBurst() { return getInboundUserBurst(); }
    }

    /**
     * Camel connector rate limit properties.
     */
    public static class ConnectorRateLimitProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private int defaultThrottleRequests = 100;
        private long defaultThrottlePeriodMs = 60000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getDefaultThrottleRequests() { return defaultThrottleRequests; }
        public void setDefaultThrottleRequests(int reqs) { if (reqs > 0) this.defaultThrottleRequests = reqs; }

        public long getDefaultThrottlePeriodMs() { return defaultThrottlePeriodMs; }
        public void setDefaultThrottlePeriodMs(long ms) { if (ms > 0) this.defaultThrottlePeriodMs = ms; }

        public boolean enabled() { return isEnabled(); }
        public int defaultThrottleRequests() { return getDefaultThrottleRequests(); }
        public long defaultThrottlePeriodMs() { return getDefaultThrottlePeriodMs(); }
    }
}
