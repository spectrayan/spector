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
package com.spectrayan.spector.memory.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.extraction.SpanMention;

/**
 * GLiNER entity wiring without a model file: phrase-linked relations, and a
 * startup failure when the model path is empty.
 */
class GlinerEntityExtractorTest {

    @Test
    @DisplayName("works at and in-location become MEMBER_OF and LOCATED_IN")
    void phraseLinksRelations() {
        String text = "Alex works at Acme in Denver";
        SpanExtractor spans = new SpanExtractor() {
            @Override
            public List<SpanMention> extract(String ignored, List<String> labels) {
                return List.of(
                        new SpanMention("Alex", "person", 0, 4, 0.9f),
                        new SpanMention("Acme", "organization", 14, 18, 0.8f),
                        new SpanMention("Denver", "location", 22, 28, 0.8f));
            }

            @Override
            public void close() {
            }
        };
        List<ExtractedEntity> entities = new GlinerEntityExtractor(spans, List.of("person"), 10)
                .extract("m1", text);
        assertThat(entities).extracting(ExtractedEntity::name).containsExactly("Alex", "Acme", "Denver");
        assertThat(entities.get(0).relations()).extracting(EntityRelation::relationType)
                .containsExactly("MEMBER_OF");
        assertThat(entities.get(0).relations()).extracting(EntityRelation::targetEntityName)
                .containsExactly("Acme");
        assertThat(entities.get(1).relations()).extracting(EntityRelation::relationType)
                .containsExactly("LOCATED_IN");
    }

    @Test
    @DisplayName("extraction-mode=gliner with no model path fails startup")
    void missingModelFailsStartup() {
        MemoryProperties props = new MemoryProperties()
                .setDimensions(4)
                .setWorkingCapacity(8)
                .setEpisodicPartitionCapacity(8)
                .setSemanticCapacity(8)
                .setProceduralCapacity(8);
        props.getEntity().setExtractionMode("gliner");
        assertThatThrownBy(() -> DefaultSpectorMemory.builder(props)
                .embeddingProvider(new FixedEmbedder())
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build())
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("model-path")
                .extracting(ex -> ((SpectorConfigException) ex).errorCode())
                .isEqualTo(ErrorCode.EXTRACTION_MODEL_INVALID);
    }

    private static final class FixedEmbedder implements EmbeddingProvider {
        @Override
        public EmbeddingResult embed(String text) {
            return new EmbeddingResult(new float[] {1f, 0f, 0f, 0f}, 1, "fixed");
        }

        @Override
        public int dimensions() {
            return 4;
        }

        @Override
        public String modelName() {
            return "fixed";
        }
    }
}
