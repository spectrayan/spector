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
 * Thrown when an operation exceeds a namespace-level quota.
 */
public class NamespaceQuotaExceededException extends NamespaceException {

    private final String namespaceId;
    private final String detail;

    /**
     * Creates a new namespace quota exceeded exception.
     *
     * @param namespaceId the identifier of the namespace
     * @param detail      details regarding the exceeded quota
     */
    public NamespaceQuotaExceededException(String namespaceId, String detail) {
        super(ErrorCode.NAMESPACE_QUOTA_EXCEEDED, "NamespaceQuotaExceeded", namespaceId, detail);
        this.namespaceId = namespaceId;
        this.detail = detail;
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
     * Gets the quota violation details.
     *
     * @return the violation detail message
     */
    public String getDetail() {
        return detail;
    }
}
