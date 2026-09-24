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
package com.spectrayan.spector.synapse.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Verifies that namespaces resolve their own embedding provider, and that sharing is preserved where the
 * configuration is the same.
 *
 * <p>Before this, the embedding provider was hoisted once at first namespace open and handed to every
 * namespace in the process. {@code CompositionHoistTest} still pins that hoist for the no-override case,
 * which is the common deployment; these tests cover the case it cannot: two namespaces that resolve to
 * different configurations.</p>
 */
@DisplayName("Per-Namespace Embedding Resolution")
class PerNamespaceEmbeddingResolutionTest {

    private static final String ACCOUNT_ID = "0195500000001";
    private static final int DIMS = 16;

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private ObjectProvider<EmbeddingProvider> embedderProvider;
    private ObjectProvider<ObjectMapper> objectMapperProvider;

    /** Embedding provider whose vectors and reported model make its identity observable. */
    static final class NamedEmbedder implements EmbeddingProvider {
        private final String model;

        NamedEmbedder(String model) {
            this.model = model;
        }

        @Override
        public EmbeddingResult embed(String text) {
            return EmbeddingResult.of(new float[DIMS], model);
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }

        @Override
        public int dimensions() {
            return DIMS;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public int maxTokens() {
            return 8192;
        }

        @Override
        public void close() {
            // no resources
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        catalog = mock(AccountCatalog.class);
        synapseProps = new SynapseProperties();
        synapseProps.getMemory().setPersistencePath(tempDir.toString());
        synapseProps.getProvider().getEmbedding().setDimensions(DIMS);
        synapseProps.getNamespace().getTenantRooted().setEnabled(false);

        embedderProvider = mock(ObjectProvider.class);
        when(embedderProvider.getIfAvailable()).thenReturn(new NamedEmbedder("default-model"));

        objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable()).thenReturn(new ObjectMapper());
        when(objectMapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());

        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Test User", new AccountQuotas(10, 10, -1, -1),
                new AccountFlags(true, true, true), "ns-default", Instant.now());
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);
        registerNamespace("ns-1");
        registerNamespace("ns-2");
        registerNamespace("ns-3");
    }

    private void registerNamespace(String id) {
        NamespaceRecord record = new NamespaceRecord(
                id, id, ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, id, "", null, Instant.now(), null);
        when(catalog.resolve(ACCOUNT_ID, id)).thenReturn(Optional.of(record));
    }

    private NamespaceResolver newResolver() {
        return new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, 10);
    }

    private static ProviderConfig onnxConfig(String model) {
        return new ProviderConfig("onnx", "onnx", model, "", "", DIMS, Map.of());
    }

    @Test
    @DisplayName("with no resolver installed, every namespace shares the hoisted provider")
    void withoutResolverProviderIsShared() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
            EmbeddingProvider first = resolver.hoistedEmbeddingProvider();
            resolver.resolve(ACCOUNT_ID, "ns-2");

            assertThat(resolver.hoistedEmbeddingProvider()).isSameAs(first);
            // Nothing was pooled, so the default costs no extra provider.
            assertThat(resolver.providerPool().distinctConfigurations()).isZero();
        }
    }

    @Test
    @DisplayName("a namespace whose resolved config matches the default is not given its own provider")
    void matchingConfigurationReusesTheDefault() {
        try (NamespaceResolver resolver = newResolver()) {
            // Same model and width as the process default embedder.
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> onnxConfig("default-model"));

            resolver.resolve(ACCOUNT_ID, "ns-1");

            assertThat(resolver.providerPool().distinctConfigurations()).isZero();
        }
    }

    @Test
    @DisplayName("a resolver that throws does not stop the namespace opening on the default")
    void resolverFailureFallsBackToDefault() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> {
                throw new IllegalStateException("config database unavailable");
            });

            SpectorMemory memory = resolver.resolve(ACCOUNT_ID, "ns-1");

            assertThat(memory).isNotNull();
            assertThat(resolver.providerPool().distinctConfigurations()).isZero();
        }
    }

    @Test
    @DisplayName("the resolver receives the namespace id it is resolving for")
    void resolverReceivesNamespaceId() {
        var seen = new java.util.ArrayList<String>();
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> {
                seen.add(namespaceId);
                return null;
            });

            resolver.resolve(ACCOUNT_ID, "ns-1");
            resolver.resolve(ACCOUNT_ID, "ns-2");

            assertThat(seen).containsExactly("ns-1", "ns-2");
        }
    }

    @Test
    @DisplayName("evicting a namespace releases its pooled provider reference")
    void evictionReleasesThePoolReference() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> null);

            resolver.resolve(ACCOUNT_ID, "ns-1");
            assertThat(resolver.isHot("ns-1")).isTrue();

            resolver.evict("ns-1");

            assertThat(resolver.isHot("ns-1")).isFalse();
            assertThat(resolver.providerPool().distinctConfigurations()).isZero();
        }
    }

    @Test
    @DisplayName("the namespace marker records the embedder identity on first open")
    void markerRecordsEmbedderIdentity() throws Exception {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
        }

        Map<String, Object> marker = readMarker("ns-1");

        assertThat(marker).containsEntry(NamespaceResolver.MARKER_EMBEDDING_MODEL, "default-model");
        assertThat(marker).containsEntry(NamespaceResolver.MARKER_EMBEDDING_DIMENSIONS, DIMS);
        assertThat(marker).containsKey(NamespaceResolver.MARKER_EMBEDDING_PROVIDER);
    }

    @Test
    @DisplayName("reopening after a model change is refused, naming both models")
    void reopenAfterModelChangeIsRefused() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
        }

        // Same width, different model — the dangerous case. Every DIMENSIONS_MISMATCH check downstream
        // compares width only, so nothing else in the stack would notice.
        when(embedderProvider.getIfAvailable()).thenReturn(new NamedEmbedder("swapped-model"));

        try (NamespaceResolver resolver = newResolver()) {
            assertThat(catchOpen(resolver, "ns-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("default-model")
                    .hasMessageContaining("swapped-model")
                    .hasMessageContaining("re-embed");
        }
    }

    @Test
    @DisplayName("reopening with the recorded model still succeeds")
    void reopenWithSameModelSucceeds() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
        }
        try (NamespaceResolver resolver = newResolver()) {
            assertThat(resolver.resolve(ACCOUNT_ID, "ns-1")).isNotNull();
        }
    }

    @Test
    @DisplayName("a marker written before the identity fields existed still opens, and is backfilled")
    void preChangeMarkerStillOpens() throws Exception {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
        }
        // Strip the identity fields to simulate a namespace created before this change.
        Path markerFile = markerPath("ns-1");
        var mapper = new ObjectMapper();
        Map<String, Object> marker = readMarker("ns-1");
        marker.remove(NamespaceResolver.MARKER_EMBEDDING_MODEL);
        marker.remove(NamespaceResolver.MARKER_EMBEDDING_DIMENSIONS);
        marker.remove(NamespaceResolver.MARKER_EMBEDDING_PROVIDER);
        mapper.writeValue(markerFile.toFile(), marker);

        try (NamespaceResolver resolver = newResolver()) {
            assertThat(resolver.resolve(ACCOUNT_ID, "ns-1")).isNotNull();
        }

        // Backfilled rather than guessed at: recorded from what actually opened it, so the next open checks.
        assertThat(readMarker("ns-1"))
                .containsEntry(NamespaceResolver.MARKER_EMBEDDING_MODEL, "default-model");
    }

    @Test
    @DisplayName("one namespace's entity-extraction decision does not change another's")
    void entityExtractionModeIsPerNamespace() {
        // No LLM provider, so buildInstance downgrades LLM/NONE to DICTIONARY on its own snapshot.
        synapseProps.getMemory().getGraph().getEntity().setExtractionMode(EntityExtractionMode.NONE.name());

        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");

            // The shared Spring bean must be untouched. Before toSpectorProperties() deep-copied, the first
            // namespace's decision was written straight into it and every later namespace inherited it.
            assertThat(synapseProps.getMemory().getGraph().getEntity().getExtractionMode())
                    .isEqualTo(EntityExtractionMode.NONE.name());
        }
    }

    private static ProviderConfig fixedWidthConfig(String model, int dims) {
        return new ProviderConfig(FixedWidthTestProviderFactory.TYPE, FixedWidthTestProviderFactory.TYPE,
                model, "", "", dims, Map.of());
    }

    @Test
    @DisplayName("two namespaces get different providers, each at its own dimensionality")
    void twoNamespacesResolveToDifferentWidths() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> switch (namespaceId) {
                case "ns-1" -> fixedWidthConfig("narrow-model", 8);
                case "ns-2" -> fixedWidthConfig("wide-model", 32);
                default -> null;
            });

            SpectorMemory first = resolver.resolve(ACCOUNT_ID, "ns-1");
            SpectorMemory second = resolver.resolve(ACCOUNT_ID, "ns-2");

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();

            // Two distinct configurations means two pooled providers, not one shared one.
            assertThat(resolver.providerPool().distinctConfigurations()).isEqualTo(2);
            assertThat(resolver.providerPool().referenceCount(
                    com.spectrayan.spector.provider.ProviderFingerprint.of(fixedWidthConfig("narrow-model", 8))))
                    .isEqualTo(1);
            assertThat(resolver.providerPool().referenceCount(
                    com.spectrayan.spector.provider.ProviderFingerprint.of(fixedWidthConfig("wide-model", 32))))
                    .isEqualTo(1);

            // Each namespace recorded its own width and model, which is the on-disk consequence of the two
            // namespaces having genuinely different embedders rather than sharing one.
            assertThat(readMarkerQuietly("ns-1"))
                    .containsEntry(NamespaceResolver.MARKER_EMBEDDING_MODEL, "narrow-model")
                    .containsEntry(NamespaceResolver.MARKER_EMBEDDING_DIMENSIONS, 8);
            assertThat(readMarkerQuietly("ns-2"))
                    .containsEntry(NamespaceResolver.MARKER_EMBEDDING_MODEL, "wide-model")
                    .containsEntry(NamespaceResolver.MARKER_EMBEDDING_DIMENSIONS, 32);
        }
    }

    @Test
    @DisplayName("two namespaces on identical configuration share one pooled provider")
    void identicalConfigurationSharesOnePooledProvider() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver(
                    (tenantId, namespaceId) -> fixedWidthConfig("shared-model", 8));

            resolver.resolve(ACCOUNT_ID, "ns-1");
            resolver.resolve(ACCOUNT_ID, "ns-2");

            // The assertion that separates this design from a per-namespace one: two namespaces, one provider.
            assertThat(resolver.providerPool().distinctConfigurations()).isEqualTo(1);
            assertThat(resolver.providerPool().referenceCount(
                    com.spectrayan.spector.provider.ProviderFingerprint.of(fixedWidthConfig("shared-model", 8))))
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("a namespace configured for an unknown provider type is refused, not silently defaulted")
    void unknownProviderTypeIsRefused() {
        try (NamespaceResolver resolver = newResolver()) {
            resolver.setEmbeddingConfigResolver((tenantId, namespaceId) -> new ProviderConfig(
                    "no-such-provider", "no-such-provider", "m", "", "", DIMS, Map.of()));

            // Substituting the default would embed into a different vector space than configured.
            assertThat(catchOpen(resolver, "ns-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no-such-provider");
        }
    }

    private Map<String, Object> readMarkerQuietly(String namespaceId) {
        try {
            return readMarker(namespaceId);
        } catch (Exception e) {
            throw new AssertionError("could not read marker for " + namespaceId, e);
        }
    }

    @Test
    @DisplayName("opening a namespace does not rewrite the global storage root")
    void openingANamespaceDoesNotRewriteTheStorageRoot() {
        String rootBefore = synapseProps.getMemory().getPersistencePath();

        try (NamespaceResolver resolver = newResolver()) {
            resolver.resolve(ACCOUNT_ID, "ns-1");
            resolver.resolve(ACCOUNT_ID, "ns-2");
        }

        // SpectorMemoryBuilder.persistence(Path) writes the path it is given back into the properties it
        // holds. While toSpectorProperties() handed out the Spring bean's own MemoryProperties, that wrote
        // *one namespace's directory* into the global persistence path — so every later reader of
        // spector.memory.persistence-path, including a freshly constructed resolver's storage root, got a
        // namespace-specific directory and nested the next namespace inside it.
        assertThat(synapseProps.getMemory().getPersistencePath()).isEqualTo(rootBefore);
    }

    @Test
    @DisplayName("toSpectorProperties hands out an independent snapshot")
    void snapshotIsIndependent() {
        var first = synapseProps.toSpectorProperties();
        var second = synapseProps.toSpectorProperties();

        first.memory().getGraph().getEntity().setExtractionMode(EntityExtractionMode.LLM.name());

        assertThat(second.memory().getGraph().getEntity().getExtractionMode())
                .isNotEqualTo(EntityExtractionMode.LLM.name());
        assertThat(synapseProps.getMemory().getGraph().getEntity().getExtractionMode())
                .isNotEqualTo(EntityExtractionMode.LLM.name());
        assertThat(first.memory()).isNotSameAs(second.memory());
        assertThat(first.memory()).isNotSameAs(synapseProps.getMemory());
    }

    private Throwable catchOpen(NamespaceResolver resolver, String namespaceId) {
        try {
            resolver.resolve(ACCOUNT_ID, namespaceId);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Path markerPath(String namespaceId) {
        var placement = com.spectrayan.spector.kernel.storage.NamespacePathResolver.resolve(
                synapseProps.remembererRoot(), null, namespaceId);
        return placement.dir().resolve(com.spectrayan.spector.kernel.storage.StoragePaths.FILE_NAMESPACE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMarker(String namespaceId) throws Exception {
        return new ObjectMapper().readValue(markerPath(namespaceId).toFile(), java.util.LinkedHashMap.class);
    }
}
