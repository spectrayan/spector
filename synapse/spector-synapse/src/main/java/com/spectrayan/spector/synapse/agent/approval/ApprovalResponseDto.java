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

import java.util.Map;

/**
 * Data transfer object for REST API approval decisions submitted by human operators.
 *
 * @param decision          the decision (APPROVE, REJECT, or MODIFY)
 * @param modifiedArguments updated tool arguments when decision is MODIFY (optional)
 * @param reason            optional reason or explanation for the decision
 */
public record ApprovalResponseDto(
        ApprovalDecision decision,
        Map<String, Object> modifiedArguments,
        String reason
) {
    public ApprovalResponseDto {
        if (decision == null) {
            decision = ApprovalDecision.APPROVE;
        }
    }
}
