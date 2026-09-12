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
package com.spectrayan.spector.synapse.config.failover;

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;

/**
 * Configuration properties for automated cell failover, lease coordinator election, and control store.
 *
 * <p>Prefix: {@code spector.failover.*}, {@code spector.coordinator.*}, {@code spector.control-store.*}</p>
 */
public class FailoverProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = SpectorPropertyConstants.DEFAULT_FAILOVER_ENABLED;
    private String mode = SpectorPropertyConstants.DEFAULT_FAILOVER_MODE;
    private long failAfterSeconds = SpectorPropertyConstants.DEFAULT_FAILOVER_FAIL_AFTER_SECONDS;
    private long cooldownSeconds = SpectorPropertyConstants.DEFAULT_FAILOVER_COOLDOWN_SECONDS;

    private long coordinatorLeaseDurationSeconds = SpectorPropertyConstants.DEFAULT_COORDINATOR_LEASE_DURATION_SECONDS;
    private long coordinatorRenewIntervalSeconds = SpectorPropertyConstants.DEFAULT_COORDINATOR_RENEW_INTERVAL_SECONDS;

    private long overrideTtlSeconds = SpectorPropertyConstants.DEFAULT_OVERRIDE_TTL_SECONDS;

    private String controlStoreType = SpectorPropertyConstants.DEFAULT_CONTROL_STORE_TYPE;
    private String controlStoreFilePath;

    public FailoverProperties() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        if (mode != null && !mode.isBlank()) {
            this.mode = mode.trim();
        }
    }

    public long getFailAfterSeconds() {
        return failAfterSeconds;
    }

    public void setFailAfterSeconds(long failAfterSeconds) {
        this.failAfterSeconds = failAfterSeconds;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public long getCoordinatorLeaseDurationSeconds() {
        return coordinatorLeaseDurationSeconds;
    }

    public void setCoordinatorLeaseDurationSeconds(long coordinatorLeaseDurationSeconds) {
        this.coordinatorLeaseDurationSeconds = coordinatorLeaseDurationSeconds;
    }

    public long getCoordinatorRenewIntervalSeconds() {
        return coordinatorRenewIntervalSeconds;
    }

    public void setCoordinatorRenewIntervalSeconds(long coordinatorRenewIntervalSeconds) {
        this.coordinatorRenewIntervalSeconds = coordinatorRenewIntervalSeconds;
    }

    public long getOverrideTtlSeconds() {
        return overrideTtlSeconds;
    }

    public void setOverrideTtlSeconds(long overrideTtlSeconds) {
        this.overrideTtlSeconds = overrideTtlSeconds;
    }

    public String getControlStoreType() {
        return controlStoreType;
    }

    public void setControlStoreType(String controlStoreType) {
        if (controlStoreType != null && !controlStoreType.isBlank()) {
            this.controlStoreType = controlStoreType.trim();
        }
    }

    public String getControlStoreFilePath() {
        return controlStoreFilePath;
    }

    public void setControlStoreFilePath(String controlStoreFilePath) {
        this.controlStoreFilePath = controlStoreFilePath;
    }

    public boolean isObserveOnly() {
        return "observe_only".equalsIgnoreCase(mode);
    }
}
