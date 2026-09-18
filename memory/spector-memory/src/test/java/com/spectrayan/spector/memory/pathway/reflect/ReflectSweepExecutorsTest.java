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
package com.spectrayan.spector.memory.pathway.reflect;

import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutor;
import com.spectrayan.spector.memory.pathway.reflect.spi.ReflectSweepExecutors;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.NoopBackpressurePolicy;
import com.spectrayan.spector.memory.pathway.reflect.spi.local.TokenBucketBackpressurePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReflectSweepExecutors: Discovery & Configuration Tests")
class ReflectSweepExecutorsTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(ReflectSweepExecutors.ORCHESTRATOR_PROPERTY);
        ReflectSweepExecutors.reset();
    }

    @Test
    @DisplayName("Discovers InProcessReflectSweepExecutor as default primary SPI provider")
    void testDefaultDiscovery() {
        ReflectSweepExecutors.reset();
        ReflectSweepExecutor primary = ReflectSweepExecutors.getPrimary();
        assertThat(primary).isNotNull();
        assertThat(primary.name()).isEqualTo("in-process");
        assertThat(primary.priority()).isEqualTo(0);
        assertThat(primary.available()).isTrue();
    }

    @Test
    @DisplayName("ReflectSweepSpec fullCycle produces full biological reflection spec")
    void testFullCycleSpec() {
        ReflectSweepSpec spec = ReflectSweepSpec.fullCycle();
        assertThat(spec.sweepId()).isEqualTo("full-cycle");
        assertThat(spec.sessionLimit()).isZero();
        assertThat(spec.runCompanionRelays()).isTrue();
        assertThat(spec.backpressure()).isSameAs(NoopBackpressurePolicy.INSTANCE);
    }

    @Test
    @DisplayName("ReflectSweepSpec consolidationOnly produces bounded partial tick spec")
    void testConsolidationOnlySpec() {
        ReflectSweepSpec spec = ReflectSweepSpec.consolidationOnly(15);
        assertThat(spec.sweepId()).isEqualTo("consolidation-tick");
        assertThat(spec.sessionLimit()).isEqualTo(15);
        assertThat(spec.runCompanionRelays()).isFalse();
    }

    @Test
    @DisplayName("TokenBucketBackpressurePolicy tracks failures and triggers abort")
    void testTokenBucketBackpressure() {
        TokenBucketBackpressurePolicy policy = new TokenBucketBackpressurePolicy(6000, 10, 2);
        assertThat(policy.shouldAbortSweep()).isFalse();

        policy.onProviderFailure(new RuntimeException("Simulated 429 Too Many Requests"));
        assertThat(policy.shouldAbortSweep()).isFalse();

        policy.onProviderFailure(new RuntimeException("Simulated 429 Too Many Requests"));
        assertThat(policy.shouldAbortSweep()).isTrue();
    }

    @Test
    @DisplayName("Builder configures custom timeout and filters")
    void testSpecBuilder() {
        ReflectSweepSpec custom = ReflectSweepSpec.builder()
                .sweepId("custom-batch")
                .sessionLimit(5)
                .timeBudget(Duration.ofSeconds(30))
                .runCompanionRelays(false)
                .build();

        assertThat(custom.sweepId()).isEqualTo("custom-batch");
        assertThat(custom.sessionLimit()).isEqualTo(5);
        assertThat(custom.timeBudget()).isEqualTo(Duration.ofSeconds(30));
        assertThat(custom.runCompanionRelays()).isFalse();
    }

    @Test
    @DisplayName("getExecutor resolves executor by name or falls back to primary")
    void testGetExecutorByName() {
        ReflectSweepExecutors.reset();
        ReflectSweepExecutor byName = ReflectSweepExecutors.getExecutor("in-process");
        assertThat(byName).isNotNull();
        assertThat(byName.name()).isEqualTo("in-process");

        // Null and blank fall back to primary
        assertThat(ReflectSweepExecutors.getExecutor(null)).isSameAs(ReflectSweepExecutors.getPrimary());
        assertThat(ReflectSweepExecutors.getExecutor("")).isSameAs(ReflectSweepExecutors.getPrimary());
        assertThat(ReflectSweepExecutors.getExecutor("   ")).isSameAs(ReflectSweepExecutors.getPrimary());

        // Unknown name falls back to primary with a warning
        assertThat(ReflectSweepExecutors.getExecutor("unknown-orchestrator")).isSameAs(ReflectSweepExecutors.getPrimary());
    }
}
