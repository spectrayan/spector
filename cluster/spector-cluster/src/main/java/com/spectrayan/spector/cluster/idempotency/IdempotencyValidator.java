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

import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Locale;
import java.util.Set;

/**
 * Validation and request classification utilities for HTTP {@code Idempotency-Key} handling.
 */
public final class IdempotencyValidator {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String HEADER_ALT_IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String HEADER_IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    public static final int MAX_KEY_BYTES = 256;

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Set<String> READ_ONLY_POST_PATHS = Set.of(
            "/api/v1/memory/recall",
            "/api/v1/memory/recall/response",
            "/api/v1/memory/search",
            "/api/v1/memory/browse",
            "/api/v1/memory/why-not",
            "/api/v1/memory/federated-recall"
    );

    private IdempotencyValidator() {}

    /**
     * Extracts and validates the idempotency key from primary ({@code Idempotency-Key})
     * and alternate ({@code X-Idempotency-Key}) request headers.
     *
     * @return the validated key, or {@code null} if neither header was supplied
     * @throws SpectorValidationException if a supplied header is empty, overlong (&gt;256 ASCII bytes),
     *                                    contains non-printable characters, or conflicts between the two headers
     */
    public static String extractAndValidate(String primaryHeader, String altHeader) {
        if (primaryHeader == null && altHeader == null) {
            return null;
        }
        if (primaryHeader != null && altHeader != null) {
            String v1 = validateKey(primaryHeader);
            String v2 = validateKey(altHeader);
            if (!v1.equals(v2)) {
                throw new SpectorValidationException(
                        ErrorCode.IDEMPOTENCY_KEY_INVALID,
                        "Conflicting Idempotency-Key and X-Idempotency-Key header values"
                );
            }
            return v1;
        }
        return validateKey(primaryHeader != null ? primaryHeader : altHeader);
    }

    /**
     * Validates a single idempotency key string when non-null.
     *
     * <p>Keys must consist of 1 to 256 printable ASCII VCHAR characters ({@code 0x21..0x7E}).</p>
     */
    public static String validateKey(String key) {
        if (key == null) {
            return null;
        }
        if (key.isEmpty()) {
            throw new SpectorValidationException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key must not be empty"
            );
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                throw new SpectorValidationException(
                        ErrorCode.IDEMPOTENCY_KEY_INVALID,
                        "Idempotency-Key must contain only printable ASCII characters (0x21..0x7E)"
                );
            }
        }
        if (key.length() > MAX_KEY_BYTES) {
            throw new SpectorValidationException(
                    ErrorCode.IDEMPOTENCY_KEY_TOO_LONG,
                    key.length(),
                    MAX_KEY_BYTES
            );
        }
        return key;
    }

    /**
     * Returns {@code true} if the HTTP method and path represent a state-mutating operation
     * subject to idempotency deduplication.
     */
    public static boolean isMutatingRequest(String method, String path) {
        if (method == null || method.isBlank()) {
            return false;
        }
        String upper = method.trim().toUpperCase(Locale.ROOT);
        if (!MUTATING_METHODS.contains(upper)) {
            return false;
        }
        if ("POST".equals(upper) && path != null) {
            String cleanPath = stripQueryAndTrailingSlash(path);
            if (READ_ONLY_POST_PATHS.contains(cleanPath)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalizes a {@link RoutingKey} into a canonical namespace scope string.
     */
    public static String normalizeNamespaceScope(RoutingKey routingKey) {
        if (routingKey == null) {
            return "default:default";
        }
        return normalizeNamespaceScope(routingKey.tenantId(), routingKey.namespaceId());
    }

    /**
     * Normalizes {@code (tenantId, namespaceId)} into a canonical scope identifier.
     */
    public static String normalizeNamespaceScope(String tenantId, String namespaceId) {
        String normTenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId.trim();
        String normNs = (namespaceId == null || namespaceId.isBlank()) ? "default" : namespaceId.trim();
        return normTenant + ":" + normNs;
    }

    private static String stripQueryAndTrailingSlash(String rawPath) {
        int q = rawPath.indexOf('?');
        String path = q >= 0 ? rawPath.substring(0, q) : rawPath;
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
