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
package com.spectrayan.spector.memory.aisme.enactment;

import com.spectrayan.spector.memory.aisme.policy.PolicyType;

import java.util.Objects;

/**
 * Encapsulates a candidate policy archetype or behavioral playbook for Stance resolution (ADR-0032).
 */
public record PolicyPlaybook(
        String name,
        PolicyType policyType,
        float[] observationMean,
        float[] observationPrecision
) {

    public PolicyPlaybook {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(policyType, "policyType must not be null");
    }

    public static PolicyPlaybook of(PolicyType policyType) {
        return new PolicyPlaybook(policyType.name(), policyType, null, null);
    }

    public static PolicyPlaybook of(String name, PolicyType policyType) {
        return new PolicyPlaybook(name, policyType, null, null);
    }

    public static PolicyPlaybook of(PolicyType policyType, float[] observationMean, float[] observationPrecision) {
        return new PolicyPlaybook(policyType.name(), policyType, observationMean, observationPrecision);
    }
}
