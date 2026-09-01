/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
