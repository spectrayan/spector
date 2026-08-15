/*
 * Copyright 2026 Spectrayan
 */
package com.spectrayan.spector.connector.sink;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TenantMemoryRegistry} — resource limits, capacity checks, and pager metrics.
 */
class TenantMemoryRegistryResourceLimitsTest {

    private TenantMemoryRegistry registry;
    private EmbeddingProvider embeddingProvider;

    @BeforeEach
    void setUp() throws Exception {
        embeddingProvider = mock(EmbeddingProvider.class);
        lenient().when(embeddingProvider.dimensions()).thenReturn(384);
        lenient().when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[384], 384, "test"));

        Path tempDir = Files.createTempDirectory("spector-registry-test-");
        registry = new TenantMemoryRegistry(tempDir, embeddingProvider, 384);
    }

    @AfterEach
    void tearDown() {
        if (registry != null) registry.close();
    }

    @Test
    void setTenantConfigOverridesDefault() {
        var custom = new TenantResourceConfig(500, 64, 100, 60_000L, true);
        registry.setTenantConfig("tenant-x", custom);

        var config = registry.getConfigForTenant("tenant-x");
        assertThat(config.maxMemories()).isEqualTo(500);
        assertThat(config.hardEnforce()).isTrue();
    }

    @Test
    void unconfiguredTenantGetsDefault() {
        var config = registry.getConfigForTenant("unconfigured-tenant");
        assertThat(config).isEqualTo(TenantResourceConfig.defaults());
    }

    @Test
    void checkCapacityAllowsDefaultTenant() {
        assertThatCode(() -> registry.checkCapacity("default"))
                .doesNotThrowAnyException();
    }

    @Test
    void checkCapacityAllowsNullTenant() {
        assertThatCode(() -> registry.checkCapacity(null))
                .doesNotThrowAnyException();
    }

    @Test
    void checkCapacityAllowsBlankTenant() {
        assertThatCode(() -> registry.checkCapacity(""))
                .doesNotThrowAnyException();
    }

    @Test
    void setDefaultConfigAffectsAll() {
        var strict = new TenantResourceConfig(10, 1, 5, 1000L, false);
        registry.setDefaultConfig(strict);

        var config = registry.getConfigForTenant("any-tenant");
        assertThat(config.maxMemories()).isEqualTo(10);
    }

    @Test
    void getTenantMetricsReturnsData() {
        var metrics = registry.getTenantMetrics("test-tenant");

        assertThat(metrics).containsKey("tenantId");
        assertThat(metrics).containsKey("active");
        assertThat(metrics).containsKey("config");
        assertThat(metrics.get("tenantId")).isEqualTo("test-tenant");
    }

    @Test
    void evictIdleReturnsEmptyWhenNoTenants() {
        var evicted = registry.evictIdle();
        assertThat(evicted).isNotNull().isEmpty();
    }

    @Test
    void pagerMetricsReturnsCorrectData() {
        var metrics = registry.pagerMetrics();

        assertThat(metrics).containsKey("hotActive");
        assertThat(metrics).containsKey("maxActive");
        assertThat(metrics).containsKey("totalLoads");
        assertThat(metrics).containsKey("pressureEvictions");
        assertThat(metrics.get("hotActive")).isEqualTo(0);
        assertThat(metrics.get("maxActive")).isEqualTo(TenantMemoryRegistry.DEFAULT_MAX_ACTIVE);
    }

    @Test
    void pressureEvictionWhenAtCapacity() {
        // Create a small registry with maxActive=2
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("spector-pressure-test-");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var smallRegistry = new TenantMemoryRegistry(tempDir, embeddingProvider, 384, 2);

        // Load 2 tenants — fills capacity
        smallRegistry.getMemoryForTenant("tenant-1");
        smallRegistry.releaseMemoryForTenant("tenant-1");
        smallRegistry.getMemoryForTenant("tenant-2");
        smallRegistry.releaseMemoryForTenant("tenant-2");
        assertThat(smallRegistry.activeTenantCount()).isEqualTo(2);

        // Load a 3rd — should trigger pressure eviction of LRU (tenant-1)
        smallRegistry.getMemoryForTenant("tenant-3");
        smallRegistry.releaseMemoryForTenant("tenant-3");
        assertThat(smallRegistry.activeTenantCount()).isEqualTo(2);

        var metrics = smallRegistry.pagerMetrics();
        assertThat((long) metrics.get("pressureEvictions")).isEqualTo(1L);
        assertThat((long) metrics.get("totalLoads")).isEqualTo(3L);

        smallRegistry.close();
    }
}
