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
package com.spectrayan.spector.spring.autoconfigure;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.metrics.MeteredSpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration and unit tests for {@link SpectorAutoConfiguration} using {@link ApplicationContextRunner}.
 */
class SpectorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpectorAutoConfiguration.class))
            .withUserConfiguration(TestDependenciesConfiguration.class);

    @Test
    void defaultConfiguration_createsMemoryBean() {
        this.contextRunner
                .withPropertyValues("spector.memory.dimensions=384")
                .run(context -> {
                    assertThat(context).hasSingleBean(SpectorMemory.class);
                    SpectorMemory memory = context.getBean(SpectorMemory.class);
                    assertThat(memory).isNotNull();
                });
    }

    @Test
    void withMeterRegistry_wrapsMemoryWithMeteredDecorator() {
        this.contextRunner
                .withUserConfiguration(TestMeterRegistryConfiguration.class)
                .withPropertyValues("spector.memory.dimensions=384", "spector.metrics.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SpectorMemory.class);
                    SpectorMemory memory = context.getBean(SpectorMemory.class);
                    assertThat(memory).isInstanceOf(MeteredSpectorMemory.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependenciesConfiguration {
        @Bean
        EmbeddingProvider embeddingProvider() {
            EmbeddingProvider mock = Mockito.mock(EmbeddingProvider.class);
            Mockito.when(mock.dimensions()).thenReturn(384);
            Mockito.when(mock.modelName()).thenReturn("mock-embed");
            return mock;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestMeterRegistryConfiguration {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
