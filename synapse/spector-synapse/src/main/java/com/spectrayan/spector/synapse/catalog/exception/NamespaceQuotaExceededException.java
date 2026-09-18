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
