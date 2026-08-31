/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.identity;

/**
 * Stable identifiers for regions within an account or tenant {@link IdentityBundle} (ADR-0029 §23.3).
 *
 * <p>This enum is distinct from the data-plane {@code RegionId} and defines the identity
 * plane's on-disk region layout.</p>
 */
public enum IdentityRegionId {

    /** Region 0: Magic, schema version, and 16-entry region directory. */
    HEADER(0),

    /** Region 1: Self-model soul context (UserSoul, AgentSoul, or TenantSoul). */
    SOUL(1),

    /** Region 2: Salience profile (ICNU weights, interest topics, modulation constants). */
    SALIENCE(2),

    /** Region 3: Identity continuity trajectory and narrative history. */
    CONTINUITY(3),

    /** Region 4: Tenant compliance policies, governance floors, and domain constraints. */
    POLICY(4),

    /** Region 5: Tenant organizational unit directory and soul slabs. */
    ORG_DIR(5),

    RESERVED_6(6),
    RESERVED_7(7),
    RESERVED_8(8),
    RESERVED_9(9),
    RESERVED_10(10),
    RESERVED_11(11),
    RESERVED_12(12),
    RESERVED_13(13),
    RESERVED_14(14),
    RESERVED_15(15);

    private final int id;

    IdentityRegionId(int id) {
        this.id = id;
    }

    /**
     * @return the numeric region ID (0..15)
     */
    public int id() {
        return id;
    }

    /**
     * Resolves an {@link IdentityRegionId} from its numeric ID.
     *
     * @param id numeric region ID (0..15)
     * @return matching IdentityRegionId
     * @throws IllegalArgumentException if id is out of range [0, 15]
     */
    public static IdentityRegionId fromId(int id) {
        for (IdentityRegionId region : values()) {
            if (region.id == id) {
                return region;
            }
        }
        throw new IllegalArgumentException("Unknown IdentityRegionId: " + id);
    }
}
