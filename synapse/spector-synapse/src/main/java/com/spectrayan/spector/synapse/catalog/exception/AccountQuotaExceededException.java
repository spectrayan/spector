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
 * Thrown when an operation exceeds an account-level quota.
 */
public class AccountQuotaExceededException extends NamespaceException {

    private final String accountId;
    private final String detail;

    /**
     * Creates a new account quota exceeded exception.
     *
     * @param accountId the identifier of the account
     * @param detail    details regarding the exceeded quota
     */
    public AccountQuotaExceededException(String accountId, String detail) {
        super(ErrorCode.ACCOUNT_QUOTA_EXCEEDED, "AccountQuotaExceeded", accountId, detail);
        this.accountId = accountId;
        this.detail = detail;
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
     * Gets the quota violation details.
     *
     * @return the violation detail message
     */
    public String getDetail() {
        return detail;
    }
}
