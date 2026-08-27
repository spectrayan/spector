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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.aisme.relay.PolicyInferenceRelay;
import com.spectrayan.spector.memory.decide.relay.DecideGates;
import com.spectrayan.spector.memory.decide.relay.DecideReport;
import com.spectrayan.spector.memory.decide.relay.DecideSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

public final class DecidePathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DecidePathway.class);

    private final CognitivePathway<DecideSignal> pathway;

    private DecidePathway(final Builder builder) {
        var pathwayBuilder = CognitivePathway.<DecideSignal>pathway("decide_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }

        // Stage 1: PolicyInferenceRelay
        pathwayBuilder.gated("policy_inference", 
                DecideGates.EFE_ENABLED.and(DecideGates.HAS_CANDIDATES), 
                new PolicyInferenceRelay(), 
                ErrorPolicy.DEGRADE_GRACEFULLY);

        // Stage 2: ExperimentRelay (waking thought experiments)
        pathwayBuilder.gated("experiment_thought",
                DecideGates.EFE_ENABLED,
                new com.spectrayan.spector.memory.decide.relay.ExperimentRelay(),
                ErrorPolicy.DEGRADE_GRACEFULLY);

        this.pathway = pathwayBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DecideReport decide(final DecideSignal signal) {
        Objects.requireNonNull(signal, "DecideSignal cannot be null");
        long start = System.currentTimeMillis();
        
        try {
            DecideSignal result = pathway.conduct(signal);
            long elapsed = System.currentTimeMillis() - start;
            var report = result.report();
            
            if (report == null) {
                return DecideReport.empty();
            }
            
            return new DecideReport(report, elapsed, report.selectedPolicy() != null);
        } catch (Exception e) {
            log.error("DecidePathway: decision cycle aborted due to error: {}", e.getMessage(), e);
            throw new IllegalStateException("DecidePathway execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // No underlying resources to close at this time
    }

    public static final class Builder {
        private PolicyInferenceEngine policyInferenceEngine;
        private Function<SynapticRelay<DecideSignal>, SynapticRelay<DecideSignal>> interceptor;

        public Builder policyInferenceEngine(PolicyInferenceEngine engine) { this.policyInferenceEngine = engine; return this; }
        public Builder interceptor(Function<SynapticRelay<DecideSignal>, SynapticRelay<DecideSignal>> inc) { this.interceptor = inc; return this; }

        public DecidePathway build() {
            return new DecidePathway(this);
        }
    }
}
