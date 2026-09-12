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
package com.spectrayan.spector.cluster.routing;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.util.Locale;

/**
 * Validates identifier material entering routing keys, matching the Phase 0.1 storage validation contract (Req R2.4).
 *
 * <p>Enforces that an unsafe identifier fails closed identically on the routing path and the storage path.</p>
 */
public final class ClusterValidation {

    public static final int MAX_IDENTIFIER_LENGTH = 256;

    private ClusterValidation() {
        // Utility class
    }

    /**
     * Validates a namespace identifier.
     *
     * @param namespaceId the namespace identifier to validate
     * @throws SpectorValidationException if the identifier is invalid
     */
    public static void validateNamespaceId(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "must not be null, empty, or whitespace-only");
        }
        if (namespaceId.length() > MAX_IDENTIFIER_LENGTH) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "length " + namespaceId.length()
                            + " exceeds maximum of " + MAX_IDENTIFIER_LENGTH + " characters");
        }
        for (int i = 0; i < namespaceId.length(); i++) {
            char c = namespaceId.charAt(i);
            if (c == '/' || c == '\\' || c == '.' || c == '{' || c == '}' || c == ':' || c <= '\u001F') {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "namespace identifier", "illegal character at index " + i
                                + " (code point U+" + String.format("%04X", (int) c) + ")");
            }
        }
    }

    /**
     * Validates a tenant identifier that is about to become a routing key component.
     *
     * <p>Applies every {@link #validateNamespaceId(String)} rule and additionally requires lowercase.</p>
     *
     * @param tenantId the tenant identifier to validate
     * @throws SpectorValidationException if the identifier is invalid
     */
    public static void validateTenantId(String tenantId) {
        validateNamespaceId(tenantId);
        if (!tenantId.equals(tenantId.toLowerCase(Locale.ROOT))) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "namespace identifier", "tenant identifier must be lowercase");
        }
    }

    /**
     * Validates a cell identifier that is about to become a routing key component (G46).
     *
     * @param cellId the cell identifier to validate
     * @throws SpectorValidationException if the identifier is invalid
     */
    public static void validateCellId(String cellId) {
        if (cellId == null || cellId.isBlank()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "cell identifier", "must not be null, empty, or whitespace-only");
        }
        if (cellId.length() > MAX_IDENTIFIER_LENGTH) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "cell identifier", "length " + cellId.length()
                            + " exceeds maximum of " + MAX_IDENTIFIER_LENGTH + " characters");
        }
        for (int i = 0; i < cellId.length(); i++) {
            char c = cellId.charAt(i);
            if (c == '/' || c == '\\' || c == '.' || c == '{' || c == '}' || c == ':' || c <= '\u001F') {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "cell identifier", "illegal character at index " + i
                                + " (code point U+" + String.format("%04X", (int) c) + ")");
            }
        }
    }
}
