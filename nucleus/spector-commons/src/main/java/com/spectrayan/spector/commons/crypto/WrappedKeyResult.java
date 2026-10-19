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
package com.spectrayan.spector.commons.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable result of wrapping a 256-bit Data-Encryption Key (DEK) under a tenant
 * Key-Encryption Key (KEK) via a {@link KeyProvider} (ADR-0088 §5.1, Issue #1070).
 *
 * @param wrappedDek ciphertext of the wrapped DEK (including authentication tag where applicable)
 * @param wrapIv     initialization vector used during local/symmetric wrapping (empty for remote KMS envelopes)
 * @param kekKeyId   canonical KEK identifier (ARN, resource name, Vault key path, or local key ID)
 * @param kekVersion version identifier of the KEK used to wrap the DEK
 */
public record WrappedKeyResult(
        byte[] wrappedDek,
        byte[] wrapIv,
        String kekKeyId,
        String kekVersion) {

    public WrappedKeyResult {
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
        if (wrappedDek.length == 0) {
            throw new IllegalArgumentException("wrappedDek must not be empty");
        }
        Objects.requireNonNull(kekKeyId, "kekKeyId must not be null");
        wrappedDek = wrappedDek.clone();
        wrapIv = wrapIv != null ? wrapIv.clone() : new byte[0];
        kekVersion = kekVersion != null ? kekVersion : "v1";
    }

    @Override
    public byte[] wrappedDek() {
        return wrappedDek.clone();
    }

    @Override
    public byte[] wrapIv() {
        return wrapIv.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WrappedKeyResult that)) return false;
        return Arrays.equals(wrappedDek, that.wrappedDek)
                && Arrays.equals(wrapIv, that.wrapIv)
                && Objects.equals(kekKeyId, that.kekKeyId)
                && Objects.equals(kekVersion, that.kekVersion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(kekKeyId, kekVersion);
        result = 31 * result + Arrays.hashCode(wrappedDek);
        result = 31 * result + Arrays.hashCode(wrapIv);
        return result;
    }
}
