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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;

/**
 * Configuration properties for index plane cooperative drift reconciliation (ADR-0082).
 */
public class IndexReconcileProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = DEFAULT_MEMORY_INDEXES_RECONCILE_ENABLED;
    private long intervalSeconds = DEFAULT_MEMORY_INDEXES_RECONCILE_INTERVAL_SECONDS;
    private long timeSliceMs = DEFAULT_MEMORY_INDEXES_RECONCILE_TIME_SLICE_MS;
    private int maxRepairsPerCycle = DEFAULT_MEMORY_INDEXES_RECONCILE_MAX_REPAIRS_PER_CYCLE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public long getTimeSliceMs() {
        return timeSliceMs;
    }

    public void setTimeSliceMs(long timeSliceMs) {
        this.timeSliceMs = timeSliceMs;
    }

    public int getMaxRepairsPerCycle() {
        return maxRepairsPerCycle;
    }

    public void setMaxRepairsPerCycle(int maxRepairsPerCycle) {
        this.maxRepairsPerCycle = maxRepairsPerCycle;
    }

    public IndexReconcileProperties copy() {
        IndexReconcileProperties cp = new IndexReconcileProperties();
        cp.enabled = this.enabled;
        cp.intervalSeconds = this.intervalSeconds;
        cp.timeSliceMs = this.timeSliceMs;
        cp.maxRepairsPerCycle = this.maxRepairsPerCycle;
        return cp;
    }
}
