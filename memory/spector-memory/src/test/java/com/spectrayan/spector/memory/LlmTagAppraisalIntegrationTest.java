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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for {@code spector.memory.tag-extractor} (#1137): the configured mode reaches the
 * remember path, and the LLM's valence and arousal reach the stored memory unless the caller set them.
 */
class LlmTagAppraisalIntegrationTest {

    private static final int DIMENSIONS = 32;
    private static final String TEXT = "Any movement on the Naperville docs? I'm tired of waiting on this assistant.";
    /** Reply in the format {@code prompts/tag-extraction.txt} asks for. */
    private static final String APPRAISAL = "/eldercare\n/paperwork\n/frustration\n/valence:-40\n/arousal:120";

    private SpectorMemory memory;

    @AfterEach
    void tearDown() {
        if (memory != null) {
            memory.close();
        }
    }

    @Test
    void llmModeStoresAppraisedTagsValenceAndArousal() {
        var llm = new ScriptedLlm(APPRAISAL);
        memory = build(TagExtractorMode.LLM, llm);

        memory.remember("mem-1", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED);

        CognitiveRecord stored = memory.inspect("mem-1");
        assertThat(stored.valence()).isEqualTo((byte) -40);
        assertThat(Byte.toUnsignedInt(stored.arousal())).isEqualTo(120);
        assertThat(stored.tags()).contains("eldercare", "frustration");
        assertThat(llm.tagCalls.get()).isEqualTo(1);
    }

    @Test
    void llmAppraisalAlsoReachesTheContextPath() {
        memory = build(TagExtractorMode.LLM, new ScriptedLlm(APPRAISAL));

        memory.remember("mem-ctx", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED,
                RememberContext.builder().overrideTimestampMs(1_781_773_980_000L).build());

        CognitiveRecord stored = memory.inspect("mem-ctx");
        assertThat(stored.valence()).isEqualTo((byte) -40);
        assertThat(Byte.toUnsignedInt(stored.arousal())).isEqualTo(120);
    }

    @Test
    void callerSuppliedAffectWins() {
        memory = build(TagExtractorMode.LLM, new ScriptedLlm(APPRAISAL));

        memory.remember("mem-2", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED,
                new RememberHints(0.5f, 0f, 0f, (byte) 30, (byte) 50));

        CognitiveRecord stored = memory.inspect("mem-2");
        assertThat(stored.valence()).isEqualTo((byte) 30);
        assertThat(Byte.toUnsignedInt(stored.arousal())).isEqualTo(50);
        assertThat(stored.tags()).contains("eldercare");
    }

    @Test
    void llmFailureRefusesTheWriteInsteadOfFallingBack() {
        var llm = new ScriptedLlm(APPRAISAL);
        llm.failing.set(true);
        memory = build(TagExtractorMode.LLM, llm);

        // No-fallback rule: storing keyword tags and neutral affect would hide a broken LLM configuration.
        assertThatThrownBy(() -> memory.remember("mem-3", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED))
                .isInstanceOfSatisfying(SpectorMemoryException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.MEMORY_LLM_APPRAISAL_FAILED));
        assertThat(memory.inspect("mem-3")).as("nothing is stored when the appraisal fails").isNull();
    }

    @Test
    void llmModeWithoutAProviderFailsAtBuildTime() {
        assertThatThrownBy(() -> build(TagExtractorMode.LLM, null))
                .isInstanceOfSatisfying(SpectorConfigException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE));
    }

    @Test
    void contentModeNeverCallsTheLlm() {
        var llm = new ScriptedLlm(APPRAISAL);
        memory = build(TagExtractorMode.CONTENT, llm);

        memory.remember("mem-4", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED);

        assertThat(llm.tagCalls.get()).isZero();
        assertThat(memory.inspect("mem-4").valence()).isZero();
    }

    @Test
    void noneModeStoresNoExtractedTags() {
        var llm = new ScriptedLlm(APPRAISAL);
        memory = build(TagExtractorMode.NONE, llm);

        memory.remember("mem-5", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED);

        assertThat(llm.tagCalls.get()).isZero();
        assertThat(memory.inspect("mem-5").tags()).doesNotContain("naperville", "eldercare");
    }

    @Test
    void recallDoesNotCallTheLlmForQueryTags() {
        var llm = new ScriptedLlm(APPRAISAL);
        memory = build(TagExtractorMode.LLM, llm);
        memory.remember("mem-6", TEXT, MemoryType.EPISODIC, MemorySource.USER_STATED);
        int afterIngest = llm.tagCalls.get();

        memory.recall("Naperville docs");

        assertThat(llm.tagCalls.get()).isEqualTo(afterIngest);
    }

    private static SpectorMemory build(TagExtractorMode mode, LlmProvider llm) {
        MemoryProperties props = new MemoryProperties()
                .setDimensions(DIMENSIONS)
                .setWorkingCapacity(20)
                .setEpisodicPartitionCapacity(100)
                .setSemanticCapacity(100)
                .setProceduralCapacity(100)
                .setTagExtractor(mode);
        return DefaultSpectorMemory.builder(props)
                .embeddingProvider(new SpectorMemoryIntegrationTest.MockEmbeddingProvider(DIMENSIONS))
                .llmProvider(llm)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .build();
    }

    /** Answers the tag-extraction prompt with a scripted appraisal and counts those calls. */
    static final class ScriptedLlm implements LlmProvider {
        final AtomicInteger tagCalls = new AtomicInteger();
        final AtomicBoolean failing = new AtomicBoolean();
        private final String appraisal;

        ScriptedLlm(String appraisal) {
            this.appraisal = appraisal;
        }

        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            String prompt = request.messages().isEmpty() ? "" : request.messages().get(0).text();
            boolean tagPrompt = prompt.startsWith("Extract 10-15 topic tags");
            if (tagPrompt) {
                tagCalls.incrementAndGet();
                if (failing.get()) {
                    throw new IllegalStateException("provider down");
                }
                return new LlmResponse(appraisal, 10, 10, "scripted");
            }
            return new LlmResponse("NONE", 10, 10, "scripted");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "scripted";
        }
    }
}
