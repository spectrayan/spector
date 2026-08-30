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
 * Thrown when federated recall is attempted on an account that does not have federation enabled.
 */
public class FederationDisabledException extends NamespaceException {

    private final String accountId;

    /**
     * Creates a new federation disabled exception.
     *
     * @param accountId the identifier of the account
     */
    public FederationDisabledException(String accountId) {
        super(ErrorCode.FEDERATION_DISABLED, "FederationDisabled", accountId);
        this.accountId = accountId;
    }

    /**
     * Gets the account identifier.
     *
     * @return the account ID
     */
    public String getAccountId() {
        return accountId;
    }
}
