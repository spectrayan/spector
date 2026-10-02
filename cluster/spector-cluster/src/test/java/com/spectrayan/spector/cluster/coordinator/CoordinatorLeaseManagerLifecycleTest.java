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
package com.spectrayan.spector.cluster.coordinator;

import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatorLeaseManagerLifecycleTest {

    @Test
    @DisplayName("Lifecycle: Clean shutdown awaits scheduler termination and is idempotent")
    void testCleanShutdownAwaitsSchedulerTermination() {
        ControlStore store = new InMemoryControlStore();
        CoordinatorLeaseManager manager = new CoordinatorLeaseManager(
                store, "node-lifecycle-1", Duration.ofSeconds(10), Duration.ofMillis(50)
        );

        assertThat(manager.isTerminated()).isFalse();

        manager.start();
        assertThat(manager.isTerminated()).isFalse();

        // Close awaits termination
        manager.close();
        assertThat(manager.isTerminated()).isTrue();

        // Idempotent secondary close
        manager.close();
        assertThat(manager.isTerminated()).isTrue();
    }

    @Test
    @DisplayName("Lifecycle: Starting an already closed lease manager throws IllegalStateException")
    void testStartAfterCloseThrowsIllegalStateException() {
        ControlStore store = new InMemoryControlStore();
        CoordinatorLeaseManager manager = new CoordinatorLeaseManager(
                store, "node-lifecycle-2", Duration.ofSeconds(10), Duration.ofSeconds(5)
        );

        manager.close();
        assertThatThrownBy(manager::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been closed");
    }

    @Test
    @DisplayName("Lifecycle: Close without start cleanly terminates internal scheduler")
    void testCloseWithoutStartTerminatesScheduler() {
        ControlStore store = new InMemoryControlStore();
        CoordinatorLeaseManager manager = new CoordinatorLeaseManager(
                store, "node-lifecycle-3", Duration.ofSeconds(10), Duration.ofSeconds(5)
        );

        assertThat(manager.isTerminated()).isFalse();
        manager.close();
        assertThat(manager.isTerminated()).isTrue();
    }
}
