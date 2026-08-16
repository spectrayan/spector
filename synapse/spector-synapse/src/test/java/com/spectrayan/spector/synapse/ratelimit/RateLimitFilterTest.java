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

import com.spectrayan.spector.config.properties.RateLimitProperties.EndpointPolicy;
import com.spectrayan.spector.config.properties.RateLimitProperties.TierPolicy;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private SynapseProperties properties;
    private CaffeineRateLimitStateStore stateStore;
    private RateLimitKeyResolver keyResolver;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SynapseProperties();
        properties.getRateLimit().setEnabled(true);
        // Set low limits for deterministic unit testing
        properties.getRateLimit().getTiers().put("anonymous", new TierPolicy(2, 3));
        properties.getRateLimit().getTiers().put("standard", new TierPolicy(5, 5));
        properties.getRateLimit().getTiers().put("system", new TierPolicy(100, 100));

        stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        keyResolver = new RateLimitKeyResolver(properties);
        filter = new RateLimitFilter(properties, stateStore, keyResolver);
    }

    @Test
    @DisplayName("Should permit requests within burst limit and reject with 429 when exceeded")
    void testRateLimitingExhaustionAnd429() throws ServletException, IOException {
        String clientIp = "192.168.1.100";

        // First 3 requests should succeed (burst capacity = 3)
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/memories");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("3");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
        }

        // 4th request must be rejected with 429 Too Many Requests
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/memories");
        request.setRemoteAddr(clientIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"urn:spector:error:rate-limit-exceeded\"")
                .contains("\"status\":429");
    }

    @Test
    @DisplayName("Should bypass excluded paths like actuator and health endpoints")
    void testExcludedPathsBypass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Should apply custom endpoint policies over general tier limits")
    void testCustomEndpointPolicy() throws ServletException, IOException {
        properties.getRateLimit().setEndpoints(List.of(
                new EndpointPolicy("/api/v1/auth/**", 2, 2)
        ));

        String ip = "172.16.0.5";

        // 2 requests allowed
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(ip);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, new MockFilterChain());
            assertThat(resp.getStatus()).isEqualTo(200);
        }

        // 3rd request rejected
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("Should assign system tier with higher capacity when valid system API key is used")
    void testSystemApiKeyHigherCapacity() throws ServletException, IOException {
        properties.setApiKey("test-system-secret-key");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/query");
        req.addHeader("X-API-Key", "test-system-secret-key");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getHeader("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    @DisplayName("Should allow all traffic without throttling when rate limiting is disabled")
    void testDisabledRateLimiting() throws ServletException, IOException {
        properties.getRateLimit().setEnabled(false);

        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/memories");
            req.setRemoteAddr("10.0.0.99");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, new MockFilterChain());
            assertThat(resp.getStatus()).isEqualTo(200);
        }
    }
}
