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

import com.spectrayan.spector.config.properties.RateLimitProperties.TierPolicy;
import com.spectrayan.spector.synapse.config.RateLimitConfiguration;
import com.spectrayan.spector.synapse.config.SecurityConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.security.ApiKeyAuthenticationFilter;
import com.spectrayan.spector.synapse.security.ApiKeyStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, RateLimitConfiguration.class})
    static class TestConfig {

        @Bean
        public SynapseProperties synapseProperties() {
            SynapseProperties properties = new SynapseProperties();
            properties.auth().setEnabled(false); // test with auth disabled (legacy permitAll)
            properties.getRateLimit().setEnabled(true);
            properties.getRateLimit().getTiers().put("anonymous", new TierPolicy(2, 3));
            return properties;
        }

        @Bean
        public ApiKeyStore apiKeyStore() {
            return Mockito.mock(ApiKeyStore.class);
        }

        @Bean
        public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(SynapseProperties properties, ApiKeyStore apiKeyStore) {
            return new ApiKeyAuthenticationFilter(properties, apiKeyStore);
        }

        @Bean
        public UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager();
        }

        @Bean
        public TestSampleController testSampleController() {
            return new TestSampleController();
        }
    }

    @RestController
    static class TestSampleController {
        @GetMapping("/api/v1/sample")
        public ResponseEntity<String> sample() {
            return ResponseEntity.ok("success");
        }

        @GetMapping("/health")
        public ResponseEntity<String> health() {
            return ResponseEntity.ok("UP");
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("Should allow requests up to burst capacity and return HTTP 429 when exceeded")
    void testEndToEndRateLimitingThroughSecurityChain() throws Exception {
        String testIp = "192.168.10.50";

        // First 3 requests succeed
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/sample").with(req -> {
                        req.setRemoteAddr(testIp);
                        return req;
                    }))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"));
        }

        // 4th request gets 429
        mockMvc.perform(get("/api/v1/sample").with(req -> {
                    req.setRemoteAddr(testIp);
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value("urn:spector:error:rate-limit-exceeded"))
                .andExpect(jsonPath("$.detail", containsString("Rate limit exceeded")));
    }

    @Test
    @DisplayName("Should allow unlimited requests to excluded health endpoint")
    void testExcludedEndpointNeverThrottled() throws Exception {
        String testIp = "192.168.10.60";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/health").with(req -> {
                        req.setRemoteAddr(testIp);
                        return req;
                    }))
                    .andExpect(status().isOk());
        }
    }
}
