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
package com.spectrayan.spector.memory.aisme.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.model.AgentSoul;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NarrativeSelfEngine}.
 */
class NarrativeSelfEngineTest {

    @Test
    void deriveNarrativePrior_blendsIdentityAndContext() {
        float[] idEmb = {1.0f, 0.0f};
        AgentSoul soul = AgentSoul.builder()
                .id("test-soul")
                .name("Narrator")
                .purposeEmbedding(idEmb)
                .build();

        NarrativeSelfEngine engine = new NarrativeSelfEngine(soul, 2);

        float[] context = {0.0f, 1.0f};
        float[] prior = engine.deriveNarrativePrior(context, 0.5f);

        // 0.5 * [1, 0] + 0.5 * [0, 1] = [0.5, 0.5]
        assertThat(prior[0]).isEqualTo(0.5f);
        assertThat(prior[1]).isEqualTo(0.5f);
    }

    @Test
    void evaluateAlignment_alignedMemory_highScore() {
        NarrativeSelfEngine engine = new NarrativeSelfEngine(null, 2);
        float[] narrativePrior = {1.0f, 0.0f};
        float[] alignedMemory = {1.0f, 0.0f};
        float[] orthogonalMemory = {0.0f, 1.0f};

        float alignedScore = engine.evaluateAlignment(alignedMemory, narrativePrior);
        float orthoScore = engine.evaluateAlignment(orthogonalMemory, narrativePrior);

        assertThat(alignedScore).isEqualTo(1.0f);
        assertThat(orthoScore).isEqualTo(0.5f);
    }
}
