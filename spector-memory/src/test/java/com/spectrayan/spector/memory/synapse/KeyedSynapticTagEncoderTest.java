/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.memory.synapse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link KeyedSynapticTagEncoder} — HMAC-SHA256 blind indexing.
 */
class KeyedSynapticTagEncoderTest {

    private static SecretKey generateTestKey() {
        try {
            KeyGenerator gen = KeyGenerator.getInstance("HmacSHA256");
            gen.init(256);
            return gen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void singleTagProducesNonZeroBits() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());
        long filter = encoder.encodeTag("java");

        assertThat(filter).isNotZero();
        assertThat(Long.bitCount(filter))
                .as("k=3 bits must be set")
                .isBetween(1, 3); // May overlap, so 1-3
    }

    @Test
    void encodingIsDeterministicWithSameKey() {
        SecretKey key = generateTestKey();
        var encoder = new KeyedSynapticTagEncoder(key);

        long a = encoder.encodeTag("project:alpha");
        long b = encoder.encodeTag("project:alpha");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentKeysProduceDifferentFilters() {
        var encoder1 = new KeyedSynapticTagEncoder(generateTestKey());
        var encoder2 = new KeyedSynapticTagEncoder(generateTestKey());

        long filter1 = encoder1.encodeTag("project:alpha");
        long filter2 = encoder2.encodeTag("project:alpha");

        // Different keys should (almost certainly) produce different filters
        // There's a tiny chance they could collide, but with 64-bit space it's negligible
        assertThat(filter1).isNotEqualTo(filter2);
    }

    @Test
    void keyedEncoderDiffersFromPublicEncoder() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());

        long keyed = encoder.encodeTag("java");
        long publicHash = SynapticTagEncoder.encodeTag("java");

        // HMAC-based output should differ from public MurmurHash output
        assertThat(keyed).isNotEqualTo(publicHash);
    }

    @Test
    void multipleTagsEncodeViaOR() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());

        long single1 = encoder.encodeTag("java");
        long single2 = encoder.encodeTag("performance");
        long combined = encoder.encode("java", "performance");

        assertThat(combined).isEqualTo(single1 | single2);
    }

    @Test
    void matchingWorksWithKeyedFilters() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());

        long record = encoder.encode("java", "performance", "coding");
        long query = encoder.encode("java", "coding");

        assertThat(KeyedSynapticTagEncoder.matches(record, query)).isTrue();
    }

    @Test
    void emptyQueryMatchesEverything() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());

        long record = encoder.encode("java", "performance");
        long emptyQuery = 0L;

        assertThat(KeyedSynapticTagEncoder.matches(record, emptyQuery)).isTrue();
    }

    @RepeatedTest(5)
    void threadSafety() throws InterruptedException {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());
        long expected = encoder.encodeTag("thread-safe-tag");

        Thread[] threads = new Thread[10];
        long[] results = new long[10];
        for (int i = 0; i < threads.length; i++) {
            int idx = i;
            threads[i] = new Thread(() -> results[idx] = encoder.encodeTag("thread-safe-tag"));
            threads[i].start();
        }
        for (Thread t : threads) t.join();

        for (long result : results) {
            assertThat(result).isEqualTo(expected);
        }
    }

    @Test
    void allRandomTagsProduceValidBitCount() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());
        java.util.Random rng = new java.util.Random(42);

        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 3 + rng.nextInt(20);
            for (int j = 0; j < len; j++) {
                sb.append((char) ('a' + rng.nextInt(26)));
            }
            long filter = encoder.encodeTag(sb.toString());
            assertThat(Long.bitCount(filter))
                    .as("Bit count for '%s'", sb)
                    .isBetween(1, 3);
        }
    }

    @Test
    void falsePositiveRateIsAcceptable() {
        var encoder = new KeyedSynapticTagEncoder(generateTestKey());

        // Encode 10 tags into a single record filter
        long filter = 0L;
        for (int i = 0; i < 10; i++) {
            filter |= encoder.encodeTag("tag-" + i);
        }

        // Test 1000 random non-existent tags for false positives
        int falsePositives = 0;
        for (int i = 100; i < 1100; i++) {
            long testMask = encoder.encodeTag("nonexistent-" + i);
            if (KeyedSynapticTagEncoder.matches(filter, testMask)) {
                falsePositives++;
            }
        }

        double fpr = falsePositives / 1000.0;
        assertThat(fpr).as("False positive rate with 10 keyed tags").isLessThan(0.10);
    }
}
