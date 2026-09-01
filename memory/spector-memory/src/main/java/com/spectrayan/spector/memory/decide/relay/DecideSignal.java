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

import com.spectrayan.spector.memory.aisme.policy.CognitivePolicy;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.model.SoulContext;

import java.time.Instant;
import java.util.List;

public final class DecideSignal {
    private final PolicyInferenceEngine policyInferenceEngine;
    private final List<CognitivePolicy> candidatePolicies;
    private final List<SoulContext> soulContexts;
    
    private final Instant startTime;
    private volatile PolicyDecisionReport report;

    private DecideSignal(Builder builder) {
        this.policyInferenceEngine = builder.policyInferenceEngine;
        this.candidatePolicies = builder.candidatePolicies == null ? List.of() : List.copyOf(builder.candidatePolicies);
        this.soulContexts = builder.soulContexts == null ? List.of() : List.copyOf(builder.soulContexts);
        this.startTime = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public PolicyInferenceEngine policyInferenceEngine() { return policyInferenceEngine; }
    public List<CognitivePolicy> candidatePolicies() { return candidatePolicies; }
    public List<SoulContext> soulContexts() { return soulContexts; }
    
    public Instant startTime() { return startTime; }
    
    public PolicyDecisionReport report() { return report; }
    public void setReport(PolicyDecisionReport report) { this.report = report; }

    public static final class Builder {
        private PolicyInferenceEngine policyInferenceEngine;
        private List<CognitivePolicy> candidatePolicies;
        private List<SoulContext> soulContexts;

        public Builder policyInferenceEngine(PolicyInferenceEngine engine) { this.policyInferenceEngine = engine; return this; }
        public Builder candidatePolicies(List<CognitivePolicy> policies) { this.candidatePolicies = policies; return this; }
        public Builder soulContexts(List<SoulContext> contexts) { this.soulContexts = contexts; return this; }

        public DecideSignal build() {
            return new DecideSignal(this);
        }
    }
}
