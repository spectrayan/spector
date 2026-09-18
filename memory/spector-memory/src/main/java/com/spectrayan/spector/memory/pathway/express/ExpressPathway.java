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
package com.spectrayan.spector.memory.pathway.express;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.DefaultPathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.memory.model.BlendshapeVector;
import com.spectrayan.spector.memory.model.IdiolectProfile;
import com.spectrayan.spector.memory.model.PhenomenologicalContextPack;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressRecipe;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressReport;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

public final class ExpressPathway extends AbstractPathway<ExpressSignal, ExpressReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExpressPathway.class);
    
    private final java.util.function.Consumer<ExpressReport> somaticFeedbackConsumer;

    private ExpressPathway(Builder builder) {
        super("express", ExpressSignal.class, ExpressReport.class);
        this.somaticFeedbackConsumer = builder.somaticFeedbackConsumer;
        final PathwayComposer<ExpressSignal> composer =
                new DefaultPathwayComposer<>("ExpressPathway");
        if (builder.interceptor != null) {
            composer.withInterceptor(builder.interceptor);
        }
        new ExpressRecipe().compose(composer);
        initEngine(composer.build());
    }

    public PathwayEngine<ExpressSignal> pathway() {
        return engine();
    }

    public ExpressReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final ExpressSignal signal) {
        if (kernel != null && signal != null) {
            if (signal.context() == null) {
                final DefaultPathwayContext.Builder ctxBuilder = DefaultPathwayContext.builder()
                        .namespaceId(kernel.namespaceId())
                        .bind(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
                signal.bind(ctxBuilder.build());
            }
        }
        return express(signal);
    }

    public ExpressReport express(final ExpressSignal signal) {
        Objects.requireNonNull(signal, "ExpressSignal cannot be null");
        if (signal.context() == null) {
            signal.bind(DefaultPathwayContext.builder().build());
        }
        return conduct(signal);
    }

    @Override
    protected ExpressReport project(final ExpressSignal signal) {
        ConductionOutcome outcome = signal.context() != null ? signal.context().outcome() : null;
        if (outcome != null && outcome.finish() == ConductionOutcome.Finish.SHORT_CIRCUITED) {
            return ExpressReport.empty(outcome);
        }
        ProsodyParameterVector prosodyVector = (ProsodyParameterVector) signal.attributes().get("prosodyVector");
        IdiolectProfile idiolectProfile = (IdiolectProfile) signal.attributes().get("idiolectProfile");
        String promptDirectives = (String) signal.attributes().get("promptDirectives");
        BlendshapeVector blendshapeVector = (BlendshapeVector) signal.attributes().get("blendshapeVector");
        PhenomenologicalContextPack contextPack = (PhenomenologicalContextPack) signal.attributes().get("contextPack");
        
        String internalMonologue = contextPack != null ? contextPack.internalMonologue() : "";
        if (promptDirectives == null && contextPack != null) {
            promptDirectives = contextPack.systemPromptDirectives();
        }

        String ssmlTags = ""; 
        int relaysExecuted = 4; // updated count
        
        ExpressReport report = new ExpressReport(
            prosodyVector, blendshapeVector, idiolectProfile, contextPack, promptDirectives, internalMonologue, ssmlTags, Duration.ZERO, relaysExecuted, outcome
        );

        if (somaticFeedbackConsumer != null) {
            try {
                somaticFeedbackConsumer.accept(report);
            } catch (Exception e) {
                log.trace("Somatic feedback hook execution degraded: {}", e.getMessage());
            }
        }

        return report;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public void close() {
    }
    
    public static class Builder {
        private java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ExpressSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ExpressSignal>> interceptor;
        private java.util.function.Consumer<ExpressReport> somaticFeedbackConsumer;
        
        public Builder interceptor(java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ExpressSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ExpressSignal>> interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public Builder onSomaticFeedback(java.util.function.Consumer<ExpressReport> consumer) {
            this.somaticFeedbackConsumer = consumer;
            return this;
        }
        
        public ExpressPathway build() {
            return new ExpressPathway(this);
        }
    }
}
