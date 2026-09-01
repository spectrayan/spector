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
package com.spectrayan.spector.memory.cortex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CentroidRouterTest {

    @Test
    @DisplayName("Should find nearest centroid by L2 distance")
    void assignCentroidFindsNearest() {
        CentroidRouter router = new CentroidRouter(2, 0);
        float[][] samples = {
            {0.0f, 0.0f},
            {10.0f, 10.0f}
        };
        router.recalibrate(samples, 10);
        
        int c1 = router.assignCentroid(new float[]{0.1f, 0.1f});
        int c2 = router.assignCentroid(new float[]{9.9f, 9.9f});
        
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    @DisplayName("Zero initial centroids returns zero active centroids")
    void zeroCentroidsReturnsZero() {
        CentroidRouter router = new CentroidRouter(2, 0);
        assertThat(router.activeCentroids()).isZero();
    }

    @Test
    @DisplayName("Recalibrate updates centroid positions")
    void recalibrateUpdatesCentroidPositions() {
        CentroidRouter router = new CentroidRouter(2, 1);
        float[] c0Before = router.centroid(0);
        
        float[][] samples = {{100f, 100f}, {100f, 100f}};
        router.recalibrate(samples, 10);
        
        float[] c0After = router.centroid(0);
        assertThat(c0Before).isNotEqualTo(c0After);
    }

    @Test
    @DisplayName("Recalibrate bootstraps from sample when 0 active centroids")
    void recalibrateBootstrapsFromSample() {
        CentroidRouter router = new CentroidRouter(2, 0);
        float[][] samples = {{1f, 1f}, {-1f, -1f}};
        router.recalibrate(samples, 10);
        assertThat(router.activeCentroids()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Recalibration with well-separated clusters converges centroids")
    void highVarianceTriggersSplit() {
        CentroidRouter router = new CentroidRouter(2, 4);
        // Two well-separated clusters with > 10 samples each
        float[][] samples = new float[40][2];
        for (int i = 0; i < 20; i++) {
            samples[i] = new float[]{-100f + i * 0.1f, -100f + i * 0.1f};
        }
        for (int i = 20; i < 40; i++) {
            samples[i] = new float[]{100f + (i - 20) * 0.1f, 100f + (i - 20) * 0.1f};
        }
        router.recalibrate(samples, 20);
        assertThat(router.activeCentroids()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Max centroids caps at 256")
    void maxCentroidsCapsAt256() {
        CentroidRouter router = new CentroidRouter(2, 256);
        float[][] samples = new float[300][2];
        for (int i = 0; i < 300; i++) {
            samples[i] = new float[]{(float)Math.random() * 100, (float)Math.random() * 100};
        }
        router.recalibrate(samples, 10); 
        assertThat(router.activeCentroids()).isLessThanOrEqualTo(256);
    }

    @Test
    @DisplayName("shouldScanPartition returns true for close queries")
    void shouldScanPartitionTrueForClose() {
        CentroidRouter router = new CentroidRouter(2, 0);
        float[][] samples = {{0f, 0f}, {100f, 100f}};
        router.recalibrate(samples, 10);
        
        int cId = router.assignCentroid(new float[]{0f, 0f});
        assertThat(router.shouldScanPartition(cId, new float[]{0.1f, 0.1f}, 5.0f)).isTrue();
    }

    @Test
    @DisplayName("shouldScanPartition returns false for far queries")
    void shouldScanPartitionFalseForFar() {
        CentroidRouter router = new CentroidRouter(2, 0);
        float[][] samples = {{0f, 0f}, {100f, 100f}};
        router.recalibrate(samples, 10);
        
        int cId = router.assignCentroid(new float[]{0f, 0f});
        assertThat(router.shouldScanPartition(cId, new float[]{100f, 100f}, 5.0f)).isFalse();
    }

    @Test
    @DisplayName("shouldScanPartition returns true for unknown centroid ID")
    void shouldScanPartitionTrueForUnknown() {
        CentroidRouter router = new CentroidRouter(2, 2);
        assertThat(router.shouldScanPartition(-1, new float[]{0f, 0f}, 1.0f)).isTrue();
        assertThat(router.shouldScanPartition(999, new float[]{0f, 0f}, 1.0f)).isTrue();
    }

    @Test
    @DisplayName("centroid() returns clone")
    void centroidReturnsClone() {
        CentroidRouter router = new CentroidRouter(2, 1);
        float[] c = router.centroid(0);
        c[0] = 999f;
        assertThat(router.centroid(0)[0]).isNotEqualTo(999f);
    }

    @Test
    @DisplayName("Invalid centroid ID throws")
    void invalidCentroidIdThrows() {
        CentroidRouter router = new CentroidRouter(2, 1);
        assertThatThrownBy(() -> router.centroid(-1))
            .isInstanceOf(RuntimeException.class);
    }
}
