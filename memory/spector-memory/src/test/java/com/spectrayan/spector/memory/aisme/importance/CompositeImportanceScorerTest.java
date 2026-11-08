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
package com.spectrayan.spector.memory.aisme.importance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.config.properties.AismeProperties;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit tests for {@link CompositeImportanceScorer}.
 */
class CompositeImportanceScorerTest {

    private AismeProperties config;
    private CompositeImportanceScorer scorer;

    @BeforeEach
    void setUp() {
        config = AismeProperties.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceWeightSurprise(0.20f)
                .importanceWeightAffect(0.20f)
                .importanceWeightGoal(0.20f)
                .importanceWeightSocial(0.20f)
                .importanceWeightNovelty(0.20f)
                .importanceFlashbulbThreshold(0.85f)
                .build();
        scorer = new CompositeImportanceScorer(config);
    }

    @Test
    void evaluate_balancedProfile_computesArithmeticMean() {
        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
        float score = scorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isCloseTo(0.50f, within(1e-5f));
        assertThat(scorer.isFlashbulb(score)).isFalse();
    }

    @Test
    void evaluate_highSalience_triggersFlashbulb() {
        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.9f, 0.95f, 0.85f, 0.90f, 0.80f);
        float score = scorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isGreaterThanOrEqualTo(0.85f);
        assertThat(scorer.isFlashbulb(score)).isTrue();
    }

    @Test
    void evaluate_profileAdaptation_altersWeights() {
        // High affect & social, low surprise & novelty
        CompositeImportanceSignals emotionalSignal = new CompositeImportanceSignals(0.1f, 0.9f, 0.2f, 0.8f, 0.1f);

        float balancedScore = scorer.evaluate(emotionalSignal, CognitiveProfile.BALANCED);
        float sensitiveScore = scorer.evaluate(emotionalSignal, CognitiveProfile.HIGHLY_SENSITIVE);

        // HIGHLY_SENSITIVE amplifies affect and social context
        assertThat(sensitiveScore).isGreaterThan(balancedScore);
    }

    @Test
    void extractSignals_fromRememberSignal_extractsAllDimensions() {
        float[] queryVec = new float[]{1.0f, 0.0f, 0.0f, 0.0f};
        float[] goalVec = new float[]{0.9f, 0.1f, 0.0f, 0.0f};
        scorer.setGoalEmbeddings(List.of(goalVec));

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Critical outage occurred during conversation with @lead: user:alice said error panic",
                queryVec,
                MemoryType.EPISODIC,
                new String[]{"user:alice", "outage"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.eventDensityMetrics(new EventDensityMetrics(1.5f, 0.8f, 0.85f, 0.90f, true, 25.0f));

        CompositeImportanceSignals extracted = scorer.extractSignals(signal);

        assertThat(extracted.surprise()).isEqualTo(0.85f);
        assertThat(extracted.affect()).isGreaterThanOrEqualTo(0.85f);
        assertThat(extracted.socialContext()).isGreaterThanOrEqualTo(0.70f);
        assertThat(extracted.goalRelevance()).isGreaterThanOrEqualTo(0.75f);
        assertThat(extracted.novelty()).isGreaterThanOrEqualTo(0.80f);

        float score = scorer.evaluateSignal(signal, CognitiveProfile.DEBUGGING);
        assertThat(score).isGreaterThanOrEqualTo(0.80f);
    }

    @Test
    void evaluate_disabledConfig_returnsDefault() {
        AismeProperties disabledConfig = AismeProperties.builder()
                .enabled(true)
                .enableImportance(false)
                .build();
        CompositeImportanceScorer disabledScorer = new CompositeImportanceScorer(disabledConfig);

        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.9f, 0.9f, 0.9f, 0.9f, 0.9f);
        float score = disabledScorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isEqualTo(0.5f);
    }

    @Test
    void extractSignals_nearMissSubstrings_doNotTriggerKeywordBoosts() {
        RememberSignal nearMiss = RememberSignal.forCognitive(
                "mem-near-miss",
                "debug the planet explanation with the committee wearing a glove failsafe",
                new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"routine"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );

        CompositeImportanceSignals nearMissSignals = scorer.extractSignals(nearMiss);
        assertThat(nearMissSignals.affect()).isEqualTo(0.0f);
        assertThat(nearMissSignals.goalRelevance()).isEqualTo(0.0f);
        assertThat(nearMissSignals.socialContext()).isEqualTo(0.0f);

        RememberSignal exactMatch = RememberSignal.forCognitive(
                "mem-exact-match",
                "the outage is critical; I commit to the plan",
                new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"incident"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );

        CompositeImportanceSignals exactSignals = scorer.extractSignals(exactMatch);
        assertThat(exactSignals.affect()).isEqualTo(0.95f);
        assertThat(exactSignals.goalRelevance()).isEqualTo(0.90f);
        assertThat(exactSignals.socialContext()).isEqualTo(0.90f);
    }

    @Test
    void extractSignals_turkishDefaultLocale_producesIdenticalScores() {
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-tr",
                "THE OUTAGE IS CRITICAL AND I COMMIT TO THIS MILESTONE",
                new float[]{0.2f, 0.2f, 0.2f, 0.2f},
                MemoryType.EPISODIC,
                new String[]{"incident"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            CompositeImportanceSignals usSignals = scorer.extractSignals(signal);
            float usScore = scorer.evaluateSignal(signal, CognitiveProfile.BALANCED);

            Locale.setDefault(Locale.forLanguageTag("tr"));
            CompositeImportanceSignals trSignals = scorer.extractSignals(signal);
            float trScore = scorer.evaluateSignal(signal, CognitiveProfile.BALANCED);

            assertThat(trSignals).isEqualTo(usSignals);
            assertThat(trScore).isEqualTo(usScore);
            assertThat(trSignals.affect()).isEqualTo(0.95f);
            assertThat(trSignals.goalRelevance()).isEqualTo(0.90f);
            assertThat(trSignals.socialContext()).isEqualTo(0.90f);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void extractSignals_prefersAppraisedHeaderAffectForNonEnglishMemory() {
        RememberSignal nonEnglish = RememberSignal.forCognitive(
                "mem-non-en",
                "本番環境で重大な障害が発生しました",
                new float[]{0.2f, 0.2f, 0.2f, 0.2f},
                MemoryType.EPISODIC,
                new String[]{"intent:goal"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        nonEnglish.header(new EncodingHeader(
                1_000L, 0L, 1.0f, 0.5f, 0, (short) 0,
                (byte) -115, (byte) 0, (byte) 230, 1.0f));

        CompositeImportanceSignals signals = scorer.extractSignals(nonEnglish);
        assertThat(signals.affect()).isGreaterThanOrEqualTo(0.85f);
        assertThat(signals.goalRelevance()).isGreaterThanOrEqualTo(0.85f);
    }

    @Test
    void extractSignals_configurableAndPerLocaleKeywordLists_overrideDefaults() {
        AismeProperties customConfig = AismeProperties.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceAffectHighKeywords(List.of("sev0"))
                .importanceAffectMediumKeywords(List.of("regression"))
                .importanceGoalHighKeywords(List.of("sev0"))
                .importanceGoalMediumKeywords(List.of("deliverable"))
                .importanceSocialKeywords(List.of("sla contract"))
                .importanceLocaleAffectHighKeywords(Map.of("es", List.of("emergencia")))
                .importanceGoalMediumKeywords("es", List.of("objetivo"))
                .build();
        CompositeImportanceScorer customScorer = new CompositeImportanceScorer(customConfig);

        RememberSignal oldDefaultWords = RememberSignal.forCognitive(
                "mem-old-defaults",
                "critical panic plan agreement",
                new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"general"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        CompositeImportanceSignals oldSignals = customScorer.extractSignals(oldDefaultWords);
        assertThat(oldSignals.affect()).isEqualTo(0.0f);
        assertThat(oldSignals.goalRelevance()).isEqualTo(0.0f);
        assertThat(oldSignals.socialContext()).isEqualTo(0.0f);

        RememberSignal customWords = RememberSignal.forCognitive(
                "mem-custom",
                "We have a sev0 deliverable under our SLA contract",
                new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"general"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        CompositeImportanceSignals customSignals = customScorer.extractSignals(customWords);
        assertThat(customSignals.affect()).isEqualTo(0.95f);
        assertThat(customSignals.goalRelevance()).isEqualTo(0.90f);
        assertThat(customSignals.socialContext()).isEqualTo(0.90f);

        RememberSignal spanishMemory = RememberSignal.forCognitive(
                "mem-es",
                "Esta emergencia afecta nuestro objetivo principal",
                new float[]{0.1f, 0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"lang:es"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        CompositeImportanceSignals esSignals = customScorer.extractSignals(spanishMemory);
        assertThat(esSignals.affect()).isEqualTo(0.95f);
        assertThat(esSignals.goalRelevance()).isEqualTo(0.85f);
    }
}
