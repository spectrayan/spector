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
 * Thrown when an attempt is made to delete or tombstone the protected default namespace.
 */
public class DefaultNamespaceProtectedException extends NamespaceException {

    private final String namespaceId;

    /**
     * Creates a new default namespace protected exception.
     *
     * @param namespaceId the identifier of the default namespace
     */
    public DefaultNamespaceProtectedException(String namespaceId) {
        super(ErrorCode.DEFAULT_NAMESPACE_PROTECTED, "DefaultNamespaceProtected", namespaceId);
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
