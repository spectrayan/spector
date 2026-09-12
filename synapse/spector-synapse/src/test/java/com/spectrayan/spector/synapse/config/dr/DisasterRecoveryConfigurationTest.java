/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
