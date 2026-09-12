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
import java.util.Locale;

/**
 * Configuration properties for automated cell failover, lease coordinator election, and control store.
 *
 * <p>Prefix: {@code spector.failover.*}, {@code spector.coordinator.*}, {@code spector.control-store.*}</p>
 */
public class FailoverProperties implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Failover operational mode. Unknown values default to {@link FailoverMode#OBSERVE_ONLY}
     * so that unrecognized modes never silently perform live, mutating failover (G20).
     */
    public enum FailoverMode {
        /** Live, mutating failover is performed. */
        ACTIVE,
        /** Evaluate and log structured audit actions without mutating cluster state. */
        OBSERVE_ONLY;

        /**
         * Parses a mode string. Unrecognized values safely default to {@link #OBSERVE_ONLY}
         * rather than silently enabling enforcing mode (G20).
         *
         * @param value mode string (case-insensitive)
         * @return parsed mode; {@code OBSERVE_ONLY} for any unrecognized value
         */
        public static FailoverMode parse(String value) {
            if (value == null || value.isBlank()) {
                return OBSERVE_ONLY;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "ACTIVE" -> ACTIVE;
                case "OBSERVE_ONLY" -> OBSERVE_ONLY;
                default -> {
                    // Unrecognized mode: fail-safe to observe-only (G20)
                    yield OBSERVE_ONLY;
                }
            };
        }
    }

    private boolean enabled = SpectorPropertyConstants.DEFAULT_FAILOVER_ENABLED;
    private FailoverMode failoverMode = FailoverMode.parse(SpectorPropertyConstants.DEFAULT_FAILOVER_MODE);
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

    /**
     * Returns the raw mode string for serialization/display purposes.
     *
     * @return raw mode string
     */
    public String getMode() {
        return failoverMode.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Sets the failover mode from a string value. Unknown values are parsed to
     * {@link FailoverMode#OBSERVE_ONLY} to prevent accidental live failover (G20).
     *
     * @param mode mode string (case-insensitive)
     */
    public void setMode(String mode) {
        this.failoverMode = FailoverMode.parse(mode);
    }

    /**
     * Returns the parsed failover mode enum.
     *
     * @return failover mode
     */
    public FailoverMode getFailoverMode() {
        return failoverMode;
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

    /**
     * Returns {@code true} when failover mode is observe-only (no cluster mutations).
     *
     * @return {@code true} if observe-only
     */
    public boolean isObserveOnly() {
        return failoverMode == FailoverMode.OBSERVE_ONLY;
    }

    /**
     * Returns {@code true} only when failover mode is explicitly {@link FailoverMode#ACTIVE}.
     * Anything unrecognized observes rather than enforces (G20).
     *
     * @return {@code true} if enforcing (active) mode
     */
    public boolean isEnforcing() {
        return failoverMode == FailoverMode.ACTIVE;
    }
}
