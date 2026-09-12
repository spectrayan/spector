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
package com.spectrayan.spector.cluster.routing.invalidation;

import java.util.Objects;

/**
 * Message published across the cell pub/sub channel to invalidate cached routing tuples
 * (ADR-0034 §8.3, Req R4.1).
 *
 * @param nsKey  the canonical namespace key material (e.g. "tenant-1/ns-alpha")
 * @param epoch  the epoch at which the route changed
 * @param reason human-readable reason (e.g. "manual_override", "stale_route", "rebalance")
 */
public record RoutingInvalidationMessage(String nsKey, long epoch, String reason) {

    public RoutingInvalidationMessage {
        Objects.requireNonNull(nsKey, "nsKey must not be null");
        if (reason == null) {
            reason = "unspecified";
        }
    }

    /**
     * Serializes this message to a compact JSON string.
     *
     * @return JSON string
     */
    public String toJson() {
        return String.format("{\"nsKey\":\"%s\",\"epoch\":%d,\"reason\":\"%s\"}",
                escapeJson(nsKey), epoch, escapeJson(reason));
    }

    /**
     * Parses a message from a JSON string, or returns null if malformed (Req R4.4).
     *
     * @param json the raw payload
     * @return parsed message or null
     */
    public static RoutingInvalidationMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String nsKey = extractField(json, "nsKey");
            if (nsKey == null || nsKey.isBlank()) {
                return null;
            }
            long epoch = 1L;
            String epochStr = extractField(json, "epoch");
            if (epochStr != null) {
                try {
                    epoch = Long.parseLong(epochStr.trim());
                } catch (NumberFormatException ignored) {}
            }
            String reason = extractField(json, "reason");
            return new RoutingInvalidationMessage(nsKey, epoch, reason != null ? reason : "unspecified");
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end != -1 ? json.substring(start + 1, end) : null;
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && !Character.isWhitespace(json.charAt(end))) {
                end++;
            }
            return json.substring(start, end);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
