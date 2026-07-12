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

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC-SHA256 keyed Bloom filter encoder for synaptic tags.
 *
 * <h3>Security Upgrade over {@link SynapticTagEncoder}</h3>
 * <p>The standard {@code SynapticTagEncoder} uses non-keyed MurmurHash3/FNV hashing
 * with public constants. An attacker who obtains the {@code .mem} files can
 * precompute hashes for a dictionary of common tags and match them against
 * the 64-bit Bloom filters stored in synaptic headers.</p>
 *
 * <p>This keyed encoder replaces the hash input with HMAC-SHA256 output,
 * making dictionary attacks infeasible without the tenant-specific secret key.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Compute {@code hmac = HMAC-SHA256(tenantKey, tagBytes)}</li>
 *   <li>Extract 64-bit hash from the first 8 bytes of the HMAC</li>
 *   <li>Derive k=3 bit positions using double-hashing (same as {@link SynapticTagEncoder})</li>
 *   <li>Return 64-bit Bloom filter with those bits set</li>
 * </ol>
 *
 * <h3>Compatibility</h3>
 * <p>The output is still a 64-bit {@code long} with k=3 bits set — the same format
 * as {@link SynapticTagEncoder}. SIMD scan loops, tag matching, and merge operations
 * are completely unaffected. Only the encoding function changes.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe via {@link ThreadLocal} HMAC instances. Each thread gets its own
 * pre-initialized {@link Mac} to avoid contention.</p>
 *
 * @see SynapticTagEncoder
 * @see com.spectrayan.spector.memory.DataEncryptor#encodeTag(String)
 */
public final class KeyedSynapticTagEncoder {

    /** Number of hash functions (bits set per tag) — matches {@link SynapticTagEncoder}. */
    private static final int K = 3;

    /** Number of bits in the filter — matches {@link SynapticTagEncoder}. */
    private static final int M = 64;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ThreadLocal<Mac> macPool;

    /**
     * Creates a keyed tag encoder with the given tenant secret key.
     *
     * @param tenantKey the HMAC secret key (must be suitable for HmacSHA256)
     * @throws IllegalArgumentException if the key is invalid for HmacSHA256
     */
    public KeyedSynapticTagEncoder(SecretKey tenantKey) {
        // Validate the key eagerly — fail fast on construction, not first use
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(tenantKey);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("HmacSHA256 not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid key for HmacSHA256", e);
        }

        this.macPool = ThreadLocal.withInitial(() -> {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(tenantKey);
                return mac;
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new AssertionError("HmacSHA256 initialization failed", e);
            }
        });
    }

    /**
     * Encodes one or more tag strings into a 64-bit Bloom filter using HMAC-SHA256.
     *
     * @param tags tag strings to encode
     * @return 64-bit Bloom filter with k=3 bits set per tag
     */
    public long encode(String... tags) {
        long filter = 0L;
        for (String tag : tags) {
            filter |= encodeTag(tag);
        }
        return filter;
    }

    /**
     * Encodes a single tag string into a 64-bit Bloom filter using HMAC-SHA256.
     *
     * @param tag tag string to encode
     * @return 64-bit value with k=3 bits set
     */
    public long encodeTag(String tag) {
        Mac mac = macPool.get();
        byte[] hmac = mac.doFinal(tag.getBytes(StandardCharsets.UTF_8));

        // Extract 64-bit primary hash from first 8 bytes of HMAC output
        long h1 = ByteBuffer.wrap(hmac, 0, 8).getLong();

        // Extract 64-bit secondary hash from bytes 8-15
        long h2 = ByteBuffer.wrap(hmac, 8, 8).getLong();
        h2 |= 1L; // Ensure step size is odd for power-of-two M

        long filter = 0L;
        for (int i = 0; i < K; i++) {
            int bitIndex = (int) ((h1 + (long) i * h2) & (M - 1));
            filter |= (1L << bitIndex);
        }
        return filter;
    }

    /**
     * Checks if a record's synaptic tags match the query mask.
     * Delegates to {@link SynapticTagEncoder#matches} — matching logic is unchanged.
     *
     * @param recordTags the record's 64-bit Bloom filter
     * @param queryMask  the query's required tag bits (encoded with the same key)
     * @return true if the record passes the tag filter
     */
    public static boolean matches(long recordTags, long queryMask) {
        return SynapticTagEncoder.matches(recordTags, queryMask);
    }
}
