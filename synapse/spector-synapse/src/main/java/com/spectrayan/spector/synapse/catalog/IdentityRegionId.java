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
package com.spectrayan.spector.synapse.catalog;

/**
 * Identity plane region identifiers. These are separate from data-plane RegionId — they live
 * in identity.bundle files, not runtime.bundle. Region authorization is keyed on this enum,
 * never on byte offsets.
 */
public enum IdentityRegionId {

    /**
     * Magic, version, and region directory.
     */
    HEADER(0),

    /**
     * UserSoul / AgentSoul / TenantSoul.
     */
    SOUL(1),

    /**
     * SalienceProfile.
     */
    SALIENCE(2),

    /**
     * Identity trajectory.
     */
    CONTINUITY(3),

    /**
     * Compliance floors, domain focus (tenant only).
     */
    POLICY(4),

    /**
     * OrgUnitId to soul offset map (tenant only).
     */
    ORG_DIR(5);

    private final int id;

    IdentityRegionId(int id) {
        this.id = id;
    }

    /**
     * Returns the integer region identifier.
     *
     * @return the numeric identifier for this identity region
     */
    public int id() {
        return id;
    }
}
