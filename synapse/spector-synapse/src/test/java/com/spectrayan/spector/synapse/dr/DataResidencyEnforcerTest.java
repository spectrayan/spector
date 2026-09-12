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
package com.spectrayan.spector.synapse.dr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Data Residency Enforcer Specification (ADR-0034 §16, Req R9.1–R9.5, V6, G31)")
class DataResidencyEnforcerTest {

    @Test
    @DisplayName("G31: Exact match is compatible")
    void testExactMatch() {
        assertThat(DataResidencyEnforcer.isCompatible("us-east-1", "us-east-1")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("eu-west-1", "eu-west-1")).isTrue();
    }

    @Test
    @DisplayName("G31: Broad continent geo prefix is compatible with child regions")
    void testBroadGeoContinentPrefix() {
        assertThat(DataResidencyEnforcer.isCompatible("eu", "eu-central-1")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("eu", "eu-west-1")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("us", "us-east-1")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("us", "us-west-2")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("ap", "ap-southeast-1")).isTrue();

        // Incompatible geo
        assertThat(DataResidencyEnforcer.isCompatible("eu", "us-east-1")).isFalse();
        assertThat(DataResidencyEnforcer.isCompatible("us", "eu-west-1")).isFalse();
    }

    @Test
    @DisplayName("G31: Loose substring matching without boundary is strictly rejected")
    void testLooseSubstringMatchingRejected() {
        // "us-east-1" must NOT match "us-east-19" or "us-east-1-fips"
        assertThat(DataResidencyEnforcer.isCompatible("us-east-1", "us-east-19")).isFalse();
        assertThat(DataResidencyEnforcer.isCompatible("us-east-1", "us-east-1-fips")).isFalse();
        assertThat(DataResidencyEnforcer.isCompatible("eu-west-1", "eu-west-10")).isFalse();
    }

    @Test
    @DisplayName("G31: Blank jurisdiction is admitted when explicit jurisdiction is not required")
    void testBlankJurisdictionAdmittedWhenNotRequired() {
        assertThat(DataResidencyEnforcer.isCompatible(null, "us-east-1")).isTrue();
        assertThat(DataResidencyEnforcer.isCompatible("", "us-east-1")).isTrue();

        DataResidencyEnforcer.validateExportResidency(null, "us-east-1", false);
        DataResidencyEnforcer.validateRestoreResidency(null, "us-east-1", false);
    }

    @Test
    @DisplayName("G31: Blank jurisdiction is refused when explicit jurisdiction is required")
    void testBlankJurisdictionRefusedWhenRequired() {
        assertThatThrownBy(() -> DataResidencyEnforcer.validateExportResidency(null, "us-east-1", true))
                .isInstanceOf(ResidencyViolationException.class)
                .hasMessageContaining("Explicit tenant jurisdiction is required");

        assertThatThrownBy(() -> DataResidencyEnforcer.validateRestoreResidency("", "us-east-1", true))
                .isInstanceOf(ResidencyViolationException.class)
                .hasMessageContaining("Explicit tenant jurisdiction is required");
    }

    @Test
    @DisplayName("G31: Incompatible jurisdiction throws ResidencyViolationException")
    void testIncompatibleJurisdictionThrows() {
        assertThatThrownBy(() -> DataResidencyEnforcer.validateExportResidency("eu-central-1", "us-east-1"))
                .isInstanceOf(ResidencyViolationException.class)
                .hasMessageContaining("Tenant pinned to jurisdiction 'eu-central-1' cannot export to target region 'us-east-1'");

        assertThatThrownBy(() -> DataResidencyEnforcer.validateRestoreResidency("us-east-1", "us-east-19"))
                .isInstanceOf(ResidencyViolationException.class)
                .hasMessageContaining("Tenant pinned to jurisdiction 'us-east-1' cannot be restored into standby cell in region 'us-east-19'");
    }
}
