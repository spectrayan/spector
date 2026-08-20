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
package com.spectrayan.spector.synapse.agent.approval.dto;

import com.spectrayan.spector.synapse.agent.approval.model.ApprovalDecision;

import java.util.Map;

/**
 * Data transfer object representing a human decision submitted via REST API.
 *
 * @param decision          the decision type (APPROVE, REJECT, MODIFY, CANCEL)
 * @param modifiedArguments updated tool arguments when decision is MODIFY (optional)
 * @param reason            optional reason or explanation for the decision
 */
public record ApprovalDecisionRequest(
        ApprovalDecision decision,
        Map<String, Object> modifiedArguments,
        String reason
) {
    public ApprovalDecisionRequest {
        if (decision == null) {
            decision = ApprovalDecision.APPROVE;
        }
    }
}
