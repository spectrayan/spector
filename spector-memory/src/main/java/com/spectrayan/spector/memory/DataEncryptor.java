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
package com.spectrayan.spector.memory;

/**
 * Service Provider Interface for data encryption at rest.
 *
 * <h3>Design</h3>
 * <p>The Spector core engine is <b>cryptographically blind</b> — it never generates,
 * manages, or stores encryption keys. This SPI allows an external control plane
 * (e.g., Spector Enterprise) to plug in encryption without modifying the core
 * engine's zero-copy mmap architecture or SIMD scoring loops.</p>
 *
 * <h3>Encryption Tiers</h3>
 * <ul>
 *   <li>{@link #encryptText} / {@link #decryptText} — for {@code text.dat} entries
 *       (cold path, post-search retrieval only)</li>
 *   <li>{@link #encryptPayload} / {@link #decryptPayload} — for WAL event payloads
 *       (ingestion path, async)</li>
 *   <li>{@link #encodeTag} — keyed Bloom filter tag encoding
 *       (replaces public MurmurHash with HMAC-based blind indexing)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Implementations MUST be thread-safe. Multiple virtual threads call
 * {@code encryptText} and {@code encryptPayload} concurrently during ingestion.</p>
 *
 * <h3>Performance Contract</h3>
 * <ul>
 *   <li>{@code encryptText/decryptText}: Called only for Top-K results (typically 5–20).
 *       Latency budget: ≤100µs per call.</li>
 *   <li>{@code encryptPayload/decryptPayload}: Called once per ingested memory.
 *       Latency budget: ≤10µs per call.</li>
 *   <li>{@code encodeTag}: Called K times per tag during ingestion.
 *       Latency budget: ≤1µs per call (amortized via cached HMAC instances).</li>
 * </ul>
 *
 * @see NoopDataEncryptor
 */
public interface DataEncryptor {

    /**
     * Encrypts raw text content for persistent storage in {@code text.dat}.
     *
     * <p>The returned bytes are opaque to the core engine — they are stored
     * as-is in the binary {@code text.dat} format. The core engine never
     * parses, indexes, or searches encrypted text bytes.</p>
     *
     * @param plaintext UTF-8 encoded text bytes
     * @return encrypted bytes (format is implementation-defined, e.g., IV + ciphertext + tag)
     */
    byte[] encryptText(byte[] plaintext);

    /**
     * Decrypts text content previously encrypted by {@link #encryptText}.
     *
     * @param ciphertext encrypted bytes from {@code text.dat}
     * @return original UTF-8 text bytes
     * @throws RuntimeException if decryption fails (wrong key, tampered data)
     */
    byte[] decryptText(byte[] ciphertext);

    /**
     * Encrypts a WAL event payload (typically quantized vector bytes).
     *
     * @param plaintext raw payload bytes
     * @return encrypted payload bytes
     */
    byte[] encryptPayload(byte[] plaintext);

    /**
     * Decrypts a WAL event payload previously encrypted by {@link #encryptPayload}.
     *
     * @param ciphertext encrypted payload bytes from WAL
     * @return original payload bytes
     * @throws RuntimeException if decryption fails
     */
    byte[] decryptPayload(byte[] ciphertext);

    /**
     * Encodes a tag string into a 64-bit Bloom filter using keyed hashing.
     *
     * <p>When a non-NOOP encryptor is active, this replaces the default
     * {@link com.spectrayan.spector.memory.synapse.SynapticTagEncoder#encodeTag(String)}
     * with a cryptographically keyed variant (e.g., HMAC-SHA256 → Bloom bits).
     * This prevents dictionary attacks on the Bloom filter.</p>
     *
     * @param tag the tag string to encode (e.g., "project:alpha", "patient:john")
     * @return 64-bit Bloom filter with k=3 bits set
     */
    long encodeTag(String tag);

    /**
     * Returns {@code true} if this encryptor actually performs encryption.
     *
     * <p>The {@link NoopDataEncryptor} returns {@code false}, allowing callers
     * to skip encryption overhead when running in OSS / unencrypted mode.</p>
     *
     * @return true if encryption is active
     */
    default boolean isEnabled() {
        return true;
    }

    /** No-op encryptor — passes all data through unchanged. */
    DataEncryptor NOOP = new NoopDataEncryptor();
}
