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

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("VectorSpaceProjectionService")
class VectorSpaceProjectionServiceTest {

    private VectorSpaceProjectionService service;
    private SpectorMemory mockMemory;
    private SpectorMemoryAdmin mockAdmin;

    @BeforeEach
    void setUp() {
        service = new VectorSpaceProjectionService();
        mockMemory = mock(SpectorMemory.class);
        mockAdmin = mock(SpectorMemoryAdmin.class);
        when(mockMemory.admin()).thenReturn(mockAdmin);
    }

    @Test
    @DisplayName("project — returns empty result when memory is null or has no records")
    void project_empty() {
        when(mockAdmin.listAll()).thenReturn(List.of());

        var result = service.project(mockMemory);
        assertThat(result.points()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);

        var nullResult = service.project(null);
        assertThat(nullResult.points()).isEmpty();
    }

    @Test
    @DisplayName("project — computes PCA 3D projection on high-dimensional vectors")
    void project_pca() {
        int dim = 128;
        List<CognitiveRecord> records = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            byte[] vec = new byte[dim];
            for (int d = 0; d < dim; d++) {
                vec[d] = (byte) ((i * 13 + d * 7) % 127);
            }
            records.add(new CognitiveRecord(
                    "mem-" + i,
                    "Text content for memory " + i,
                    i % 2 == 0 ? MemoryType.SEMANTIC : MemoryType.EPISODIC,
                    null,
                    new String[]{"test"},
                    System.currentTimeMillis(),
                    0L, 1.0f, 0.8f, 1, 1,
                    (short) 0, (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0,
                    vec, 0, 0L,
                    Map.of(), false
            ));
        }

        when(mockAdmin.listAll()).thenReturn(records);

        var result = service.project(mockMemory);
        assertThat(result.totalCount()).isEqualTo(20);
        assertThat(result.vectorDimension()).isEqualTo(dim);
        assertThat(result.points()).hasSize(20);

        for (var point : result.points()) {
            assertThat(point.id()).isNotNull();
            assertThat(point.tier()).isNotBlank();
            assertThat(point.x()).isBetween(-30.0f, 30.0f);
            assertThat(point.y()).isBetween(-30.0f, 30.0f);
            assertThat(point.z()).isBetween(-30.0f, 30.0f);
        }

        // Verify query projection using active basis
        float[] queryVec = new float[dim];
        for (int d = 0; d < dim; d++) queryVec[d] = 0.5f;

        float[] projectedQuery = service.projectQuery(queryVec);
        assertThat(projectedQuery).isNotNull();
        assertThat(projectedQuery).hasSize(3);
    }

    @Test
    @DisplayName("project — handles heterogeneous vector dimensions without index out of bounds")
    void project_heterogeneousDimensions() {
        List<CognitiveRecord> records = new ArrayList<>();

        // Add 5 records with dim=100
        for (int i = 0; i < 5; i++) {
            byte[] vec100 = new byte[100];
            vec100[0] = (byte) (i * 10);
            records.add(new CognitiveRecord(
                    "mem-100-" + i, "dim 100 text", MemoryType.SEMANTIC, null,
                    new String[]{"test"}, System.currentTimeMillis(), 0L, 1.0f, 0.8f, 1, 1,
                    (short) 0, (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0,
                    vec100, 0, 0L, Map.of(), false
            ));
        }

        // Add 15 records with dim=768 (dominant)
        for (int i = 0; i < 15; i++) {
            byte[] vec768 = new byte[768];
            vec768[0] = (byte) (i * 5);
            records.add(new CognitiveRecord(
                    "mem-768-" + i, "dim 768 text", MemoryType.SEMANTIC, null,
                    new String[]{"test"}, System.currentTimeMillis(), 0L, 1.0f, 0.8f, 1, 1,
                    (short) 0, (byte) 0, (byte) 0, 1.0f, (byte) 0, (byte) 0,
                    vec768, 0, 0L, Map.of(), false
            ));
        }

        when(mockAdmin.listAll()).thenReturn(records);

        var result = service.project(mockMemory);
        assertThat(result.vectorDimension()).isEqualTo(768);
        assertThat(result.points()).hasSize(15);
    }
}

