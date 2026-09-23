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
package com.spectrayan.spector.memory.runtime;

import com.spectrayan.spector.commons.pathway.PathwayCatalog;
import com.spectrayan.spector.commons.pathway.PathwayContext;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.pathway.decide.DecidePathway;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideReport;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;
import com.spectrayan.spector.memory.pathway.dream.DreamPathway;
import com.spectrayan.spector.memory.pathway.express.ExpressPathway;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressReport;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.pathway.recall.RecallPathway;
import com.spectrayan.spector.memory.pathway.reflect.ReflectPathway;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.pathway.skill.SkillPathway;
import com.spectrayan.spector.memory.pathway.wander.WanderPathway;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phase 4: SpectorRuntime hosts DefaultPathwayCatalog and Pathway<I, O> pathways (ADR-0035 M2/M3)")
class PathwayCatalogIntegrationTest {

    private static final int DIMS = 4;

    private static final EmbeddingProvider DETERMINISTIC_EMBEDDER = new EmbeddingProvider() {
        @Override
        public EmbeddingResult embed(String text) {
            return EmbeddingResult.of(new float[]{0.1f, 0.2f, 0.3f, 0.4f}, "test-model");
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
            return "deterministic-test";
        }
    };

    @Test
    @DisplayName("Catalog hosts express and decide pathways out of the box")
    void testInitialCatalogPathways() {
        SpectorProperties props = SpectorProperties.builder().build();

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(DETERMINISTIC_EMBEDDER)
                .build()) {

            PathwayCatalog catalog = runtime.catalog();
            assertThat(catalog).isNotNull();

            assertThat(catalog.find(ExpressPathway.class)).isPresent();
            assertThat(catalog.find(DecidePathway.class)).isPresent();
            assertThat(catalog.find(SkillPathway.class)).isPresent();

            ExpressPathway express = (ExpressPathway) catalog.require(ExpressPathway.class);
            assertThat(express).isSameAs(runtime.expressPathway());

            DecidePathway decide = (DecidePathway) catalog.require(DecidePathway.class);
            assertThat(decide).isSameAs(runtime.decidePathway());

            SkillPathway skill = (SkillPathway) catalog.require(SkillPathway.class);
            assertThat(skill).isSameAs(runtime.skillPathway());

            PathwayContext processContext = runtime.processContext();
            assertThat(processContext).isNotNull();
            assertThat(processContext.catalog()).isSameAs(catalog);
            assertThat(processContext.get(SpectorProperties.class)).isSameAs(props);
            assertThat(processContext.get(EmbeddingProvider.class)).isSameAs(DETERMINISTIC_EMBEDDER);
        }
    }

    @Test
    @DisplayName("Attached namespace registers all 7 domain pathways in catalog (ADR-0035 M3)")
    void testAllPathwaysRegisteredOnAttach(@TempDir Path tempDir) {
        SpectorProperties props = SpectorProperties.builder().build();
        props.memory().setDimensions(DIMS);

        try (SpectorRuntime runtime = SpectorRuntime.builder()
                .properties(props)
                .embeddingProvider(DETERMINISTIC_EMBEDDER)
                .build()) {

            SpectorMemory mem = runtime.attach("ns-catalog-test", b -> b.persistence(tempDir.resolve("mem")));
            try {
                PathwayCatalog catalog = runtime.catalog();

                assertThat(catalog.find(RecallPathway.class)).isPresent();
                assertThat(catalog.find(ReflectPathway.class)).isPresent();
                assertThat(catalog.find(ExpressPathway.class)).isPresent();
                assertThat(catalog.find(DreamPathway.class)).isPresent();
                assertThat(catalog.find(DecidePathway.class)).isPresent();
                assertThat(catalog.find(WanderPathway.class)).isPresent();
                assertThat(catalog.find(SkillPathway.class)).isPresent();

                assertThat(catalog.require(RecallPathway.class)).isSameAs(runtime.recallPathway());
                assertThat(catalog.require(ReflectPathway.class)).isSameAs(runtime.reflectPathway());
                assertThat(catalog.require(ExpressPathway.class)).isSameAs(runtime.expressPathway());
                assertThat(catalog.require(DreamPathway.class)).isSameAs(runtime.dreamPathway());
                assertThat(catalog.require(DecidePathway.class)).isSameAs(runtime.decidePathway());
                assertThat(catalog.require(WanderPathway.class)).isSameAs(runtime.wanderPathway());
                assertThat(catalog.require(SkillPathway.class)).isSameAs(runtime.skillPathway());

                RememberPathway rememberPathway = ((com.spectrayan.spector.memory.DefaultSpectorMemory) mem).rememberPathway();
                assertThat(rememberPathway).isNotNull();
                catalog.register(RememberPathway.class, rememberPathway);
                assertThat(catalog.find(RememberPathway.class)).isPresent();
                assertThat(catalog.require(RememberPathway.class)).isSameAs(rememberPathway);

                PathwayContext nsCtx = runtime.contextFor("ns-catalog-test", true);
                assertThat(nsCtx.namespaceId()).isEqualTo("ns-catalog-test");
                assertThat(nsCtx.traceEnabled()).isTrue();
                assertThat(nsCtx.catalog()).isSameAs(catalog);

                ExpressReport expressReport = catalog.require(ExpressPathway.class)
                        .conduct(nsCtx, ExpressSignal.builder().queryText("test-express").build());
                assertThat(expressReport).isNotNull();

                DecideReport decideReport = catalog.require(DecidePathway.class)
                        .conduct(nsCtx, DecideSignal.builder().build());
                assertThat(decideReport).isNotNull();
            } finally {
                mem.close();
            }
        }
    }
}
