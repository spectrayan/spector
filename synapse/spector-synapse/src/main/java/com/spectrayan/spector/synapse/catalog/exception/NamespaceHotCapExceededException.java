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
 * Thrown when the number of concurrently active hot namespaces exceeds the configured cap.
 */
public class NamespaceHotCapExceededException extends NamespaceException {

    private final String accountId;
    private final int maxHotNamespaces;

    /**
     * Creates a new namespace hot cap exceeded exception.
     *
     * @param accountId        the identifier of the account
     * @param maxHotNamespaces the maximum number of hot namespace instances permitted
     */
    public NamespaceHotCapExceededException(String accountId, int maxHotNamespaces) {
        super(ErrorCode.NAMESPACE_HOT_CAP_EXCEEDED, "NamespaceHotCapExceeded", accountId, maxHotNamespaces);
        this.accountId = accountId;
        this.maxHotNamespaces = maxHotNamespaces;
    }

    /**
     * Gets the account identifier.
     *
     * @return the account ID
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Gets the maximum permitted hot namespaces.
     *
     * @return the hot namespace cap
     */
    public int getMaxHotNamespaces() {
        return maxHotNamespaces;
    }
}
