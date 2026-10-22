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
package com.spectrayan.spector.memory.affect;

import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.properties.AffectProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractionResult;
import com.spectrayan.spector.memory.pathway.pipeline.TagExtractor;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.onnx.OnnxProviderFactory;
import com.spectrayan.spector.provider.ProviderConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnchorAffectAppraiserTest {

    private static final String POLES = """
            positive:
              - joy
            negative:
              - grief
            high:
              - alarm
            low:
              - calm
            """;

    @TempDir
    Path tmp;

    @Test
    void valenceFollowsThePoleDifferenceAndDeadbandZeroesATie() throws Exception {
        Path anchors = Files.writeString(tmp.resolve("anchors.yaml"), POLES);
        FixedEmbedder embedder = axes();
        AffectAppraiser appraiser = appraiser(anchors, embedder, 0.05f, 4.0f);

        byte positive = appraiser.appraise(embedder.embed("joy").vector()).valence();
        byte negative = appraiser.appraise(embedder.embed("grief").vector()).valence();
        byte tied = appraiser.appraise(new float[] {1f, 1f, 0f, 0f}).valence();

        assertThat(positive).isGreaterThan((byte) 0);
        assertThat(negative).isLessThan((byte) 0);
        assertThat(tied).isEqualTo((byte) 0);
        assertThat(positive).isEqualTo((byte) Math.round(127.0 * Math.tanh(4.0)));
    }

    @Test
    void arousalRisesTowardTheHighPole() throws Exception {
        Path anchors = Files.writeString(tmp.resolve("anchors.yaml"), POLES);
        AffectAppraiser appraiser = appraiser(anchors, axes(), 0.05f, 4.0f);

        int high = Byte.toUnsignedInt(appraiser.appraise(new float[] {0f, 0f, 1f, 0f}).arousal());
        int low = Byte.toUnsignedInt(appraiser.appraise(new float[] {0f, 0f, 0f, 1f}).arousal());

        assertThat(high).isGreaterThan(200);
        assertThat(low).isLessThan(55);
        assertThat(high).isEqualTo((int) Math.round(127.5 + 127.5 * Math.tanh(4.0)));
    }

    @Test
    void missingFileMissingPoleAndDimensionMismatchFail() throws Exception {
        FixedEmbedder embedder = axes();
        AffectProperties missing = settings(tmp.resolve("absent.yaml"), 0.05f, 4.0f);
        assertThatThrownBy(() -> new AnchorAffectAppraiser(missing, embedder))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("does not exist");

        Path noHigh = Files.writeString(tmp.resolve("no-high.yaml"), """
                positive:
                  - joy
                negative:
                  - grief
                low:
                  - calm
                """);
        assertThatThrownBy(() -> new AnchorAffectAppraiser(settings(noHigh, 0.05f, 4.0f), embedder))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("high");

        FixedEmbedder mismatch = axes();
        mismatch.override = Map.of("alarm", new float[] {1f, 0f});
        Path anchors = Files.writeString(tmp.resolve("anchors.yaml"), POLES);
        assertThatThrownBy(() -> new AnchorAffectAppraiser(settings(anchors, 0.05f, 4.0f), mismatch))
                .isInstanceOf(SpectorConfigException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void callerAndLlmAffectAreKept() throws Exception {
        Path anchors = Files.writeString(tmp.resolve("anchors.yaml"), POLES);
        FixedEmbedder embedder = axes();
        embedder.vectors.put("a sad story", embedder.vectors.get("grief"));

        MemoryProperties props = new MemoryProperties()
                .setDimensions(4)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(50)
                .setProceduralCapacity(20);
        props.getAffect().setAppraiser(AffectProperties.Mode.SOUL_ANCHORS)
                .setAnchorsPath(anchors.toString())
                .setDeadband(0.05f)
                .setGain(4.0f);

        try (SpectorMemory memory = DefaultSpectorMemory.builder(props)
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            memory.remember("caller", "a sad story", MemoryType.SEMANTIC, MemorySource.OBSERVED,
                    new RememberHints(0f, 0f, 0f, (byte) 40, (byte) 10));
            assertThat(storedValence(memory, "caller")).isEqualTo((byte) 40);

            memory.remember("anchors", "a sad story", MemoryType.SEMANTIC);
            assertThat(storedValence(memory, "anchors")).isLessThan((byte) 0);
        }

        TagExtractor llm = new TagExtractor() {
            @Override public String[] extract(String id, String text) { return new String[0]; }
            @Override public boolean appraises() { return true; }
            @Override public TagExtractionResult extractWithContext(String id, String text) {
                return new TagExtractionResult(new String[0], (byte) 20, (byte) 30);
            }
        };
        try (SpectorMemory memory = DefaultSpectorMemory.builder(props)
                .embeddingProvider(embedder)
                .tagExtractor(llm)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build()) {
            memory.remember("llm", "a sad story", MemoryType.SEMANTIC);
            assertThat(storedValence(memory, "llm")).isEqualTo((byte) 20);
        }
    }

    @Test
    void bundledMiniLmAgreesOnSign() {
        EmbeddingProvider miniLm;
        try {
            miniLm = new OnnxProviderFactory()
                    .createEmbeddingProvider(new ProviderConfig("onnx", "onnx", "", "", "", 0, Map.of()))
                    .orElseThrow();
        } catch (RuntimeException e) {
            org.junit.jupiter.api.Assumptions.abort("bundled MiniLM unavailable: " + e.getMessage());
            return;
        }
        AffectProperties affect = new AffectProperties()
                .setAppraiser(AffectProperties.Mode.SOUL_ANCHORS)
                .setGain(4.0f)
                .setDeadband(0.02f);
        AffectAppraiser appraiser = new AnchorAffectAppraiser(affect, miniLm);
        byte thrilled = appraiser.appraise(miniLm.embed("I'm thrilled about the launch").vector()).valence();
        byte outage = appraiser.appraise(miniLm.embed("the outage wiped the database").vector()).valence();
        assertThat(thrilled).isGreaterThan((byte) 0);
        assertThat(outage).isLessThan((byte) 0);
    }

    private static AffectAppraiser appraiser(Path anchors, FixedEmbedder embedder, float deadband, float gain) {
        return new AnchorAffectAppraiser(settings(anchors, deadband, gain), embedder);
    }

    private static AffectProperties settings(Path anchors, float deadband, float gain) {
        return new AffectProperties()
                .setAppraiser(AffectProperties.Mode.SOUL_ANCHORS)
                .setAnchorsPath(anchors.toString())
                .setDeadband(deadband)
                .setGain(gain);
    }

    private static byte storedValence(SpectorMemory memory, String id) {
        DefaultSpectorMemory dm = (DefaultSpectorMemory) memory;
        var loc = dm.index().locate(id);
        return dm.partitionManager().routerFor(loc.colocatedPartition()).readHeader(loc).valence();
    }

    private static FixedEmbedder axes() {
        FixedEmbedder embedder = new FixedEmbedder();
        embedder.vectors.put("joy", new float[] {1f, 0f, 0f, 0f});
        embedder.vectors.put("grief", new float[] {0f, 1f, 0f, 0f});
        embedder.vectors.put("alarm", new float[] {0f, 0f, 1f, 0f});
        embedder.vectors.put("calm", new float[] {0f, 0f, 0f, 1f});
        return embedder;
    }

    private static final class FixedEmbedder implements EmbeddingProvider {
        final Map<String, float[]> vectors = new java.util.HashMap<>();
        Map<String, float[]> override = Map.of();

        @Override public EmbeddingResult embed(String text) {
            float[] vector = override.containsKey(text) ? override.get(text) : vectors.get(text);
            if (vector == null) {
                throw new IllegalStateException(text);
            }
            return new EmbeddingResult(vector, 1, "fixed");
        }

        @Override public int dimensions() { return 4; }
        @Override public String modelName() { return "fixed"; }
    }
}
