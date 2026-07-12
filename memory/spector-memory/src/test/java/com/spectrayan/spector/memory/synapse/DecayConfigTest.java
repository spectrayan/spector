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
package com.spectrayan.spector.memory.synapse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DecayConfig} — power-law decay configuration and bucket generation.
 */
class DecayConfigTest {

    @Test
    void defaultConfigHasTwelveBuckets() {
        assertThat(DecayConfig.DEFAULT.buckets()).hasSize(12);
    }

    @Test
    void defaultExponentIsPointFifteen() {
        assertThat(DecayConfig.DEFAULT.exponent()).isEqualTo(0.15f);
    }

    @Test
    void defaultFloorIsPointTen() {
        assertThat(DecayConfig.DEFAULT.floor()).isEqualTo(0.10f);
    }

    @Test
    void computeBucketsProducesMonotonicallyDecreasingValues() {
        float[] buckets = DecayConfig.computeBuckets(0.3f, 0.10f);

        assertThat(buckets).hasSize(12);
        for (int i = 1; i < buckets.length; i++) {
            assertThat(buckets[i])
                    .as("Bucket %d (%.4f) should be <= bucket %d (%.4f)", i, buckets[i], i - 1, buckets[i - 1])
                    .isLessThanOrEqualTo(buckets[i - 1]);
        }
    }

    @Test
    void computeBucketsFirstBucketIsOne() {
        float[] buckets = DecayConfig.computeBuckets(0.15f, 0.10f);
        assertThat(buckets[0]).isEqualTo(1.0f);
    }

    @Test
    void computeBucketsRespectsFloor() {
        float floor = 0.15f;
        float[] buckets = DecayConfig.computeBuckets(0.5f, floor);

        for (float bucket : buckets) {
            assertThat(bucket)
                    .as("No bucket should be below floor %.2f", floor)
                    .isGreaterThanOrEqualTo(floor);
        }
    }

    @Test
    void slowForgetDecaysMoreSlowly() {
        float[] slow = DecayConfig.SLOW_FORGET.buckets();
        float[] fast = DecayConfig.FAST_FORGET.buckets();

        // At every bucket (except 0), slow forgetting should retain more signal
        for (int i = 1; i < slow.length; i++) {
            assertThat(slow[i])
                    .as("Slow bucket %d should be >= fast bucket %d", i, i)
                    .isGreaterThanOrEqualTo(fast[i]);
        }
    }

    @Test
    void differentExponentsProduceDifferentCurves() {
        float[] d008 = DecayConfig.computeBuckets(0.08f, 0.05f);
        float[] d015 = DecayConfig.computeBuckets(0.15f, 0.05f);
        float[] d030 = DecayConfig.computeBuckets(0.30f, 0.05f);

        // At bucket 6 (1-3 months), the differences should be significant
        assertThat(d008[6]).isGreaterThan(d015[6]);
        assertThat(d015[6]).isGreaterThan(d030[6]);
    }

    @Test
    void customConfigIsAllowed() {
        DecayConfig custom = new DecayConfig(0.2f, 0.12f, null);

        assertThat(custom.exponent()).isEqualTo(0.2f);
        assertThat(custom.floor()).isEqualTo(0.12f);
        assertThat(custom.buckets()).hasSize(12);
        assertThat(custom.buckets()[0]).isEqualTo(1.0f);
    }

    @Test
    void exponentBelowRangeThrows() {
        assertThatThrownBy(() -> new DecayConfig(0.04f, 0.10f, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("exponent");
    }

    @Test
    void exponentAboveRangeThrows() {
        assertThatThrownBy(() -> new DecayConfig(1.5f, 0.10f, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("exponent");
    }

    @Test
    void floorBelowRangeThrows() {
        assertThatThrownBy(() -> new DecayConfig(0.3f, -0.1f, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("floor");
    }

    @Test
    void floorAboveRangeThrows() {
        assertThatThrownBy(() -> new DecayConfig(0.3f, 0.6f, null))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("floor");
    }

    @Test
    void wrongBucketCountThrows() {
        float[] badBuckets = new float[]{1.0f, 0.5f, 0.25f}; // only 3
        assertThatThrownBy(() -> new DecayConfig(0.3f, 0.10f, badBuckets))
                .isInstanceOf(com.spectrayan.spector.commons.error.SpectorValidationException.class)
                .hasMessageContaining("12");
    }

    @Test
    void customBucketsAreUsedWhenProvided() {
        float[] custom = new float[]{1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f,
                0.4f, 0.35f, 0.3f, 0.25f, 0.2f, 0.15f};
        DecayConfig config = new DecayConfig(0.3f, 0.10f, custom);

        assertThat(config.buckets()).isEqualTo(custom);
    }
}
