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
 * Thrown when an operation is attempted on a tombstoned namespace.
 */
public class NamespaceTombstonedException extends NamespaceException {

    private final String namespaceId;

    /**
     * Creates a new namespace tombstoned exception.
     *
     * @param namespaceId the identifier of the tombstoned namespace
     */
    public NamespaceTombstonedException(String namespaceId) {
        super(ErrorCode.NAMESPACE_TOMBSTONED, "NamespaceTombstoned", namespaceId);
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
