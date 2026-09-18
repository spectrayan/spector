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
