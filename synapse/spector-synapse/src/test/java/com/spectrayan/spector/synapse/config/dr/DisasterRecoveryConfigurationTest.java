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
package com.spectrayan.spector.synapse.config.dr;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryExporter;
import com.spectrayan.spector.synapse.dr.DisasterRecoveryRestorer;
import com.spectrayan.spector.synapse.dr.ObjectStoreClient;
import com.spectrayan.spector.synapse.dr.TenantErasureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DisasterRecoveryConfiguration Bean Presence Tests (G0)")
class DisasterRecoveryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    com.spectrayan.spector.synapse.config.CatalogAutoConfiguration.class,
                    DisasterRecoveryConfiguration.class,
                    SynapseProperties.class
            );

    @Test
    @DisplayName("Default properties produces local TenantErasureService but no cloud object store beans")
    void testDefaultPropertiesProducesNoCloudStoreBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenantErasureService.class);
            assertThat(context).doesNotHaveBean(ObjectStoreClient.class);
            assertThat(context).doesNotHaveBean(DisasterRecoveryExporter.class);
            assertThat(context).doesNotHaveBean(DisasterRecoveryRestorer.class);
        });
    }

    @Test
    @DisplayName("Configured object store endpoint produces all DR beans")
    void testConfiguredEndpointProducesAllBeans() {
        runner.withPropertyValues(
                "spector.dr.object-store-endpoint=http://localhost:9000",
                "spector.dr.object-store-bucket=spector-dr",
                "spector.dr.object-store-region=us-east-1",
                "spector.dr.export-enabled=true"
        ).run(context -> {
            assertThat(context).hasSingleBean(ObjectStoreClient.class);
            assertThat(context).hasSingleBean(DisasterRecoveryExporter.class);
            assertThat(context).hasSingleBean(DisasterRecoveryRestorer.class);
            assertThat(context).hasSingleBean(TenantErasureService.class);
        });
    }
}
