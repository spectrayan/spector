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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of a completed HTTP response stored for idempotent replay.
 *
 * @param statusCode         HTTP status code of the original response
 * @param headers            captured HTTP response headers
 * @param body               captured HTTP response body bytes
 * @param committedAtEpochMs epoch timestamp (ms) when the response was committed
 */
public record StoredIdempotentResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        long committedAtEpochMs
) {

    public StoredIdempotentResponse {
        headers = copyHeaders(headers);
        body = body != null ? body.clone() : new byte[0];
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    /**
     * Returns a copy of the response headers including {@code Idempotent-Replayed: true}.
     */
    public Map<String, List<String>> headersWithReplayMarker() {
        Map<String, List<String>> copy = new LinkedHashMap<>(headers);
        copy.put(IdempotencyValidator.HEADER_IDEMPOTENT_REPLAYED, List.of("true"));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), List.copyOf(new ArrayList<>(entry.getValue())));
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
