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

import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Cell Disaster Recovery, mutable snapshot cloud export, and standby restore
 * (ADR-0034 §11.2, §11.3, §14, §16, Phase 6, Req R1, R2, R3, R8, R10).
 *
 * <p>Prefix: {@code spector.dr.*}</p>
 */
public class DisasterRecoveryProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean exportEnabled = SpectorPropertyConstants.DEFAULT_DR_EXPORT_ENABLED;
    private long exportIntervalSeconds = SpectorPropertyConstants.DEFAULT_DR_EXPORT_INTERVAL_SECONDS;
    private long bandwidthLimitBytesPerSec = SpectorPropertyConstants.DEFAULT_DR_BANDWIDTH_LIMIT_BYTES_PER_SEC;
    private String objectStoreEndpoint = SpectorPropertyConstants.DEFAULT_DR_OBJECT_STORE_ENDPOINT;
    private String objectStoreBucket = SpectorPropertyConstants.DEFAULT_DR_OBJECT_STORE_BUCKET;
    private String objectStoreRegion = SpectorPropertyConstants.DEFAULT_DR_OBJECT_STORE_REGION;
    private int alertLagMultiplier = SpectorPropertyConstants.DEFAULT_DR_ALERT_LAG_MULTIPLIER;
    private String standbyMode = SpectorPropertyConstants.DEFAULT_DR_STANDBY_MODE;
    private Map<String, String> tenantJurisdictions = new HashMap<>();
    private boolean requireTenantJurisdiction = false;

    public DisasterRecoveryProperties() {}

    public boolean isExportEnabled() {
        return exportEnabled;
    }

    public void setExportEnabled(boolean exportEnabled) {
        this.exportEnabled = exportEnabled;
    }

    public long getExportIntervalSeconds() {
        return exportIntervalSeconds;
    }

    public void setExportIntervalSeconds(long exportIntervalSeconds) {
        this.exportIntervalSeconds = exportIntervalSeconds;
    }

    public long getBandwidthLimitBytesPerSec() {
        return bandwidthLimitBytesPerSec;
    }

    public void setBandwidthLimitBytesPerSec(long bandwidthLimitBytesPerSec) {
        this.bandwidthLimitBytesPerSec = bandwidthLimitBytesPerSec;
    }

    public String getObjectStoreEndpoint() {
        return objectStoreEndpoint;
    }

    public void setObjectStoreEndpoint(String objectStoreEndpoint) {
        this.objectStoreEndpoint = objectStoreEndpoint;
    }

    public String getObjectStoreBucket() {
        return objectStoreBucket;
    }

    public void setObjectStoreBucket(String objectStoreBucket) {
        this.objectStoreBucket = objectStoreBucket;
    }

    public String getObjectStoreRegion() {
        return objectStoreRegion;
    }

    public void setObjectStoreRegion(String objectStoreRegion) {
        this.objectStoreRegion = objectStoreRegion;
    }

    public int getAlertLagMultiplier() {
        return alertLagMultiplier;
    }

    public void setAlertLagMultiplier(int alertLagMultiplier) {
        this.alertLagMultiplier = alertLagMultiplier;
    }

    public String getStandbyMode() {
        return standbyMode;
    }

    public void setStandbyMode(String standbyMode) {
        this.standbyMode = standbyMode;
    }

    public Map<String, String> getTenantJurisdictions() {
        return tenantJurisdictions;
    }

    public void setTenantJurisdictions(Map<String, String> tenantJurisdictions) {
        this.tenantJurisdictions = tenantJurisdictions != null ? new HashMap<>(tenantJurisdictions) : new HashMap<>();
    }

    public boolean isRequireTenantJurisdiction() {
        return requireTenantJurisdiction;
    }

    public void setRequireTenantJurisdiction(boolean requireTenantJurisdiction) {
        this.requireTenantJurisdiction = requireTenantJurisdiction;
    }
}
