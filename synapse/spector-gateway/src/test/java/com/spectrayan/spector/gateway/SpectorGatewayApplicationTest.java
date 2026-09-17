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
package com.spectrayan.spector.gateway;

import com.spectrayan.spector.gateway.config.GatewayConfiguration;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import com.spectrayan.spector.gateway.filter.GatewayWebFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spector Gateway Application Context Tests (ADR-0081 §8 Phase 2)")
class SpectorGatewayApplicationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class, GatewayWebFilter.class)
            .withPropertyValues("spector.cell.role=gateway", "spector.cell.id=test-cell", "spector.cell.node-id=gw-1");

    @Test
    @DisplayName("Context loads successfully when spector.cell.role is gateway")
    void contextLoadsWithGatewayRole() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(GatewayWebFilter.class);
            assertThat(context).hasSingleBean(GatewayProperties.class);
        });
    }
}
