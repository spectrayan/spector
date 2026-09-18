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
package com.spectrayan.spector.memory.aisme.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityEvaluator;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ConsciousnessContinuityRelay}.
 */
class ConsciousnessContinuityRelayTest {

    @Test
    void relayName_isConsciousnessContinuity() {
        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(null, null);
        assertThat(relay.relayName()).isEqualTo("consciousness-continuity");
    }

    @Test
    void unconfigured_isPassThrough() {
        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(null, null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        CognitiveResult item = createResult("m1", 0.5f);
        signal.candidates().add(item);

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isEqualTo(0.5f);
    }

    @Test
    void configured_modulatesCandidateScores() {
        ConsciousnessContinuityEvaluator evaluator = new ConsciousnessContinuityEvaluator(null);
        Map<String, float[]> vectors = new HashMap<>();
        vectors.put("m1", new float[]{1.0f, 0.0f});
        vectors.put("m2", new float[]{0.9f, 0.1f});

        ConsciousnessContinuityRelay relay = new ConsciousnessContinuityRelay(evaluator, vectors::get, 0.5f);

        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());
        signal.candidates().add(createResult("m1", 0.5f));
        signal.candidates().add(createResult("m2", 0.5f));

        boolean ok = relay.transmit(signal);
        assertThat(ok).isTrue();
        assertThat(signal.candidates().get(0).score()).isGreaterThanOrEqualTo(0.5f);
    }

    private static CognitiveResult createResult(String id, float score) {
        return new CognitiveResult(
                id,
                "text " + id,
                score,
                1.0f,
                0.1f,
                0,
                (byte) 0,
                MemoryType.EPISODIC,
                MemorySource.USER_STATED,
                new String[0],
                1.0f,
                1.0f,
                CognitiveResult.RetrievalMode.STANDARD,
                null,
                null,
                null,
                Map.of()
        );
    }
}
