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
package com.spectrayan.spector.commons.pathway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RetryPolicy Configuration and Rules")
class RetryPolicyTest {

    @Test
    @DisplayName("none() produces a single attempt policy with no backoff")
    void nonePolicy() {
        var policy = RetryPolicy.none();
        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.initialBackoff()).isEqualTo(Duration.ZERO);
        assertThat(policy.shouldRetry(FaultKind.TRANSIENT)).isFalse();
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Creates retry policy and computes exponential backoff")
    void ofWithExponentialBackoff() {
        var policy = RetryPolicy.of(3, Duration.ofMillis(100), 0.0, FaultKind.TRANSIENT);
        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.initialBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.shouldRetry(FaultKind.TRANSIENT)).isTrue();
        assertThat(policy.shouldRetry(FaultKind.CONTRACT)).isFalse();

        // attempt 1 has no backoff
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ZERO);
        // attempt 2 has initial backoff (100ms)
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofMillis(100));
        // attempt 3 has 2x initial backoff (200ms)
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("Rejects never-retry fault kinds like VALIDATION, CONTRACT, CONTROL, INTERNAL")
    void rejectsNeverRetryKinds() {
        assertThatThrownBy(() -> RetryPolicy.of(3, Duration.ofMillis(50), FaultKind.CONTRACT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot retry on CONTRACT");

        assertThatThrownBy(() -> RetryPolicy.of(3, Duration.ofMillis(50), FaultKind.VALIDATION))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RetryPolicy.of(3, Duration.ofMillis(50), FaultKind.INTERNAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects invalid maxAttempts or jitter")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> RetryPolicy.of(0, Duration.ofMillis(10), FaultKind.TRANSIENT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> RetryPolicy.of(2, Duration.ofMillis(10), 1.5, FaultKind.TRANSIENT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
