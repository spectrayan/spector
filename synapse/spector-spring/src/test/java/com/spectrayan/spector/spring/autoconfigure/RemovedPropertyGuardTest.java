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
package com.spectrayan.spector.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import com.spectrayan.spector.config.SpectorPropertyConstants;

/**
 * Verifies the Spring-side refusal of removed properties.
 *
 * <p>This guard exists because Spring does not use {@code SpectorConfigFactory}. It binds
 * {@code @ConfigurationProperties} directly onto the properties beans, and relaxed binding will
 * populate a field whose backing property has been withdrawn — so the standalone rejection alone would
 * leave Spring Boot users silently ignored.</p>
 */
@DisplayName("Removed Property Guard (Spring)")
class RemovedPropertyGuardTest {

    @Test
    @DisplayName("refuses an environment that sets the removed dimensions property")
    void refusesRemovedProperty() {
        var environment = new MockEnvironment()
                .withProperty(SpectorPropertyConstants.REMOVED_MEMORY_DIMENSIONS, "384");

        assertThatThrownBy(() -> RemovedPropertyGuard.check(environment))
                .isInstanceOf(SpectorConfigException.class)
                .satisfies(e -> assertThat(((SpectorConfigException) e).errorCode())
                        .isEqualTo(ErrorCode.CONFIG_VALUE_INVALID))
                .hasMessageContaining("spector.memory.dimensions")
                .hasMessageContaining("384")
                .hasMessageContaining(SpectorPropertyConstants.PROVIDER_EMBEDDING_DIMENSIONS);
    }

    @Test
    @DisplayName("accepts an environment that sets only the replacement")
    void acceptsReplacementProperty() {
        var environment = new MockEnvironment()
                .withProperty(SpectorPropertyConstants.PROVIDER_EMBEDDING_DIMENSIONS, "1024");

        assertThatCode(() -> RemovedPropertyGuard.check(environment)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts an empty environment and tolerates a null one")
    void toleratesAbsence() {
        assertThatCode(() -> RemovedPropertyGuard.check(new MockEnvironment())).doesNotThrowAnyException();
        assertThatCode(() -> RemovedPropertyGuard.check(null)).doesNotThrowAnyException();
    }
}
