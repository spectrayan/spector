/*
 * Copyright 2025–2026 Spectrayan. Licensed under the Apache License, Version 2.0.
 */
package com.spectrayan.spector.synapse.agent.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FlowSpecMarketplace.
 */
class FlowSpecMarketplaceTest {

    private FlowSpecMarketplace marketplace;

    @BeforeEach
    void setUp() {
        marketplace = new FlowSpecMarketplace();
        marketplace.publish(new FlowSpecMarketplace.MarketplaceEntry(
                "research", "Research Agent", "Deep web research", "research",
                "Spector Team", "1.0.0", List.of("ai", "research"), 100, 4.5, true));
        marketplace.publish(new FlowSpecMarketplace.MarketplaceEntry(
                "codegen", "Code Generator", "Generate code from specs", "development",
                "Spector Team", "1.0.0", List.of("code", "ai"), 50, 4.2, true));
        marketplace.publish(new FlowSpecMarketplace.MarketplaceEntry(
                "finance", "Financial Analyst", "Stock analysis", "finance",
                "Community", "0.9.0", List.of("stocks", "finance"), 30, 3.8, false));
    }

    @Test
    void searchByKeyword() {
        var results = marketplace.search("research");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo("research");
    }

    @Test
    void searchByTag() {
        var results = marketplace.search("ai");
        assertThat(results).hasSize(2);
    }

    @Test
    void filterByDomain() {
        var results = marketplace.byDomain("finance");
        assertThat(results).hasSize(1);
    }

    @Test
    void popularTemplates() {
        var results = marketplace.popular(2);
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().id()).isEqualTo("research"); // highest install count
    }

    @Test
    void installIncrementsCount() {
        var before = marketplace.get("codegen").orElseThrow().installCount();
        marketplace.install("codegen");
        var after = marketplace.get("codegen").orElseThrow().installCount();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void totalSize() {
        assertThat(marketplace.size()).isEqualTo(3);
    }
}
