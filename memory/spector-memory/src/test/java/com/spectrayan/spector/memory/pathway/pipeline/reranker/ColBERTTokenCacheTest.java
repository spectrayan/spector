/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.pipeline.reranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Tests for {@link ColBERTTokenCache}.
 */
class ColBERTTokenCacheTest {

    private ColBERTTokenCache cache;

    @BeforeEach
    void setUp() {
        cache = new ColBERTTokenCache(10, 64);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.close();
        }
    }

    @Test
    @DisplayName("Put and get  --  retrieves identical float values")
    void putAndGet_exactMatch() {
        float[][] embeddings = new float[][]{
                {1.0f, 2.0f, 3.0f},
                {4.0f, 5.0f, 6.0f}
        };

        cache.put("doc-1", embeddings);
        float[][] retrieved = cache.get("doc-1");

        assertThat(retrieved).isNotNull();
        assertThat(retrieved).hasDimensions(2, 3);
        assertThat(retrieved[0][0]).isCloseTo(1.0f, within(1e-6f));
        assertThat(retrieved[1][2]).isCloseTo(6.0f, within(1e-6f));
    }

    @Test
    @DisplayName("Get  --  missing document returns null")
    void get_missingReturnsNull() {
        assertThat(cache.get("non-existent")).isNull();
    }

    @Test
    @DisplayName("Eviction  --  oldest entry evicted when capacity reached")
    void eviction_lruPolicy() {
        for (int i = 0; i < 10; i++) {
            cache.put("doc-" + i, new float[][]{{i, i + 1}});
        }
        assertThat(cache.size()).isEqualTo(10);

        // Put 11th entry  --  should evict oldest
        cache.put("doc-10", new float[][]{{10, 11}});
        assertThat(cache.size()).isEqualTo(10);
    }
}
