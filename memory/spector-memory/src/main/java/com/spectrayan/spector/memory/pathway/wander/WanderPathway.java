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
package com.spectrayan.spector.memory.pathway.wander;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.kernel.store.ContinuityMemory;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderRecipe;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderReport;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderSignal;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.pathway.DefaultPathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.GatedRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * 4th Canonical Cognitive Pathway in Spector Memory orchestrating Default Mode Network (DMN)
 * spontaneous mind-wandering and longitudinal consciousness continuity (\(\Phi_{CC}\)) tracking.
 *
 * <h3>Biological Analog: Default Mode Network Wakeful Rest Activation</h3>
 * <p>Executes continuous Hopfield attractor discovery across active memory stores during idle states
 * and captures multi-epoch identity metrics into {@link ContinuityMemory}.</p>
 *
 * @since 1.2.0
 */
public final class WanderPathway extends AbstractPathway<WanderSignal, WanderReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WanderPathway.class);

    private final ScalarQuantizer quantizer;
    private final EmbeddingProvider embeddingProvider;
    private final MentalStateTracker mentalStateTracker;
    private final CognitiveManifold cognitiveManifold;
    private final ContinuousHopfieldNetwork hopfieldNetwork;
    private final HebbianGraphBase hebbianGraph;
    private final HomeostaticCore homeostaticCore;
    private final ContinuityMemory continuityMemory;
    private final AismeProperties aismeConfig;
    private final float[] soulPriorPreference;

    private WanderPathway(final Builder builder) {
        super("wander", WanderSignal.class, WanderReport.class);
        this.quantizer = builder.quantizer;
        this.embeddingProvider = builder.embeddingProvider;
        this.mentalStateTracker = builder.mentalStateTracker;
        this.cognitiveManifold = builder.cognitiveManifold;
        this.hopfieldNetwork = builder.hopfieldNetwork;
        this.hebbianGraph = builder.hebbianGraph;
        this.homeostaticCore = builder.homeostaticCore;
        this.continuityMemory = builder.continuityMemory;
        this.aismeConfig = builder.aismeConfig;
        this.soulPriorPreference = builder.soulPriorPreference;

        final PathwayComposer<WanderSignal> pathwayBuilder =
                new DefaultPathwayComposer<>("wander_pathway");
        if (builder.interceptor != null) {
            pathwayBuilder.withInterceptor(builder.interceptor);
        }
        new WanderRecipe().compose(pathwayBuilder);

        final PathwayEngine<WanderSignal> engine = pathwayBuilder.build();
        initEngine(engine);
    }

    public PathwayEngine<WanderSignal> pathway() {
        return engine();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected WanderReport project(final WanderSignal signal) {
        ConductionOutcome outcome = signal.context() != null ? signal.context().outcome() : null;
        if (outcome != null && outcome.finish() == ConductionOutcome.Finish.SHORT_CIRCUITED) {
            return WanderReport.empty(outcome);
        }
        final WanderReport report = signal.buildReport();
        if (log.isDebugEnabled()) {
            log.debug("WanderPathway: cycle complete in {}ms — sampled={}, associations={}, snapshotRecorded={}",
                    report.elapsed().toMillis(), report.memoriesSampled(), report.associationsFormed(), report.snapshotRecorded());
        }
        return report;
    }

    /**
     * Executes the wandering pathway for an explicit namespace kernel and populated signal.
     *
     * @param kernel the namespace kernel
     * @param signal the wander execution signal
     * @return resulting {@link WanderReport}
     */
    public WanderReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final WanderSignal signal) {
        Objects.requireNonNull(signal, "WanderSignal cannot be null");
        if (signal.context() == null) {
            final DefaultPathwayContext.Builder ctxBuilder = DefaultPathwayContext.builder();
            if (kernel != null) {
                ctxBuilder.namespaceId(kernel.namespaceId());
                ctxBuilder.bind(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
            }
            signal.bind(ctxBuilder.build());
        }
        return conduct(signal);
    }

    /**
     * Executes a mind-wandering cycle for an explicit namespace kernel.
     */
    public WanderReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                final PartitionManager partitionManager,
                                final long lastActivityTimestampMs) {
        WanderSignal signal = WanderSignal.builder()
                .partitionManager(partitionManager)
                .quantizer(quantizer)
                .embeddingProvider(embeddingProvider)
                .mentalStateTracker(mentalStateTracker)
                .cognitiveManifold(cognitiveManifold)
                .hopfieldNetwork(hopfieldNetwork)
                .hebbianGraph(hebbianGraph)
                .homeostaticCore(homeostaticCore)
                .continuityMemory(continuityMemory)
                .aismeConfig(aismeConfig)
                .soulPriorPreference(soulPriorPreference)
                .lastActivityTimestampMs(lastActivityTimestampMs)
                .idleThresholdSeconds(aismeConfig != null ? aismeConfig.dmnIdleIntervalSeconds() : 60)
                .build();

        return execute(kernel, signal);
    }

    /**
     * Convenience method to execute a mind-wandering cycle.
     */
    public WanderReport wander(final PartitionManager partitionManager, final long lastActivityTimestampMs) {
        return execute(null, partitionManager, lastActivityTimestampMs);
    }

    public ContinuityMemory continuityMemory() {
        return continuityMemory;
    }

    @Override
    public void close() {
        if (continuityMemory != null) {
            continuityMemory.close();
        }
    }

    /**
     * Builder for {@link WanderPathway}.
     */
    public static final class Builder {
        private ScalarQuantizer quantizer;
        private EmbeddingProvider embeddingProvider;
        private MentalStateTracker mentalStateTracker;
        private CognitiveManifold cognitiveManifold;
        private ContinuousHopfieldNetwork hopfieldNetwork;
        private HebbianGraphBase hebbianGraph;
        private HomeostaticCore homeostaticCore;
        private ContinuityMemory continuityMemory;
        private AismeProperties aismeConfig = AismeProperties.defaultConfig();
        private float[] soulPriorPreference;
        private Function<SynapticRelay<WanderSignal>, SynapticRelay<WanderSignal>> interceptor;

        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder mentalStateTracker(MentalStateTracker mst) { this.mentalStateTracker = mst; return this; }
        public Builder cognitiveManifold(CognitiveManifold cm) { this.cognitiveManifold = cm; return this; }
        public Builder hopfieldNetwork(ContinuousHopfieldNetwork chn) { this.hopfieldNetwork = chn; return this; }
        public Builder hebbianGraph(HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }
        public Builder homeostaticCore(HomeostaticCore hc) { this.homeostaticCore = hc; return this; }
        public Builder continuityMemory(ContinuityMemory crm) { this.continuityMemory = crm; return this; }
        public Builder aismeConfig(AismeProperties cfg) { this.aismeConfig = cfg; return this; }
        public Builder soulPriorPreference(float[] prior) { this.soulPriorPreference = prior; return this; }
        public Builder interceptor(Function<SynapticRelay<WanderSignal>, SynapticRelay<WanderSignal>> inc) { this.interceptor = inc; return this; }

        public WanderPathway build() {
            return new WanderPathway(this);
        }
    }
}
