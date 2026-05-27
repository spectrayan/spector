package com.spectrayan.spector.memory.synapse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DecayStrategy} — bucket-based decay with reconsolidation.
 */
class DecayStrategyTest {

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
        assertThat(DecayStrategy.decay(bucket)).isEqualTo(0.95f);
    }

    @Test
    void oneDayOldMemoryGetsBucketTwo() {
        long now = System.currentTimeMillis();
        long halfDayAgo = now - 12 * 3_600_000L;
        int bucket = DecayStrategy.ageToBucket(halfDayAgo, now);

        assertThat(bucket).isEqualTo(2);
        assertThat(DecayStrategy.decay(bucket)).isEqualTo(0.85f);
    }

    @Test
    void veryOldMemoryGetsMaxBucket() {
        long now = System.currentTimeMillis();
        long sixMonthsAgo = now - 180L * 86_400_000L;
        int bucket = DecayStrategy.ageToBucket(sixMonthsAgo, now);

        assertThat(bucket).isEqualTo(DecayStrategy.MAX_BUCKET);
        assertThat(DecayStrategy.decay(bucket)).isEqualTo(0.05f);
    }

    @Test
    void futureTimestampTreatedAsFresh() {
        long now = System.currentTimeMillis();
        long future = now + 100_000;
        int bucket = DecayStrategy.ageToBucket(future, now);

        assertThat(bucket).isZero();
    }

    @Test
    void reconsolidationShiftsBucketFresher() {
        // A memory in bucket 5 (1-4 weeks old)
        int rawBucket = 5;

        // No recalls → stays at bucket 5
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 0)).isEqualTo(5);

        // 3 recalls → shifts 1 bucket fresher → bucket 4
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 3)).isEqualTo(4);

        // 6 recalls → shifts 2 buckets fresher → bucket 3
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 6)).isEqualTo(3);

        // 9 recalls → shifts 3 buckets → bucket 2
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 9)).isEqualTo(2);

        // 15 recalls → shifts 5 buckets → clamped to 0
        assertThat(DecayStrategy.adjustForReconsolidation(rawBucket, (short) 15)).isZero();
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

        // Without recalls: bucket 3, decay = 0.70
        float decayNoRecalls = DecayStrategy.computeDecay(twoDaysAgo, now, (short) 0);
        assertThat(decayNoRecalls).isEqualTo(0.70f);

        // With 6 recalls: bucket shifts from 3 to 1, decay = 0.95
        float decayWithRecalls = DecayStrategy.computeDecay(twoDaysAgo, now, (short) 6);
        assertThat(decayWithRecalls).isEqualTo(0.95f);
    }

    @Test
    void decayBucketsAreMonotonicallyDecreasing() {
        for (int i = 1; i < DecayStrategy.DECAY_BUCKETS.length; i++) {
            assertThat(DecayStrategy.DECAY_BUCKETS[i])
                    .as("Bucket %d should be less than bucket %d", i, i - 1)
                    .isLessThan(DecayStrategy.DECAY_BUCKETS[i - 1]);
        }
    }
}
