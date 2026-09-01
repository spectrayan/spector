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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Controls how the cognitive recall pathway handles contradicting bitemporal facts and claims.
 */
public enum ConflictMode {
    /**
     * Preserves all competing evidence versions and attaches epistemic entropy and action policies.
     */
    MULTI_EVIDENCE,

    /**
     * Resolves deterministically to the single highest-confidence fact.
     */
    HIGHEST_CONFIDENCE,

    /**
     * Drops any candidate with active contradictions (fail-closed security).
     */
    FAIL_CLOSED
}
