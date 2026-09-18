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
