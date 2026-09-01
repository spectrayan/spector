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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.memory.*;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.memory.express.relay.ExpressGates;
import com.spectrayan.spector.memory.express.relay.ExpressReport;
import com.spectrayan.spector.memory.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.express.relay.IdiolectStylometryRelay;
import com.spectrayan.spector.memory.express.relay.VocalProsodyRelay;
import com.spectrayan.spector.memory.express.relay.EmbodiedKinesicsRelay;
import com.spectrayan.spector.memory.express.relay.PhenomenologicalStreamRelay;
import com.spectrayan.spector.memory.model.BlendshapeVector;
import com.spectrayan.spector.memory.model.PhenomenologicalContextPack;
import com.spectrayan.spector.memory.model.ProsodyParameterVector;
import com.spectrayan.spector.memory.model.IdiolectProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public final class ExpressPathway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExpressPathway.class);
    
    private final CognitivePathway<ExpressSignal> pathway;
    
    private final java.util.function.Consumer<ExpressReport> somaticFeedbackConsumer;

    private ExpressPathway(Builder builder) {
        this.somaticFeedbackConsumer = builder.somaticFeedbackConsumer;
        this.pathway = CognitivePathway.<ExpressSignal>pathway("ExpressPathway")
                .withInterceptor(builder.interceptor)
                .gated("IdiolectStylometry", ExpressGates.IDIOLECT_ENABLED, new IdiolectStylometryRelay(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated("VocalProsody", ExpressGates.PROSODY_ENABLED, new VocalProsodyRelay(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated("EmbodiedKinesics", ExpressGates.KINESICS_ENABLED, new EmbodiedKinesicsRelay(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated("PhenomenologicalStream", ExpressGates.PHENOMENOLOGICAL_ENABLED, new PhenomenologicalStreamRelay(), ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();
    }
    
    public ExpressReport express(ExpressSignal signal) {
        long start = System.currentTimeMillis();
        pathway.conduct(signal);
        
        long elapsedMillis = System.currentTimeMillis() - start;
        Duration elapsed = Duration.ofMillis(elapsedMillis);
        
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
            prosodyVector, blendshapeVector, idiolectProfile, contextPack, promptDirectives, internalMonologue, ssmlTags, elapsed, relaysExecuted
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
    public void close() throws Exception {
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
