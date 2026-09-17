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
package com.spectrayan.spector.cluster.gateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable request representation for gateway forwarding with streaming body support
 * (ADR-0034 §8, ADR-0081 §8, Req R7.2, R7.5, Invariant K7).
 *
 * @param method         HTTP method (GET, POST, etc.)
 * @param uriPath        subpath and query parameters (e.g. {@code /api/v1/memory/remember?namespace=alpha})
 * @param headers        request headers
 * @param bodySupplier   supplier providing a fresh {@link InputStream} for each forwarding attempt
 * @param contentLength  known request body length, or negative if chunked / unknown
 * @param idempotencyKey original client idempotency key reused across retries (Invariant K7)
 */
public record ForwardRequest(
        String method,
        String uriPath,
        Map<String, List<String>> headers,
        Supplier<InputStream> bodySupplier,
        long contentLength,
        String idempotencyKey
) {

    public ForwardRequest {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(uriPath, "uriPath must not be null");
        headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Collections.emptyMap();
        bodySupplier = bodySupplier != null ? bodySupplier : InputStream::nullInputStream;
    }

    /**
     * Convenience constructor creating a request backed by an in-memory byte array.
     */
    public ForwardRequest(
            String method,
            String uriPath,
            Map<String, List<String>> headers,
            byte[] body,
            String idempotencyKey
    ) {
        this(
                method,
                uriPath,
                headers,
                () -> new ByteArrayInputStream(body != null ? body : new byte[0]),
                body != null ? body.length : 0L,
                idempotencyKey
        );
    }

    public static ForwardRequest ofBytes(
            String method,
            String uriPath,
            Map<String, List<String>> headers,
            byte[] body,
            String idempotencyKey
    ) {
        return new ForwardRequest(method, uriPath, headers, body, idempotencyKey);
    }

    public static ForwardRequest ofStream(
            String method,
            String uriPath,
            Map<String, List<String>> headers,
            Supplier<InputStream> bodySupplier,
            long contentLength,
            String idempotencyKey
    ) {
        return new ForwardRequest(method, uriPath, headers, bodySupplier, contentLength, idempotencyKey);
    }

    /**
     * Opens a new stream for reading the body payload.
     */
    public InputStream openBodyStream() {
        return bodySupplier.get();
    }

    /**
     * Convenience method to read all body bytes into memory.
     * Caution: For large streaming payloads, prefer {@link #openBodyStream()}.
     */
    public byte[] body() {
        try (InputStream in = openBodyStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
