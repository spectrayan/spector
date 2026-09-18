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
package com.spectrayan.spector.synapse.memory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NamespaceIsolatedAnalyticsTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MemoryAnalyticsScheduler.class, TestConfig.class);

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MemoryAccessObject memoryAccessObject() {
            return mock(MemoryAccessObject.class);
        }

        @Bean
        public JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }

        @Bean
        public NamespaceResolver namespaceResolver() {
            return mock(NamespaceResolver.class);
        }

        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    @DisplayName("Scheduler conditional on property - default enabled")
    void schedulerConditionalOnProperty_defaultEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MemoryAnalyticsScheduler.class);
        });
    }

    @Test
    @DisplayName("Scheduler conditional on property - disabled when false")
    void schedulerConditionalOnProperty_disabledWhenFalse() {
        contextRunner
            .withPropertyValues("spector.memory.analytics.history.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(MemoryAnalyticsScheduler.class);
            });
    }
}
