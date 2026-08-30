/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
