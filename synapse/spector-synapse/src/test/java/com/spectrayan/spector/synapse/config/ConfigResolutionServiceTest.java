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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;
import com.spectrayan.spector.synapse.config.service.ConfigResolutionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigResolutionService: Projection from SpectorProperties")
class ConfigResolutionServiceTest {

    @Test
    @DisplayName("projects ingestion defaults directly from SpectorProperties chunk configuration")
    void testIngestionDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.INGESTION);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("chunk-size"))
                .isEqualTo(props.memory().getRemember().getChunk().getSize());
        assertThat(resolved.get("chunk-overlap"))
                .isEqualTo(props.memory().getRemember().getChunk().getOverlap());
        assertThat(resolved.get("parent-child-linking")).isEqualTo(false);
    }

    @Test
    @DisplayName("projects rag defaults directly from SpectorProperties recall configuration")
    void testRagDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.RAG);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("top-k"))
                .isEqualTo(com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_QUERY_DEFAULT_TOP_K);
        assertThat(resolved.get("similarity-threshold"))
                .isEqualTo((double) props.memory().getGraphExpansionThreshold());
    }

    @Test
    @DisplayName("projects llm provider defaults directly from SpectorProperties generation configuration")
    void testLlmProviderDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.LLM_PROVIDER);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("provider"))
                .isEqualTo(props.provider().getGeneration().getType());
        assertThat(resolved.get("model"))
                .isEqualTo(props.provider().getGeneration().getModel());
        assertThat(resolved.get("base-url"))
                .isEqualTo(props.provider().getGeneration().getBaseUrl());
        assertThat(resolved.get("temperature"))
                .isEqualTo(0.7);
    }

    @Test
    @DisplayName("projects memory defaults directly from SpectorProperties")
    void testMemoryDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.MEMORY);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("capacity")).isEqualTo(props.memory().getCapacity());
        assertThat(resolved.get("surprise-warmup")).isEqualTo(props.memory().getRemember().getSurpriseWarmup());
        // 'dimensions' belongs to the embedding category alone. It used to appear in both, which let a
        // tenant override one copy and leave the other stale.
        assertThat(resolved).doesNotContainKey("dimensions");
    }

    @Test
    @DisplayName("projects embedding dimensionality from the embedding property, not the memory one")
    void testEmbeddingDimensionsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.EMBEDDING_PROVIDER);
        SpectorProperties props = SpectorProperties.load();

        assertThat(resolved.get("dimensions")).isEqualTo(props.provider().getEmbedding().getDimensions());
    }

    @Test
    @DisplayName("projects recall and hnsw defaults directly from SpectorProperties")
    void testRecallAndHnswDefaultsProjection() {
        ConfigRepository repo = Mockito.mock(ConfigRepository.class);
        ConfigResolutionService service = new ConfigResolutionService(repo);

        Map<String, Object> recall = service.resolve(null, null, ConfigCategory.RECALL);
        assertThat(recall.get("scoring-mode")).isEqualTo("COGNITIVE");
        assertThat(recall.get("score-fusion-mode")).isEqualTo("MULTIPLICATIVE");

        Map<String, Object> hnsw = service.resolve(null, null, ConfigCategory.HNSW);
        assertThat(hnsw.get("ef-search")).isNotNull();
        assertThat(hnsw.get("m")).isNotNull();
        assertThat(hnsw.get("ef-construction")).isNotNull();
    }
}
