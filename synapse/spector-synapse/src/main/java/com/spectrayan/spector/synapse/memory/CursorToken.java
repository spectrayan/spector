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
package com.spectrayan.spector.synapse.memory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque URL-safe Base64 cursor token encoding {@code (timestampMs, id)}
 * conforming to Interface Contract 4 and Requirement R5.
 */
public record CursorToken(long timestampMs, String id) {

    /**
     * Encodes a timestamp and engram ID into an opaque, URL-safe Base64 cursor token without padding.
     *
     * @param timestampMs the creation timestamp in epoch milliseconds
     * @param id          the engram identifier
     * @return opaque URL-safe base64 string
     */
    public static String encode(long timestampMs, String id) {
        String raw = timestampMs + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque URL-safe Base64 cursor token into its constituent {@code (timestampMs, id)}.
     *
     * @param base64Cursor the base64 cursor token
     * @return decoded {@link CursorToken}, or {@code null} if input is null or blank
     * @throws IllegalArgumentException if the cursor token is malformed or invalid base64
     */
    public static CursorToken decode(String base64Cursor) {
        if (base64Cursor == null || base64Cursor.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64Cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int colonIdx = raw.indexOf(':');
            if (colonIdx <= 0) {
                throw new IllegalArgumentException("Malformed cursor token: missing timestamp separator");
            }
            long ts = Long.parseLong(raw.substring(0, colonIdx));
            String id = raw.substring(colonIdx + 1);
            return new CursorToken(ts, id);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid base64 cursor token: " + base64Cursor, e);
        }
    }
}
