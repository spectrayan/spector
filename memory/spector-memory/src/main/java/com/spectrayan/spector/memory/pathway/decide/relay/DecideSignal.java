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
package com.spectrayan.spector.memory.pathway.decide.relay;

import com.spectrayan.spector.memory.aisme.policy.CognitivePolicy;
import com.spectrayan.spector.memory.aisme.policy.PolicyDecisionReport;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.model.SoulContext;

import java.time.Instant;
import java.util.List;

public final class DecideSignal extends com.spectrayan.spector.commons.pathway.AbstractSignal {
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
