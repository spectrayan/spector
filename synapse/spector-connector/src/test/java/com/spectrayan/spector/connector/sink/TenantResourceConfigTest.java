/*
 * Copyright 2026 Spectrayan
 */
package com.spectrayan.spector.connector.sink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TenantResourceConfig} — configuration records.
 */
class TenantResourceConfigTest {

    @Test
    void defaultsHasReasonableValues() {
        var config = TenantResourceConfig.defaults();

        assertThat(config.maxMemories()).isEqualTo(100_000);
        assertThat(config.maxSegmentMb()).isEqualTo(512);
        assertThat(config.maxIngestionsPerMin()).isEqualTo(1000);
        assertThat(config.idleEvictionMs()).isEqualTo(30 * 60 * 1000L);
        assertThat(config.hardEnforce()).isFalse();
    }

    @Test
    void unlimitedHasMaxValues() {
        var config = TenantResourceConfig.unlimited();

        assertThat(config.maxMemories()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.maxSegmentMb()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.maxIngestionsPerMin()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.idleEvictionMs()).isEqualTo(Long.MAX_VALUE);
        assertThat(config.hardEnforce()).isFalse();
    }

    @Test
    void customConfigPreservesValues() {
        var config = new TenantResourceConfig(500, 64, 100, 60_000L, true);

        assertThat(config.maxMemories()).isEqualTo(500);
        assertThat(config.maxSegmentMb()).isEqualTo(64);
        assertThat(config.maxIngestionsPerMin()).isEqualTo(100);
        assertThat(config.idleEvictionMs()).isEqualTo(60_000L);
        assertThat(config.hardEnforce()).isTrue();
    }
}
