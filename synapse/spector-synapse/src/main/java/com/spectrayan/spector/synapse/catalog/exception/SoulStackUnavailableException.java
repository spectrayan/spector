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
 * Thrown when a soul stack cannot be assembled or is unavailable for an account.
 */
public class SoulStackUnavailableException extends NamespaceException {

    private final String accountId;
    private final String reason;

    /**
     * Creates a new soul stack unavailable exception.
     *
     * @param accountId the identifier of the account
     * @param reason    the reason why the soul stack is unavailable
     */
    public SoulStackUnavailableException(String accountId, String reason) {
        super(ErrorCode.SOUL_STACK_UNAVAILABLE, "SoulStackUnavailable", accountId, reason);
        this.accountId = accountId;
        this.reason = reason;
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
     * Gets the reason for unavailability.
     *
     * @return the failure reason
     */
    public String getReason() {
        return reason;
    }
}
