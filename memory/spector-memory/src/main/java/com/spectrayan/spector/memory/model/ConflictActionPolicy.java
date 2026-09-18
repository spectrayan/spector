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
package com.spectrayan.spector.memory.model;

/**
 * Action policy recommended by the cognitive engine when encountering evidence or contradictions.
 */
public enum ConflictActionPolicy {
    /**
     * Single dominant hypothesis with sufficient confidence spread; safe to act as truth.
     */
    ACCEPT_WINNER,

    /**
     * Multiple legitimate temporal or context-partitioned perspectives; present alternatives to caller.
     */
    PRESENT_ALTERNATIVES,

    /**
     * High ambiguity between equally weighted overlapping hypotheses; ask user/agent for clarification.
     */
    ASK_CLARIFYING_QUESTION,

    /**
     * Insufficient epistemic grounding (confidence below minimum threshold); abstain from answering.
     */
    ABSTAIN
}
