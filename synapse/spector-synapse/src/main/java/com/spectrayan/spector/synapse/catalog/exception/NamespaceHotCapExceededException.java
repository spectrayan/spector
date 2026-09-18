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
