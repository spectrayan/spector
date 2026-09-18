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
package com.spectrayan.spector.synapse.agent.approval.model;

/**
 * Result of evaluating and executing an agent tool action through the approval gate.
 */
public sealed interface ApprovalExecutionResult
        permits ApprovalExecutionResult.Success, ApprovalExecutionResult.Denied {

    /** Action was successfully executed (either read-only or approved). */
    record Success(String output, AgentActionApproval approval) implements ApprovalExecutionResult {}

    /** Action execution was denied (rejected, cancelled, or timed out). */
    record Denied(String reason, AgentActionApproval approval) implements ApprovalExecutionResult {}
}
