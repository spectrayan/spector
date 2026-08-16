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
            "/actuator/info",
            "/actuator/prometheus",
            "/health",
            "/index.html",
            "/assets/**",
            "/*.js",
            "/*.css",
            "/*.ico",
            "/*.png",
            "/*.svg"
    ));

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

        public EndpointPolicy() {}

        public EndpointPolicy(String pathPattern, int requestsPerMinute, int burstCapacity) {
            this.pathPattern = pathPattern;
            this.requestsPerMinute = Math.max(1, requestsPerMinute);
            this.burstCapacity = Math.max(1, burstCapacity);
        }

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int rpm) { if (rpm > 0) this.requestsPerMinute = rpm; }

        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burst) { if (burst > 0) this.burstCapacity = burst; }

        public String pathPattern() { return getPathPattern(); }
        public int requestsPerMinute() { return getRequestsPerMinute(); }
        public int burstCapacity() { return getBurstCapacity(); }
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
