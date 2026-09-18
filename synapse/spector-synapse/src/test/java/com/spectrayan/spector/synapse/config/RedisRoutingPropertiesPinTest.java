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

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.routing.RoutingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins routing properties defaults reflectively to prevent javac inlining hiding stale defaults
 * (ADR-0034 §8, §15.6, Req R8.2, Task 1.8).
 */
class RedisRoutingPropertiesPinTest {

    @Test
    @DisplayName("Pins Redis routing defaults reflectively in SpectorPropertyConstants")
    void testReflectiveRedisRoutingDefaults() throws Exception {
        assertReflectiveConstant("DEFAULT_ROUTING_REDIS_ENABLED", false);
        assertReflectiveConstant("DEFAULT_ROUTING_REDIS_URI", "redis://localhost:6379");
        assertReflectiveConstant("DEFAULT_ROUTING_REDIS_TIMEOUT_MS", 100L);
        assertReflectiveConstant("DEFAULT_ROUTING_REDIS_TTL_SECONDS", 30L);
        assertReflectiveConstant("DEFAULT_ROUTING_CAFFEINE_TTL_SECONDS", 5L);
        assertReflectiveConstant("DEFAULT_ROUTING_CAFFEINE_MAX_SIZE", 200_000L);
        assertReflectiveConstant("DEFAULT_ROUTING_GATEWAY_RETRY_MAX", 2);
    }

    @Test
    @DisplayName("RoutingProperties matches defaults upon instantiation")
    void testRoutingPropertiesInstantiationDefaults() {
        RoutingProperties props = new RoutingProperties();

        assertThat(props.getRedis().isEnabled()).isFalse();
        assertThat(props.getRedis().getUri()).isEqualTo("redis://localhost:6379");
        assertThat(props.getRedis().getTimeoutMs()).isEqualTo(100L);
        assertThat(props.getRedis().getTtlSeconds()).isEqualTo(30L);

        assertThat(props.getCaffeine().getTtlSeconds()).isEqualTo(5L);
        assertThat(props.getCaffeine().getMaxSize()).isEqualTo(200_000L);

        assertThat(props.getGateway().getRetryMax()).isEqualTo(2);
    }

    private void assertReflectiveConstant(String fieldName, Object expectedValue) throws Exception {
        Field field = SpectorPropertyConstants.class.getField(fieldName);
        Object rawValue = field.get(null);
        assertThat(rawValue)
                .as("SpectorPropertyConstants." + fieldName + " must reflectively match")
                .isEqualTo(expectedValue);
    }
}
