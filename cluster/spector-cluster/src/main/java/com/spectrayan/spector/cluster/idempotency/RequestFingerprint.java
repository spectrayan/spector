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
package com.spectrayan.spector.cluster.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable fingerprint of a mutating HTTP request used for idempotency verification.
 *
 * @param method        normalized uppercase HTTP method (e.g. {@code POST}, {@code PUT}, {@code DELETE})
 * @param path          normalized request URI path (including query string when present)
 * @param payloadSha256 lowercase hexadecimal SHA-256 digest of the raw request body bytes
 */
public record RequestFingerprint(
        String method,
        String path,
        String payloadSha256
) {

    private static final byte[] EMPTY_BYTES = new byte[0];

    public RequestFingerprint {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(payloadSha256, "payloadSha256 must not be null");
    }

    /**
     * Computes a {@link RequestFingerprint} for the given HTTP method, request path, and payload bytes.
     */
    public static RequestFingerprint of(String method, String path, byte[] payload) {
        String normMethod = (method == null || method.isBlank())
                ? "POST"
                : method.trim().toUpperCase(Locale.ROOT);
        String normPath = (path == null || path.isBlank())
                ? "/"
                : path.trim();
        String sha256 = sha256Hex(payload != null ? payload : EMPTY_BYTES);
        return new RequestFingerprint(normMethod, normPath, sha256);
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes != null ? bytes : EMPTY_BYTES);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
