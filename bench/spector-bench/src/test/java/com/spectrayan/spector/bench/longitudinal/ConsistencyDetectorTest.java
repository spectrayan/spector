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
package com.spectrayan.spector.bench.longitudinal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsistencyDetector Unit Tests")
class ConsistencyDetectorTest {

    private final ConsistencyDetector detector = new ConsistencyDetector();

    @Test
    @DisplayName("evaluatePreferenceStability returns 1.0 when all preferences are matched")
    void testEvaluatePreferenceStabilityFullMatch() {
        Map<String, String> prefs = Map.of("database", "PostgreSQL", "framework", "Spring Boot 3.2");
        List<String> content = List.of("We use PostgreSQL for database and Spring Boot 3.2 framework.");

        double score = detector.evaluatePreferenceStability(prefs, content);

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("evaluatePreferenceStability returns partial score when some preferences are missing")
    void testEvaluatePreferenceStabilityPartialMatch() {
        Map<String, String> prefs = Map.of("database", "PostgreSQL", "framework", "Spring Boot 3.2");
        List<String> content = List.of("We use PostgreSQL for storage.");

        double score = detector.evaluatePreferenceStability(prefs, content);

        assertThat(score).isEqualTo(0.5);
    }

    @Test
    @DisplayName("evaluateErrorNonRepetition returns 1.0 when no negative constraints are violated")
    void testEvaluateErrorNonRepetitionNoViolations() {
        List<String> negativeConstraints = List.of("MySQL", "MongoDB");
        List<String> content = List.of("Database selected: PostgreSQL");

        double score = detector.evaluateErrorNonRepetition(negativeConstraints, content);

        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("evaluateErrorNonRepetition returns reduced score when negative constraints are violated")
    void testEvaluateErrorNonRepetitionWithViolation() {
        List<String> negativeConstraints = List.of("MySQL", "MongoDB");
        List<String> content = List.of("We switched to MySQL for auth service");

        double score = detector.evaluateErrorNonRepetition(negativeConstraints, content);

        assertThat(score).isEqualTo(0.5);
    }
}
