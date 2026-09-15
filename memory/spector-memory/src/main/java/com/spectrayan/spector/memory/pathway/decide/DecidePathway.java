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
package com.spectrayan.spector.memory.pathway.decide;

import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.aisme.relay.PolicyInferenceRelay;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideGates;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideReport;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;
import com.spectrayan.spector.memory.pathway.decide.relay.ExperimentRelay;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.aisme.relay.PolicyInferenceRelay;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideGates;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideReport;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

public final class DecidePathway extends AbstractPathway<DecideSignal, DecideReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DecidePathway.class);

    private DecidePathway(final Builder builder) {
        super("decide", DecideSignal.class, DecideReport.class);
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
                new com.spectrayan.spector.memory.pathway.decide.relay.ExperimentRelay(),
                ErrorPolicy.DEGRADE_GRACEFULLY);

        initEngine(pathwayBuilder.build());
    }

    public CognitivePathway<DecideSignal> pathway() {
        return engine();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DecideReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final DecideSignal signal) {
        if (kernel != null && signal != null && signal.context() == null) {
            final DefaultPathwayContext.Builder ctxBuilder = DefaultPathwayContext.builder()
                    .namespaceId(kernel.namespaceId())
                    .bind(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
            signal.bind(ctxBuilder.build());
        }
        return decide(signal);
    }

    public DecideReport decide(final DecideSignal signal) {
        Objects.requireNonNull(signal, "DecideSignal cannot be null");
        if (signal.context() == null) {
            signal.bind(DefaultPathwayContext.builder().build());
        }
        return conduct(signal);
    }

    @Override
    protected DecideReport project(final DecideSignal signal) {
        var report = signal.report();
        if (report == null) {
            return DecideReport.empty();
        }
        return new DecideReport(report, 0L, report.selectedPolicy() != null);
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
