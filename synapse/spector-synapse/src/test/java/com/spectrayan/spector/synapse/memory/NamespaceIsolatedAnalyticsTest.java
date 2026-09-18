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
package com.spectrayan.spector.synapse.memory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.spectrayan.spector.config.ObservabilityConfig;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.pathway.reflect.ReflectSweepSpec;
import com.spectrayan.spector.metrics.ObservedSpectorMemory;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NamespaceIsolatedAnalyticsTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> meterProvider(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

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
        public MemoryRegistry memoryRegistry() {
            return mock(MemoryRegistry.class);
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

    @Test
    @DisplayName("Two-namespace consolidation stats are isolated and do not cross-contaminate")
    void testConsolidationStatsPerNamespaceIsolation() {
        MemoryAccessObject mao = mock(MemoryAccessObject.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        TsidGenerator tsid = mock(TsidGenerator.class);
        MemoryRegistry memoryRegistry = mock(MemoryRegistry.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        SpectorMemory memA = mock(SpectorMemory.class);
        SpectorMemory memB = mock(SpectorMemory.class);
        when(memA.namespaceId()).thenReturn("ns-tenant-a");
        when(memB.namespaceId()).thenReturn("ns-tenant-b");

        MemoryService service = new MemoryService(mao, eventPublisher, tsid, null, null, memoryRegistry, null, null, null, meterProvider(meterRegistry));

        // Simulate reflect on namespace A
        ReflectReport reportA = new ReflectReport(12, 3, 0, 1, Duration.ofMillis(100), null, 0, 0, 0f, 5);
        when(mao.reflect(eq(memA), any(ReflectSweepSpec.class))).thenReturn(reportA);

        when(memoryRegistry.resolveForCurrentRequest()).thenReturn(memA);
        when(mao.isAvailable(memA)).thenReturn(true);
        var adminA = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(memA.admin()).thenReturn(adminA);
        when(adminA.listAll()).thenReturn(List.of());

        service.reflect();
        var statsA = service.getStats();
        assertThat(statsA.consolidationStats().memoriesMerged()).isEqualTo(12);
        assertThat(statsA.consolidationStats().duplicatesRemoved()).isEqualTo(3);

        // Switch to namespace B
        when(memoryRegistry.resolveForCurrentRequest()).thenReturn(memB);
        when(mao.isAvailable(memB)).thenReturn(true);
        var adminB = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(memB.admin()).thenReturn(adminB);
        when(adminB.listAll()).thenReturn(List.of());

        var statsB = service.getStats();
        // Namespace B must have empty consolidation stats
        assertThat(statsB.consolidationStats().memoriesMerged()).isEqualTo(0);
        assertThat(statsB.consolidationStats().lastRunTimestamp()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Similarity scores write and read on the exact same MeterRegistry")
    void testSimilarityWriteReadSameRegistry() {
        MemoryAccessObject mao = mock(MemoryAccessObject.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        TsidGenerator tsid = mock(TsidGenerator.class);
        MemoryRegistry memoryRegistry = mock(MemoryRegistry.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        SpectorMemory rawMem = mock(SpectorMemory.class);
        when(rawMem.namespaceId()).thenReturn("ns-sim-readwrite");

        var admin = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(rawMem.admin()).thenReturn(admin);
        when(admin.listAll()).thenReturn(List.of());

        ObservedSpectorMemory observed = new ObservedSpectorMemory(
                rawMem,
                io.micrometer.observation.ObservationRegistry.create(),
                ObservabilityConfig.DEFAULT,
                meterRegistry
        );

        MemoryService service = new MemoryService(mao, eventPublisher, tsid, null, null, memoryRegistry, null, null, null, meterProvider(meterRegistry));

        when(memoryRegistry.resolveForCurrentRequest()).thenReturn(observed);
        when(mao.isAvailable(observed)).thenReturn(true);

        // Write through ObservedSpectorMemory
        observed.recordSimilarityScore(0.95);
        observed.recordSimilarityScore(0.85);

        // Read through MemoryService.getScoringStats()
        var scoringStats = service.getScoringStats();
        assertThat(scoringStats.avgSimilarity()).isEqualTo(0.90);
    }

    @Test
    @DisplayName("Growth query isolates by namespace_id in SQL")
    void testGrowthQueryNamespaceIsolation() {
        MemoryAccessObject mao = mock(MemoryAccessObject.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        TsidGenerator tsid = mock(TsidGenerator.class);
        MemoryRegistry memoryRegistry = mock(MemoryRegistry.class);
        JdbcClient jdbc = mock(JdbcClient.class);

        SpectorMemory mem = mock(SpectorMemory.class);
        when(mem.namespaceId()).thenReturn("tenant-alpha");
        var admin = mock(com.spectrayan.spector.memory.SpectorMemoryAdmin.class);
        when(mem.admin()).thenReturn(admin);
        when(admin.listAll()).thenReturn(List.of());

        when(memoryRegistry.resolveForCurrentRequest()).thenReturn(mem);
        when(mao.isAvailable(mem)).thenReturn(true);

        var statementSpec = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql(any(String.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("namespace_id = :ns");
            return statementSpec;
        });
        when(statementSpec.param(eq("since"), any())).thenReturn(statementSpec);
        when(statementSpec.param(eq("ns"), eq("tenant-alpha"))).thenReturn(statementSpec);
        var mappedQuerySpec = mock(JdbcClient.MappedQuerySpec.class);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.list()).thenReturn(List.of(Map.entry("2026-09-18", 42L)));

        MemoryService service = new MemoryService(mao, eventPublisher, tsid, jdbc, null, memoryRegistry, null, null, null, null);
        var stats = service.getStats();

        assertThat(stats.growthOverTime()).containsEntry("2026-09-18", 42L);
        verify(statementSpec).param("ns", "tenant-alpha");
    }
}
