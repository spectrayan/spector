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
package com.spectrayan.spector.memory.decide.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.util.function.Predicate;

public final class DecideGates {
    public static final Predicate<DecideSignal> EFE_ENABLED = signal -> signal != null && signal.policyInferenceEngine() != null;
    public static final Predicate<DecideSignal> HAS_CANDIDATES = signal -> signal != null && signal.candidatePolicies() != null && !signal.candidatePolicies().isEmpty();
    
    private DecideGates() {}
}
