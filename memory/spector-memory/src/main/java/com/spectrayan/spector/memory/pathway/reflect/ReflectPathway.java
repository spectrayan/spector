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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.commons.pathway.AbstractPathway;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ConductionOutcome;
import com.spectrayan.spector.commons.pathway.DefaultPathwayContext;
import com.spectrayan.spector.commons.template.TemplateEngine;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.config.properties.CircadianProperties;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay;
import com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay;
import com.spectrayan.spector.memory.api.ImportanceProvider;
import com.spectrayan.spector.memory.cortex.CentroidRouter;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.TypeNormalizer;
import com.spectrayan.spector.kernel.store.HebbianGraphBase;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import com.spectrayan.spector.kernel.id.MemoryIdGenerator;
import com.spectrayan.spector.memory.model.ReflectReport;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.reflect.relay.CrossLayerPromotionRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.EntityMaintenanceRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.EpisodicLogConsolidationRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.HebbianHomeostasisRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.IdiolectLearningRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.ProactiveInterferenceRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.ProceduralCrystallizationRelay;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectRecipe;
import com.spectrayan.spector.memory.pathway.reflect.relay.ReflectSignal;
import com.spectrayan.spector.memory.pathway.reflect.relay.SoulDriftRefusionRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.SpectralSparsificationRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.SynapticPruningRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.TemporalPruningRelay;
import com.spectrayan.spector.memory.pathway.reflect.relay.WalJournalRelay;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.session.EpisodicSessionIndex;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates biological sleep consolidation (reflection) using the composable Cognitive Pathway Engine.
 *
 * <p>Consolidates NREM deep sleep downscaling, REM replay gist extraction, identity soul-drift
 * re-fusion (#503), proactive interference, Hebbian synaptic homeostasis, temporal pruning,
 * STC cross-layer promotion, Riemannian cognitive manifold consolidation, Soft Identity Anchor Lyapunov
 * restoring force, and entity maintenance into a unified, observable 14-relay pipeline.</p>
 */
public final class ReflectPathway extends AbstractPathway<ReflectSignal, ReflectReport> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReflectPathway.class);

    private final ScalarQuantizer quantizer;
    /** Narrowed from RememberPathway to the one capability Reflect needs (ADR-0035 §8.1b). */
    private final SoulVersionSource soulVersionSource;
    private final EmbeddingProvider embeddingProvider;
    private final LlmProvider textGenerator;
    private final ImportanceProvider importanceProvider;
    private final CircadianProperties policy;
    private final CentroidRouter centroidRouter;
    private final TemplateEngine templateEngine;
    private final EpisodicSessionIndex episodicSessionIndex;
    private final ProvenanceMemory provenanceMemory;
    private final MemoryIdGenerator idGenerator;

    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final MemoryWal wal;
    private final TypeNormalizer typeNormalizer;

    private final int minClusterSize;
    private final boolean pinSourceEpisodes;
    private final int pinnedQuota;
    private final boolean soulDriftRefusionEnabled;
    private final int soulDriftRefusionBatchSize;
    private final int temporalRetentionDays;
    private final boolean entityResolutionEnabled;
    private final boolean entityShadowMode;
    private final float entityCosineThreshold;
    private final com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker;
    private final CognitiveManifold cognitiveManifold;
    private final boolean softIdentityAnchorEnabled;
    private final float identityAnchorEta;
    private final float identityLyapunovThreshold;

    private ReflectPathway(final Builder builder) {
        super("reflect", ReflectSignal.class, ReflectReport.class);
        this.quantizer = builder.quantizer;
        this.soulVersionSource = builder.soulVersionSource;
        this.embeddingProvider = builder.embeddingProvider;
        this.textGenerator = builder.textGenerator;
        this.importanceProvider = builder.importanceProvider != null ? builder.importanceProvider : ImportanceProvider.baseline();
        this.policy = builder.policy != null ? builder.policy : CircadianProperties.DEFAULT;
        this.centroidRouter = builder.centroidRouter;
        this.templateEngine = builder.templateEngine != null ? builder.templateEngine : TemplateEngine.getDefault();
        this.episodicSessionIndex = builder.episodicSessionIndex;
        this.provenanceMemory = builder.provenanceMemory;
        this.idGenerator = builder.idGenerator;

        this.hebbianGraph = builder.hebbianGraph;
        this.temporalChain = builder.temporalChain;
        this.entityDirectory = builder.entityDirectory;
        this.hyperEntityGraph = builder.hyperEntityGraph;
        this.wal = builder.wal;
        this.typeNormalizer = builder.typeNormalizer;

        this.minClusterSize = builder.minClusterSize;
        this.pinSourceEpisodes = builder.pinSourceEpisodes;
        this.pinnedQuota = builder.pinnedQuota;
        this.soulDriftRefusionEnabled = builder.soulDriftRefusionEnabled;
        this.soulDriftRefusionBatchSize = builder.soulDriftRefusionBatchSize;
        this.temporalRetentionDays = builder.temporalRetentionDays;
        this.entityResolutionEnabled = builder.entityResolutionEnabled;
        this.entityShadowMode = builder.entityShadowMode;
        this.entityCosineThreshold = builder.entityCosineThreshold;
        this.mentalStateTracker = builder.mentalStateTracker;
        this.cognitiveManifold = builder.cognitiveManifold;
        this.softIdentityAnchorEnabled = builder.softIdentityAnchorEnabled;
        this.identityAnchorEta = builder.identityAnchorEta;
        this.identityLyapunovThreshold = builder.identityLyapunovThreshold;

        final var manifoldRelay = builder.manifoldConsolidationRelay != null
                ? builder.manifoldConsolidationRelay
                : (builder.cognitiveManifold != null
                        ? new com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay(
                                builder.cognitiveManifold, null, builder.coActivationSupplier)
                        : null);

        final var anchorRelay = builder.softIdentityAnchorRelay != null
                ? builder.softIdentityAnchorRelay
                : new SoftIdentityAnchorRelay(builder.identityAnchorEta, builder.identityLyapunovThreshold);

        // Composer + recipe directly. ReflectPathwayFactory is @Deprecated and exists only
        // as a shim for external callers; production must not route through it.
        final PathwayComposer<ReflectSignal> composer = PathwayComposer.of("reflect");
        if (builder.interceptor != null) {
            composer.withInterceptor(builder.interceptor);
        }
        ReflectRecipe.builder()
                .pruningRelay(new SynapticPruningRelay())
                .logConsolidationRelay(new EpisodicLogConsolidationRelay())
                .soulDriftRelay(new SoulDriftRefusionRelay())
                .proceduralRelay(new ProceduralCrystallizationRelay())
                .interferenceRelay(new ProactiveInterferenceRelay())
                .hebbianRelay(new HebbianHomeostasisRelay())
                .temporalRelay(new TemporalPruningRelay())
                .promotionRelay(new CrossLayerPromotionRelay())
                .entityRelay(new EntityMaintenanceRelay())
                .sparsificationRelay(new SpectralSparsificationRelay())
                .manifoldConsolidationRelay(manifoldRelay)
                .softIdentityAnchorRelay(anchorRelay)
                .walRelay(new WalJournalRelay())
                .idiolectRelay(new com.spectrayan.spector.memory.pathway.reflect.relay.IdiolectLearningRelay())
                .build()
                .compose(composer);
        initEngine(composer.build());
    }

    public PathwayEngine<ReflectSignal> pathway() {
        return engine();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected ReflectReport project(final ReflectSignal signal) {
        ConductionOutcome outcome = signal.context() != null ? signal.context().outcome() : null;
        if (outcome != null && outcome.finish() == ConductionOutcome.Finish.SHORT_CIRCUITED) {
            return ReflectReport.empty(outcome);
        }
        final ReflectReport report = signal.buildReport();
        log.info("ReflectPathway: sleep cycle complete in {}ms — consolidated={}, tombstoned={}, compacted={}, soulRefused={}",
                report.duration().toMillis(), report.consolidatedCount(), report.tombstonedCount(),
                report.compactedPartitions(), report.soulRefusedCount());
        return report;
    }

    /**
     * Executes the reflection pathway for an explicit namespace kernel and populated signal.
     *
     * @param kernel the namespace kernel
     * @param signal the populated reflection signal
     * @return the resulting {@link ReflectReport}
     */
    public ReflectReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel, final ReflectSignal signal) {
        return execute(kernel, signal, this.soulVersionSource);
    }

    /**
     * Real implementation. {@code svs} is passed explicitly rather than read from a field so the
     * deprecated {@code RememberPathway} overloads can supply it per call — ReflectPathway is a
     * process-wide singleton, so a field write per invocation would race across namespaces.
     */
    private ReflectReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                 final ReflectSignal signal,
                                 final SoulVersionSource svs) {
        Objects.requireNonNull(signal, "ReflectSignal cannot be null");
        final DefaultPathwayContext.Builder ctxBuilder = signal.context() != null
                ? DefaultPathwayContext.from(signal.context())
                : DefaultPathwayContext.builder();
        if (kernel != null) {
            ctxBuilder.namespaceId(kernel.namespaceId());
            ctxBuilder.bindIfAbsent(com.spectrayan.spector.kernel.api.NamespaceKernel.class, kernel);
        }
        if (svs != null) {
            ctxBuilder.bindIfAbsent(SoulVersionSource.class, svs);
        }
        if (signal.quantizer() != null) {
            ctxBuilder.bindIfAbsent(ScalarQuantizer.class, signal.quantizer());
        } else if (this.quantizer != null) {
            ctxBuilder.bindIfAbsent(ScalarQuantizer.class, this.quantizer);
        }
        signal.bind(ctxBuilder.build());
        return conduct(signal);
    }

    /**
     * Reflects using a caller-supplied signal builder — the recommended entry point.
     *
     * <p>ADR-0035 R2.3: the collaborator-threading {@code execute}/{@code reflect} overloads
     * exist only for backwards compatibility. New callers should either build a
     * {@link ReflectSignal} and call {@link #conduct(ReflectSignal)}, or hand a partially
     * populated builder here. Nested gist writes resolve {@code RememberPathway} from the
     * {@code PathwayCatalog}; it is never passed in.</p>
     *
     * @param signalBuilder a populated {@link ReflectSignal.Builder}
     * @return the reflection report
     */
    public ReflectReport reflect(final ReflectSignal.Builder signalBuilder) {
        Objects.requireNonNull(signalBuilder, "signalBuilder cannot be null");
        return conduct(signalBuilder.build());
    }

    /**
     * Reflects over an explicit sweep without threading a {@link RememberPathway}.
     *
     * <p>The non-deprecated replacement for the collaborator overloads: soul version comes
     * from the {@link SoulVersionSource} on the context, and nested writes go through the
     * catalog.</p>
     *
     * @param kernel            namespace kernel, may be null
     * @param partitionManager  partition manager for the sweep
     * @param index             memory index
     * @param salienceProfile   active salience profile
     * @param sessionIndex      episodic session index, or null to use this pathway's own
     * @param sweepSpec         sweep specification, or null for a full cycle
     * @param checkpointStore   checkpoint store, or null for none
     * @return the reflection report
     */
    public ReflectReport reflect(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                 final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final SalienceProfile salienceProfile,
                                 final EpisodicSessionIndex sessionIndex,
                                 final ReflectSweepSpec sweepSpec,
                                 final com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore checkpointStore) {
        return execute(kernel, partitionManager, index, null, salienceProfile,
                sessionIndex, sweepSpec, checkpointStore);
    }

    /**
     * Executes a sleep reflection cycle for an explicit namespace kernel.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                 final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile) {
        return execute(kernel, partitionManager, index, rememberPathway, salienceProfile, this.episodicSessionIndex);
    }

    /**
     * Executes a sleep reflection cycle with explicit session index for an explicit namespace kernel.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                 final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile,
                                 final EpisodicSessionIndex sessionIndex) {
        return execute(kernel, partitionManager, index, rememberPathway, salienceProfile, sessionIndex, ReflectSweepSpec.fullCycle(), null);
    }

    /**
     * Executes a reflection cycle with an explicit sweep specification, checkpoint store, and namespace kernel.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport execute(final com.spectrayan.spector.kernel.api.NamespaceKernel kernel,
                                 final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile,
                                 final EpisodicSessionIndex sessionIndex,
                                 final ReflectSweepSpec sweepSpec,
                                 final com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore checkpointStore) {
        ReflectCheckpoint initialCheckpoint = null;
        if (checkpointStore != null && sweepSpec != null && sweepSpec.sweepId() != null) {
            initialCheckpoint = checkpointStore.load(sweepSpec.sweepId())
                    .orElseGet(() -> ReflectCheckpoint.initial(sweepSpec.sweepId()));
        }

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .index(index)
                .quantizer(quantizer)
                .embeddingProvider(embeddingProvider)
                .textGenerator(textGenerator)
                .importanceProvider(importanceProvider)
                .salienceProfile(salienceProfile)
                .policy(policy)
                .centroidRouter(centroidRouter)
                .templateEngine(templateEngine)
                .episodicSessionIndex(sessionIndex != null ? sessionIndex : this.episodicSessionIndex)
                .provenanceMemory(this.provenanceMemory)
                .idGenerator(this.idGenerator)
                .hebbianGraph(hebbianGraph)
                .temporalChain(temporalChain)
                .entityDirectory(entityDirectory)
                .hyperEntityGraph(hyperEntityGraph)
                .wal(wal)
                .typeNormalizer(typeNormalizer)
                .minClusterSize(minClusterSize)
                .pinSourceEpisodes(pinSourceEpisodes)
                .pinnedQuota(pinnedQuota)
                .soulDriftRefusionEnabled(soulDriftRefusionEnabled)
                .soulDriftRefusionBatchSize(soulDriftRefusionBatchSize)
                .temporalRetentionDays(temporalRetentionDays)
                .entityResolutionEnabled(entityResolutionEnabled)
                .entityShadowMode(entityShadowMode)
                .entityCosineThreshold(entityCosineThreshold)
                .mentalStateTracker(mentalStateTracker)
                .cognitiveManifold(cognitiveManifold)
                .softIdentityAnchorEnabled(softIdentityAnchorEnabled)
                .identityAnchorEta(identityAnchorEta)
                .identityLyapunovThreshold(identityLyapunovThreshold)
                .sweepSpec(sweepSpec != null ? sweepSpec : ReflectSweepSpec.fullCycle())
                .checkpointStore(checkpointStore)
                .checkpoint(initialCheckpoint)
                .build();

        return execute(kernel, signal);
    }

    public CircadianProperties policy() {
        return policy;
    }

    /**
     * Convenience method to execute a sleep reflection cycle.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport reflect(final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile) {
        return execute(null, partitionManager, index, rememberPathway, salienceProfile);
    }

    /**
     * Executes a sleep reflection cycle with explicit session index for prior-turn context.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport reflect(final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile,
                                 final EpisodicSessionIndex sessionIndex) {
        return execute(null, partitionManager, index, rememberPathway, salienceProfile, sessionIndex);
    }

    /**
     * Executes a reflection cycle with an explicit sweep specification and checkpoint store.
     *
     * @deprecated Use {@code conduct(ReflectSignal)} with RememberPathway registered in the catalog.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public ReflectReport reflect(final PartitionManager partitionManager,
                                 final MemoryIndex index,
                                 final RememberPathway rememberPathway,
                                 final SalienceProfile salienceProfile,
                                 final EpisodicSessionIndex sessionIndex,
                                 final ReflectSweepSpec sweepSpec,
                                 final com.spectrayan.spector.memory.pathway.reflect.spi.ReflectCheckpointStore checkpointStore) {
        return execute(null, partitionManager, index, rememberPathway, salienceProfile, sessionIndex, sweepSpec, checkpointStore);
    }

    @Override
    public void close() {
        // Any resources closed gracefully
    }

    public static final class Builder {
        private ScalarQuantizer quantizer;
        private SoulVersionSource soulVersionSource;
        private EmbeddingProvider embeddingProvider;
        private LlmProvider textGenerator;
        private ImportanceProvider importanceProvider;
        private CircadianProperties policy = CircadianProperties.DEFAULT;
        private CentroidRouter centroidRouter;
        private TemplateEngine templateEngine;
        private EpisodicSessionIndex episodicSessionIndex;
        private ProvenanceMemory provenanceMemory;
        private MemoryIdGenerator idGenerator;

        private HebbianGraphBase hebbianGraph;
        private TemporalChainMemory temporalChain;
        private EntityDirectory entityDirectory;
        private HyperEntityGraphMemory hyperEntityGraph;
        private MemoryWal wal;
        private TypeNormalizer typeNormalizer;

        private int minClusterSize = SpectorPropertyConstants.DEFAULT_MEMORY_REFLECT_MIN_CLUSTER_SIZE;
        private boolean pinSourceEpisodes = false;
        private int pinnedQuota = 10_000;
        private boolean soulDriftRefusionEnabled = SpectorPropertyConstants.DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_ENABLED;
        private int soulDriftRefusionBatchSize = SpectorPropertyConstants.DEFAULT_CONSOLIDATION_SOUL_DRIFT_REFUSION_BATCH_SIZE;
        private int temporalRetentionDays = 30;
        private boolean entityResolutionEnabled = false;
        private boolean entityShadowMode = true;
        private float entityCosineThreshold = 0.85f;

        private CognitiveManifold cognitiveManifold;
        private com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay manifoldConsolidationRelay;
        private com.spectrayan.spector.memory.aisme.relay.SoftIdentityAnchorRelay softIdentityAnchorRelay;
        private com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mentalStateTracker;
        private java.util.function.Supplier<java.util.List<float[]>> coActivationSupplier;
        private java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>> interceptor;

        private boolean softIdentityAnchorEnabled = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_SOFT_IDENTITY_ANCHOR_ENABLED;
        private float identityAnchorEta = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_ANCHOR_ETA;
        private float identityLyapunovThreshold = SpectorPropertyConstants.DEFAULT_MEMORY_AISME_IDENTITY_LYAPUNOV_THRESHOLD;

        public Builder quantizer(ScalarQuantizer q) { this.quantizer = q; return this; }

        /**
         * Supplies the soul-version accessor.
         *
         * @param svs soul version source (RememberPathway implements this)
         * @return this builder
         */
        public Builder soulVersionSource(SoulVersionSource svs) { this.soulVersionSource = svs; return this; }
        public Builder embeddingProvider(EmbeddingProvider ep) { this.embeddingProvider = ep; return this; }
        public Builder textGenerator(LlmProvider tg) { this.textGenerator = tg; return this; }
        public Builder importanceProvider(ImportanceProvider ip) { this.importanceProvider = ip; return this; }
        public Builder policy(CircadianProperties p) { this.policy = p; return this; }
        public Builder centroidRouter(CentroidRouter cr) { this.centroidRouter = cr; return this; }
        public Builder templateEngine(TemplateEngine te) { this.templateEngine = te; return this; }
        public Builder episodicSessionIndex(EpisodicSessionIndex esi) { this.episodicSessionIndex = esi; return this; }
        public Builder provenanceMemory(ProvenanceMemory pm) { this.provenanceMemory = pm; return this; }
        public Builder idGenerator(MemoryIdGenerator gen) { this.idGenerator = gen; return this; }

        public Builder hebbianGraph(HebbianGraphBase hg) { this.hebbianGraph = hg; return this; }
        public Builder temporalChain(TemporalChainMemory tc) { this.temporalChain = tc; return this; }
        public Builder entityDirectory(EntityDirectory ed) { this.entityDirectory = ed; return this; }
        public Builder hyperEntityGraph(HyperEntityGraphMemory heg) { this.hyperEntityGraph = heg; return this; }
        public Builder wal(MemoryWal w) { this.wal = w; return this; }
        public Builder typeNormalizer(TypeNormalizer tn) { this.typeNormalizer = tn; return this; }

        public Builder minClusterSize(int sz) { this.minClusterSize = sz; return this; }
        public Builder pinSourceEpisodes(boolean pin) { this.pinSourceEpisodes = pin; return this; }
        public Builder pinnedQuota(int quota) { this.pinnedQuota = quota; return this; }
        public Builder soulDriftRefusionEnabled(boolean enabled) { this.soulDriftRefusionEnabled = enabled; return this; }
        public Builder soulDriftRefusionBatchSize(int batch) { this.soulDriftRefusionBatchSize = batch; return this; }
        public Builder temporalRetentionDays(int days) { this.temporalRetentionDays = days; return this; }
        public Builder entityResolutionEnabled(boolean enabled) { this.entityResolutionEnabled = enabled; return this; }
        public Builder entityShadowMode(boolean shadow) { this.entityShadowMode = shadow; return this; }
        public Builder entityCosineThreshold(float threshold) { this.entityCosineThreshold = threshold; return this; }

        public Builder cognitiveManifold(CognitiveManifold cm) {
            this.cognitiveManifold = cm;
            return this;
        }

        public Builder manifoldConsolidationRelay(com.spectrayan.spector.memory.aisme.relay.ManifoldConsolidationRelay mcr) {
            this.manifoldConsolidationRelay = mcr;
            return this;
        }

        public Builder softIdentityAnchorRelay(SoftIdentityAnchorRelay siar) {
            this.softIdentityAnchorRelay = siar;
            return this;
        }

        public Builder mentalStateTracker(com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker mst) {
            this.mentalStateTracker = mst;
            return this;
        }

        public Builder coActivationSupplier(java.util.function.Supplier<java.util.List<float[]>> supplier) {
            this.coActivationSupplier = supplier;
            return this;
        }

        public Builder interceptor(
                final java.util.function.Function<com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>, com.spectrayan.spector.commons.pathway.SynapticRelay<ReflectSignal>> interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        public Builder softIdentityAnchorEnabled(boolean enabled) {
            this.softIdentityAnchorEnabled = enabled;
            return this;
        }

        public Builder identityAnchorEta(float eta) {
            this.identityAnchorEta = eta;
            return this;
        }

        public Builder identityLyapunovThreshold(float threshold) {
            this.identityLyapunovThreshold = threshold;
            return this;
        }

        public ReflectPathway build() {
            return new ReflectPathway(this);
        }
    }
}
