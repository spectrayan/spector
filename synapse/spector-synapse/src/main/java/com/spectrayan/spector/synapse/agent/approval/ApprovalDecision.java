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
 * Human operator decision action on a pending approval request.
 */
public enum ApprovalDecision {
    /** Approve tool execution with original arguments. */
    APPROVE,

    /** Reject tool execution. */
    REJECT,

    /** Approve tool execution with modified arguments. */
    MODIFY
}
