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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.synapse.actuator.RateLimitActuatorEndpoint;
import com.spectrayan.spector.synapse.ratelimit.CaffeineRateLimitStateStore;
import com.spectrayan.spector.synapse.ratelimit.RateLimitFilter;
import com.spectrayan.spector.synapse.ratelimit.RateLimitKeyResolver;
import com.spectrayan.spector.synapse.ratelimit.RateLimitStateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration wiring RateLimiter state store, key resolver, filter, and actuator endpoints.
 */
@Configuration
public class RateLimitConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimitStateStore.class)
    public RateLimitStateStore rateLimitStateStore(SynapseProperties properties) {
        return new CaffeineRateLimitStateStore(Duration.ofMinutes(15), 50_000);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitKeyResolver.class)
    public RateLimitKeyResolver rateLimitKeyResolver(SynapseProperties properties) {
        return new RateLimitKeyResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitFilter.class)
    public RateLimitFilter rateLimitFilter(SynapseProperties properties,
                                           RateLimitStateStore stateStore,
                                           RateLimitKeyResolver keyResolver) {
        return new RateLimitFilter(properties, stateStore, keyResolver);
    }

    @Bean
    @ConditionalOnProperty(prefix = "management.endpoint.ratelimits", name = "enabled", matchIfMissing = true)
    public RateLimitActuatorEndpoint rateLimitActuatorEndpoint(SynapseProperties properties,
                                                               RateLimitStateStore stateStore) {
        return new RateLimitActuatorEndpoint(properties, stateStore);
    }
}
