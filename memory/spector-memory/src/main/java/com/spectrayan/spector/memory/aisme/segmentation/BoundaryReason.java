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
package com.spectrayan.spector.memory.aisme.segmentation;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Reason triggering an episodic boundary cut in the sensory stream.
 *
 * <h3>Biological Analog: Event Segmentation Trigger Modality</h3>
 * <p>Identifies whether an episodic partition was induced by gradual Bayesian run-length
 * regime change, an abrupt predictive coding surprisal spike, buffer timeout, or explicit signal.</p>
 */
public enum BoundaryReason {
    /**
     * Bayesian Online Change-Point Detection (BOCPD) posterior probability exceeded threshold.
     */
    BOCPD_CHANGE_POINT,

    /**
     * Instantaneous predictive coding surprisal S(o_t) exceeded threshold.
     */
    SURPRISAL_SPIKE,

    /**
     * Episode frame buffer reached maximum capacity timeout.
     */
    MAX_DURATION_TIMEOUT,

    /**
     * Manually or programmatically requested boundary partition.
     */
    EXPLICIT
}
