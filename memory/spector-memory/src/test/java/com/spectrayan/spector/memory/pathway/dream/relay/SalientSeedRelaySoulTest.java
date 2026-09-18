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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.api.DreamMode;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.SalienceProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalientSeedRelaySoulTest {

    @Test
    void testPreExistingSeedsPreserved() {
        SalientSeedRelay relay = new SalientSeedRelay();
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f, 0.0f};

        DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .seedMemoryIds(List.of("seed-1"))
                .seedVectors(List.of(v1))
                .build();

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.seedMemoryIds()).containsExactly("seed-1");
        assertThat(signal.seedVectors()).hasSize(1);
    }

    @Test
    void testRelayName() {
        SalientSeedRelay relay = new SalientSeedRelay();
        assertThat(relay.relayName()).isEqualTo("salient_seed");
    }

    @Test
    void testSoulConditionedConfigurationPropagation() {
        float[] securityEmbedding = new float[]{1.0f, 0.0f, 0.0f, 0.0f};

        AgentSoul securityAuditor = new AgentSoul(
                "agent-auditor-1",
                "Security Sentinel",
                "Audits code and enforces zero vulnerabilities",
                "System prompt",
                "Enforce cybersecurity policies",
                "strict and vigilant",
                List.of("security", "cryptography"),
                List.of("Zero trust"),
                List.of("No unauthorized release"),
                AgentSoul.EmotionalBaseline.NEUTRAL,
                "analytical",
                "gpt-4",
                List.of(),
                securityEmbedding,
                securityEmbedding,
                (short) 1,
                Instant.now(),
                Instant.now()
        );

        SalienceProfile profile = SalienceProfile.builder()
                .interest("cybersecurity", InterestLevel.CRITICAL, securityEmbedding)
                .build();

        DreamSignal signal = DreamSignal.builder()
                .mode(DreamMode.REM)
                .primarySoul(securityAuditor)
                .salienceProfile(profile)
                .build();

        assertThat(signal.primarySoul()).isEqualTo(securityAuditor);
        assertThat(signal.salienceProfile()).isEqualTo(profile);
        assertThat(signal.primarySoul().identityEmbedding()).isEqualTo(securityEmbedding);
    }
}
