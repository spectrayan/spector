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
 * Thrown when a token is locked to specific namespaces and a request attempts to switch to an unauthorized namespace.
 */
public class TokenNamespaceLockedException extends NamespaceException {

    private final String allowSet;
    private final String requestedSlug;

    /**
     * Creates a new token namespace locked exception.
     *
     * @param allowSet      the set or pattern of allowed namespaces
     * @param requestedSlug the requested namespace slug
     */
    public TokenNamespaceLockedException(String allowSet, String requestedSlug) {
        super(ErrorCode.TOKEN_NAMESPACE_LOCKED, "TokenNamespaceLocked", allowSet, requestedSlug);
        this.allowSet = allowSet;
        this.requestedSlug = requestedSlug;
    }

    /**
     * Gets the allowed namespaces pattern or set.
     *
     * @return the allowed namespace set
     */
    public String getAllowSet() {
        return allowSet;
    }

    /**
     * Gets the requested namespace slug.
     *
     * @return the requested slug
     */
    public String getRequestedSlug() {
        return requestedSlug;
    }
}
