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
package com.spectrayan.spector.synapse.agent.approval;

/**
 * Status lifecycle of a human-in-the-loop approval request.
 */
public enum ApprovalStatus {
    /** Awaiting decision by a human operator. */
    PENDING,

    /** Approved with original arguments. */
    APPROVED,

    /** Rejected by a human operator. */
    REJECTED,

    /** Approved with modified arguments. */
    MODIFIED,

    /** Timed out waiting for a human response. */
    TIMEOUT
}
