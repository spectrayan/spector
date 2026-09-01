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

/**
 * Tests for {@link DecayStrategy} — 12-bucket power-law decay with reconsolidation.
 */
class DecayStrategyTest {

    // ══════════════════════════════════════════════════════════════
    // Basic bucket assignment (0–6: unchanged time ranges)
    // ══════════════════════════════════════════════════════════════

    @Test
    void freshMemoryGetsBucketZero() {
        long now = System.currentTimeMillis();
        int bucket = DecayStrategy.ageToBucket(now - 1000, now); // 1 second old

        assertThat(bucket).isZero();
        assertThat(DecayStrategy.decay(bucket)).isEqualTo(1.0f);
    }

    @Test
    void twoHourOldMemoryGetsBucketOne() {
        long now = System.currentTimeMillis();
        long twoHoursAgo = now - 2 * 3_600_000L;
        int bucket = DecayStrategy.ageToBucket(twoHoursAgo, now);

        assertThat(bucket).isEqualTo(1);
    }

    @Test
    void twelveHourOldMemoryGetsBucketTwo() {
        long now = System.currentTimeMillis();
        long halfDayAgo = now - 12 * 3_600_000L;
        int bucket = DecayStrategy.ageToBucket(halfDayAgo, now);

        assertThat(bucket).isEqualTo(2);
    }

    @Test
    void futureTimestampTreatedAsFresh() {
        long now = System.currentTimeMillis();
        long future = now + 100_000;
        int bucket = DecayStrategy.ageToBucket(future, now);

        assertThat(bucket).isZero();
    }

    // ══════════════════════════════════════════════════════════════
    // New extended buckets (7–11)
    // ══════════════════════════════════════════════════════════════

    @Test
    void fourMonthOldMemoryGetsBucketSeven() {
        long now = System.currentTimeMillis();
        long fourMonthsAgo = now - 120L * 86_400_000L; // ~120 days
        int bucket = DecayStrategy.ageToBucket(fourMonthsAgo, now);

        assertThat(bucket).isEqualTo(7); // 3–6 months
    }

    @Test
    void nineMonthOldMemoryGetsBucketEight() {
        long now = System.currentTimeMillis();
        long nineMonthsAgo = now - 270L * 86_400_000L; // ~270 days
        int bucket = DecayStrategy.ageToBucket(nineMonthsAgo, now);

        assertThat(bucket).isEqualTo(8); // 6–12 months
    }

    @Test
    void eighteenMonthOldMemoryGetsBucketNine() {
        long now = System.currentTimeMillis();
        long eighteenMonthsAgo = now - 540L * 86_400_000L; // ~18 months
        int bucket = DecayStrategy.ageToBucket(eighteenMonthsAgo, now);

        assertThat(bucket).isEqualTo(9); // 1–2 years
    }

    @Test
    void threeYearOldMemoryGetsBucketTen() {
        long now = System.currentTimeMillis();
        long threeYearsAgo = now - 1095L * 86_400_000L; // ~3 years
        int bucket = DecayStrategy.ageToBucket(threeYearsAgo, now);

        assertThat(bucket).isEqualTo(10); // 2–5 years
    }

    @Test
    void tenYearOldMemoryGetsMaxBucket() {
        long now = System.currentTimeMillis();
        long tenYearsAgo = now - 3650L * 86_400_000L; // ~10 years
        int bucket = DecayStrategy.ageToBucket(tenYearsAgo, now);

        assertThat(bucket).isEqualTo(DecayStrategy.MAX_BUCKET); // 5+ years = bucket 11
    }

    @Test
    void maxBucketIsEleven() {
        assertThat(DecayStrategy.MAX_BUCKET).isEqualTo(11);
    }

    @Test
    void twelveBucketsExist() {
        assertThat(DecayStrategy.DECAY_BUCKETS).hasSize(12);
    }

    // ══════════════════════════════════════════════════════════════
    // Permastore floor
    // ══════════════════════════════════════════════════════════════

    @Test
    void oldestBucketHasPermastoreFloor() {
        float oldest = DecayStrategy.DECAY_BUCKETS[DecayStrategy.MAX_BUCKET];

        // Permastore floor: nothing decays below 0.10 (configurable via DecayConfig)
        assertThat(oldest).isGreaterThanOrEqualTo(0.10f);
    }

    @Test
    void sixMonthOldMemoryRetainsSignificantSignal() {
        long now = System.currentTimeMillis();
        long sixMonthsAgo = now - 180L * 86_400_000L;
        int bucket = DecayStrategy.ageToBucket(sixMonthsAgo, now);
        float decay = DecayStrategy.decay(bucket);

        // Power-law: 6-month memory should retain much more than old 0.05
        assertThat(decay).isGreaterThan(0.20f);
    }

    // ══════════════════════════════════════════════════════════════
    // Power-law properties
    // ══════════════════════════════════════════════════════════════

    @Test
    void decayBucketsAreMonotonicallyDecreasing() {
        for (int i = 1; i < DecayStrategy.DECAY_BUCKETS.length; i++) {
            assertThat(DecayStrategy.DECAY_BUCKETS[i])
                    .as("Bucket %d should be less than bucket %d", i, i - 1)
                    .isLessThan(DecayStrategy.DECAY_BUCKETS[i - 1]);
        }
    }

    @Test
    void freshBucketIsExactlyOne() {
        assertThat(DecayStrategy.DECAY_BUCKETS[0]).isEqualTo(1.0f);
    }

    @Test
    void allBucketsArePositive() {
        for (float bucket : DecayStrategy.DECAY_BUCKETS) {
            assertThat(bucket).isGreaterThan(0.0f);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Reconsolidation with 12 buckets
    // ══════════════════════════════════════════════════════════════

    @Test
    void reconsolidationShiftsBucketFresher() {
        int rawBucket = 10; // 2–5 years old

        // No recalls → stays at bucket 10
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 0)).isEqualTo(10);

        // 1 recall → bucket >> 1 = 5 (halves perceived age)
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 1)).isEqualTo(5);

        // 2 recalls → bucket >> 2 = 2 (quarter perceived age)
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 2)).isEqualTo(2);

        // 3 recalls → bucket >> 3 = 1
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 3)).isEqualTo(1);

        // 5+ recalls → capped at shift 5, bucket >> 5 = 0
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 10)).isZero();
    }

    @Test
    void reconsolidationNeverGoesBelowZero() {
        int rawBucket = 1;
        short manyRecalls = 100;

        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, manyRecalls)).isZero();
    }

    @Test
    void computeDecayIntegratesAllSteps() {
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - 2 * 86_400_000L;

        // Without recalls: bucket 3
        float decayNoRecalls = DecayStrategy.computeDecay(twoDaysAgo, now, (short) 0);
        assertThat(decayNoRecalls).isEqualTo(DecayStrategy.DECAY_BUCKETS[3]);

        // With 1 recall: bucket shifts from 3 >> 1 = 1
        float decayWithRecalls = DecayStrategy.computeDecay(twoDaysAgo, now, (short) 1);
        assertThat(decayWithRecalls).isEqualTo(DecayStrategy.DECAY_BUCKETS[1]);
    }

    @Test
    void reconsolidationWorksWithMaxBucket() {
        // A 10-year-old memory in bucket 11
        int rawBucket = 11;

        // 1 recall → bucket >> 1 = 5
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 1)).isEqualTo(5);

        // 2 recalls → bucket >> 2 = 2
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 2)).isEqualTo(2);
    }
}
