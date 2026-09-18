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
package com.spectrayan.spector.memory.pathway.decide;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.DefaultPathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.policy.PolicyInferenceEngine;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideRecipe;
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
        final PathwayComposer<DecideSignal> pathwayBuilder =
                new DefaultPathwayComposer<>("decide_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }
        new DecideRecipe().compose(pathwayBuilder);

        initEngine(pathwayBuilder.build());
    }

    public PathwayEngine<DecideSignal> pathway() {
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
        ConductionOutcome outcome = signal.context() != null ? signal.context().outcome() : null;
        var report = signal.report();
        if (report == null) {
            return DecideReport.empty(outcome);
        }
        return new DecideReport(report, 0L, report.selectedPolicy() != null, outcome);
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
