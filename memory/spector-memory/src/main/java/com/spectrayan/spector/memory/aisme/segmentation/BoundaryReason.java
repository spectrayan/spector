/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.aisme.segmentation;

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
