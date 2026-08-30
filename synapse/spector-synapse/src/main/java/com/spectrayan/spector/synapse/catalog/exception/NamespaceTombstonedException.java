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
