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

import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

/**
 * No-op {@link DataEncryptor} that passes all data through unchanged.
 *
 * <p>Used in OSS mode and when no enterprise encryption is configured.
 * All methods are zero-cost pass-throughs. Tag encoding delegates to
 * the standard {@link SynapticTagEncoder} (non-keyed MurmurHash).</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Stateless and inherently thread-safe.</p>
 */
public final class NoopDataEncryptor implements DataEncryptor {

    NoopDataEncryptor() {}

    @Override
    public byte[] encryptText(byte[] plaintext) {
        return plaintext;
    }

    @Override
    public byte[] decryptText(byte[] ciphertext) {
        return ciphertext;
    }

    @Override
    public byte[] encryptPayload(byte[] plaintext) {
        return plaintext;
    }

    @Override
    public byte[] decryptPayload(byte[] ciphertext) {
        return ciphertext;
    }

    @Override
    public long encodeTag(String tag) {
        return SynapticTagEncoder.encodeTag(tag);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
