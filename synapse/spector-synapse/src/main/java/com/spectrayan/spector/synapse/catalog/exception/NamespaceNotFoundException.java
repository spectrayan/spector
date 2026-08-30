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
 * Thrown when a requested namespace does not exist in the catalog.
 */
public class NamespaceNotFoundException extends NamespaceException {

    private final String namespaceIdOrSlug;

    /**
     * Creates a new namespace not found exception.
     *
     * @param namespaceIdOrSlug the namespace ID or slug that could not be found
     */
    public NamespaceNotFoundException(String namespaceIdOrSlug) {
        super(ErrorCode.NAMESPACE_NOT_FOUND, "NamespaceNotFound", namespaceIdOrSlug);
        this.namespaceIdOrSlug = namespaceIdOrSlug;
    }

    /**
     * Gets the namespace ID or slug.
     *
     * @return the namespace ID or slug
     */
    public String getNamespaceIdOrSlug() {
        return namespaceIdOrSlug;
    }
}
