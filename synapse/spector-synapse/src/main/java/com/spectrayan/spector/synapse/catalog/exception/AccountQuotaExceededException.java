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
