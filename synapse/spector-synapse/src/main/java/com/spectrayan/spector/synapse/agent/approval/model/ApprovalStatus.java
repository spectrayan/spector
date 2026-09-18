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
 * Lifecycle status of an agent action approval request.
 */
public enum ApprovalStatus {
    /** Awaiting decision by a human operator. */
    PENDING,

    /** Approved by human operator with original arguments. */
    APPROVED,

    /** Rejected by human operator. */
    REJECTED,

    /** Approved by human operator with modified arguments. */
    MODIFIED,

    /** Explicitly cancelled by human operator or system. */
    CANCELLED,

    /** Timed out waiting for human operator response. */
    TIMED_OUT
}
