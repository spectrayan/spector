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
package com.spectrayan.spector.memory.pathway.reflect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReflectFilter: Criteria Matching Tests")
class ReflectFilterTest {

    @Test
    @DisplayName("ReflectFilter.all() matches any session, timestamp, and partition")
    void testAllFilter() {
        ReflectFilter filter = ReflectFilter.all();
        assertThat(filter.matches(100L, 5000L, 0)).isTrue();
        assertThat(filter.matches(200L, 10000L, 1)).isTrue();
    }

    @Test
    @DisplayName("Filter matches explicit session IDs")
    void testSessionIdFilter() {
        ReflectFilter filter = ReflectFilter.builder()
                .sessionIds(Set.of(101L, 102L))
                .build();

        assertThat(filter.matches(101L, 1000L, 0)).isTrue();
        assertThat(filter.matches(102L, 1000L, 0)).isTrue();
        assertThat(filter.matches(103L, 1000L, 0)).isFalse();
    }

    @Test
    @DisplayName("Filter respects exclusive cursor watermark (sessionIdAfter)")
    void testSessionIdAfterWatermark() {
        ReflectFilter filter = ReflectFilter.builder()
                .sessionIdAfter(500L)
                .build();

        assertThat(filter.matches(499L, 1000L, 0)).isFalse();
        assertThat(filter.matches(500L, 1000L, 0)).isFalse();
        assertThat(filter.matches(501L, 1000L, 0)).isTrue();
    }

    @Test
    @DisplayName("Filter matches timestamp range [from, to]")
    void testTimestampRange() {
        Instant t1 = Instant.ofEpochMilli(2000L);
        Instant t2 = Instant.ofEpochMilli(4000L);

        ReflectFilter filter = ReflectFilter.builder()
                .from(t1)
                .to(t2)
                .build();

        assertThat(filter.matches(1L, 1999L, 0)).isFalse();
        assertThat(filter.matches(1L, 2000L, 0)).isTrue();
        assertThat(filter.matches(1L, 3000L, 0)).isTrue();
        assertThat(filter.matches(1L, 4000L, 0)).isTrue();
        assertThat(filter.matches(1L, 4001L, 0)).isFalse();
    }

    @Test
    @DisplayName("Filter matches partition sequence set")
    void testPartitionSeqFilter() {
        ReflectFilter filter = ReflectFilter.builder()
                .partitionSeqs(Set.of(0, 2))
                .build();

        assertThat(filter.matches(1L, 1000L, 0)).isTrue();
        assertThat(filter.matches(1L, 1000L, 1)).isFalse();
        assertThat(filter.matches(1L, 1000L, 2)).isTrue();
    }
}
