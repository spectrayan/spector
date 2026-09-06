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

import java.io.Serializable;

public class CircadianProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean enabled = true;
    private int volumeTrigger = 100;
    private long timeTriggerSeconds = 3600L;  // 1h in seconds
    private float tombstoneThreshold = 0.30f;
    private float decayPruneThreshold = 0.05f;
    private float interferenceThreshold = 0.12f;
    private float interferenceDecayFactor = 0.7f;

    public CircadianProperties() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }

    public int getVolumeTrigger() { return volumeTrigger; }
    public void setVolumeTrigger(int volumeTrigger) { this.volumeTrigger = volumeTrigger; }
    public int volumeTrigger() { return volumeTrigger; }

    public long getTimeTriggerSeconds() { return timeTriggerSeconds; }
    public void setTimeTriggerSeconds(long timeTriggerSeconds) { this.timeTriggerSeconds = timeTriggerSeconds; }
    public long timeTriggerSeconds() { return timeTriggerSeconds; }

    public float getTombstoneThreshold() { return tombstoneThreshold; }
    public void setTombstoneThreshold(float tombstoneThreshold) { this.tombstoneThreshold = tombstoneThreshold; }
    public float tombstoneThreshold() { return tombstoneThreshold; }

    public float getDecayPruneThreshold() { return decayPruneThreshold; }
    public void setDecayPruneThreshold(float decayPruneThreshold) { this.decayPruneThreshold = decayPruneThreshold; }
    public float decayPruneThreshold() { return decayPruneThreshold; }

    public float getInterferenceThreshold() { return interferenceThreshold; }
    public void setInterferenceThreshold(float interferenceThreshold) { this.interferenceThreshold = interferenceThreshold; }
    public float interferenceThreshold() { return interferenceThreshold; }

    public float getInterferenceDecayFactor() { return interferenceDecayFactor; }
    public void setInterferenceDecayFactor(float interferenceDecayFactor) { this.interferenceDecayFactor = interferenceDecayFactor; }
    public float interferenceDecayFactor() { return interferenceDecayFactor; }

    private String orchestrator = "";

    public String getOrchestrator() { return orchestrator; }
    public void setOrchestrator(String orchestrator) { this.orchestrator = orchestrator; }
    public String orchestrator() { return orchestrator; }
}
