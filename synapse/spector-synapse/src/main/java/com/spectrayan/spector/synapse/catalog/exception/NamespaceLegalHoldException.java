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
