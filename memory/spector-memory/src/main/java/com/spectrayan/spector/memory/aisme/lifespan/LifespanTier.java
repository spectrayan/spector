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
package com.spectrayan.spector.memory.aisme.lifespan;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Hierarchical autobiographical memory tiers governing lifespan-adaptive retention and decay.
 *
 * <h3>Neurobiological Hierarchy (Conway &amp; Pleydell-Pearce, 2000)</h3>
 * <ul>
 *   <li><b>{@link #CORE}</b>: Lifetime milestones, soul invariants, and flashbulb memories (\(I(o_t) \ge 0.85\)).
 *       Completely immune to lifespan forgetting (\(\tau_{\text{effective}} = 0.0\)).</li>
 *   <li><b>{@link #FLAVOUR}</b>: Contextual details and recurring task workflows (\(0.30 \le I(o_t) < 0.85\)).
 *       Retained while importance exceeds the dynamic threshold \(\tau(t)\), or consolidated into semantic gists.</li>
 *   <li><b>{@link #EPHEMERAL}</b>: Low-salience operational noise, uncompacted turns, and background telemetry (\(I(o_t) < 0.30\)).
 *       Aggressively tombstoned and compacted under storage capacity pressure.</li>
 * </ul>
 */
public enum LifespanTier {
    /**
     * Permanent autobiographical core milestones, relationship covenants, and flashbulb events.
     */
    CORE,

    /**
     * Contextual narrative details and domain patterns subject to dynamic threshold \(\tau(t)\).
     */
    FLAVOUR,

    /**
     * Ephemeral high-frequency observations and routine telemetry.
     */
    EPHEMERAL
}
