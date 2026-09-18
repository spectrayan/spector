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
package com.spectrayan.spector.synapse.catalog.exception;

import com.spectrayan.spector.commons.error.ErrorCode;

/**
 * Thrown when an operation is blocked due to a legal hold placed on the namespace.
 */
public class NamespaceLegalHoldException extends NamespaceException {

    private final String namespaceId;

    /**
     * Creates a new namespace legal hold exception.
     *
     * @param namespaceId the identifier of the namespace under legal hold
     */
    public NamespaceLegalHoldException(String namespaceId) {
        super(ErrorCode.NAMESPACE_LEGAL_HOLD, "NamespaceLegalHold", namespaceId);
        this.namespaceId = namespaceId;
    }

    /**
     * Gets the namespace identifier.
     *
     * @return the namespace ID
     */
    public String getNamespaceId() {
        return namespaceId;
    }
}
