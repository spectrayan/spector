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
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.config.failover.FailoverProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins cell failover and coordinator property defaults reflectively to prevent javac inlining
 * hiding stale defaults and enforces that failover defaults to disabled (Req R10.1, Task 0.2).
 */
class FailoverPropertiesPinTest {

    @Test
    @DisplayName("Pins failover and coordinator defaults reflectively in SpectorPropertyConstants")
    void testReflectiveFailoverDefaults() throws Exception {
        assertReflectiveConstant("DEFAULT_FAILOVER_ENABLED", false);
        assertReflectiveConstant("DEFAULT_FAILOVER_MODE", "observe_only");
        assertReflectiveConstant("DEFAULT_FAILOVER_FAIL_AFTER_SECONDS", 15L);
        assertReflectiveConstant("DEFAULT_FAILOVER_COOLDOWN_SECONDS", 60L);
        assertReflectiveConstant("DEFAULT_COORDINATOR_LEASE_DURATION_SECONDS", 15L);
        assertReflectiveConstant("DEFAULT_COORDINATOR_RENEW_INTERVAL_SECONDS", 10L);
        assertReflectiveConstant("DEFAULT_OVERRIDE_TTL_SECONDS", 300L);
        assertReflectiveConstant("DEFAULT_CONTROL_STORE_TYPE", "file");
    }

    @Test
    @DisplayName("FailoverProperties matches safe defaults upon instantiation")
    void testFailoverPropertiesInstantiationDefaults() {
        FailoverProperties props = new FailoverProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getMode()).isEqualTo("observe_only");
        assertThat(props.isObserveOnly()).isTrue();
        assertThat(props.getFailAfterSeconds()).isEqualTo(15L);
        assertThat(props.getCooldownSeconds()).isEqualTo(60L);
        assertThat(props.getCoordinatorLeaseDurationSeconds()).isEqualTo(15L);
        assertThat(props.getCoordinatorRenewIntervalSeconds()).isEqualTo(10L);
        assertThat(props.getOverrideTtlSeconds()).isEqualTo(300L);
        assertThat(props.getControlStoreType()).isEqualTo("file");
    }

    private void assertReflectiveConstant(String fieldName, Object expectedValue) throws Exception {
        Field field = SpectorPropertyConstants.class.getField(fieldName);
        Object rawValue = field.get(null);
        assertThat(rawValue)
                .describedAs("Field SpectorPropertyConstants." + fieldName)
                .isEqualTo(expectedValue);
    }
}
