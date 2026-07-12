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
package com.spectrayan.spector.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class SpectorJvmMetricsTest {

    @Test
    void testPrivateConstructor() throws Exception {
        Constructor<SpectorJvmMetrics> constructor = SpectorJvmMetrics.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SpectorJvmMetrics instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    void testBind() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SpectorJvmMetrics.bind(registry);
        // Verify that JVM meters are bound
        assertThat(registry.getMeters()).isNotEmpty();
    }
}
