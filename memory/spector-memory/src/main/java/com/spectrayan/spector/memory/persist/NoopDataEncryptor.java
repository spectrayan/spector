/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.kernel.score.SynapticTagEncoder;

import com.spectrayan.spector.kernel.score.SynapticTagEncoder;

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
