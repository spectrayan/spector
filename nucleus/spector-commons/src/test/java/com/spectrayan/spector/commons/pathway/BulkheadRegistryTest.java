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

@DisplayName("BulkheadConfig and BulkheadRegistry")
class BulkheadRegistryTest {

    @Test
    @DisplayName("BulkheadConfig rejects invalid permits or null arguments")
    void configValidation() {
        assertThatThrownBy(() -> BulkheadConfig.of(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> BulkheadConfig.of(5, null, OnReject.FAIL))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> BulkheadConfig.of(5, Duration.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("BulkheadRegistry returns same semaphore for same name")
    void registryReturnsSameSemaphore() {
        var registry = BulkheadRegistry.create();
        var cfg1 = BulkheadConfig.of(4, Duration.ofMillis(50), OnReject.BYPASS);
        var sem1 = registry.get("test-bulkhead", cfg1);

        assertThat(sem1.availablePermits()).isEqualTo(4);
        assertThat(registry.configFor("test-bulkhead")).isEqualTo(cfg1);
        assertThat(registry.size()).isEqualTo(1);

        var cfg2 = BulkheadConfig.of(8);
        var sem2 = registry.get("test-bulkhead", cfg2);
        assertThat(sem2).isSameAs(sem1);
    }
}
