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
 * Thrown when a principal does not have sufficient access permissions for a namespace.
 */
public class NamespaceAccessDeniedException extends NamespaceException {

    private final String namespaceId;
    private final String principalId;

    /**
     * Creates a new namespace access denied exception.
     *
     * @param namespaceId the identifier of the namespace
     * @param principalId the identifier of the principal attempting access
     */
    public NamespaceAccessDeniedException(String namespaceId, String principalId) {
        super(ErrorCode.NAMESPACE_ACCESS_DENIED, "NamespaceAccessDenied", namespaceId, principalId);
        this.namespaceId = namespaceId;
        this.principalId = principalId;
    }

    /**
     * Gets the namespace identifier.
     *
     * @return the namespace ID
     */
    public String getNamespaceId() {
        return namespaceId;
    }

    /**
     * Gets the principal identifier.
     *
     * @return the principal ID
     */
    public String getPrincipalId() {
        return principalId;
    }
}
