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

    // ─────────────── Duration & Fluent API ───────────────

    public java.time.Duration timeTrigger() { return java.time.Duration.ofSeconds(timeTriggerSeconds); }
    public java.time.Duration getTimeTrigger() { return timeTrigger(); }
    public void setTimeTrigger(java.time.Duration duration) {
        if (duration != null) this.timeTriggerSeconds = duration.toSeconds();
    }

    public CircadianProperties timeTrigger(java.time.Duration duration) { setTimeTrigger(duration); return this; }
    public CircadianProperties volumeTrigger(int v) { setVolumeTrigger(v); return this; }
    public CircadianProperties tombstoneThreshold(float t) { setTombstoneThreshold(t); return this; }
    public CircadianProperties decayPruneThreshold(float d) { setDecayPruneThreshold(d); return this; }
    public CircadianProperties interferenceThreshold(float i) { setInterferenceThreshold(i); return this; }
    public CircadianProperties interferenceDecayFactor(float f) { setInterferenceDecayFactor(f); return this; }

    public CircadianProperties copy() {
        CircadianProperties cp = new CircadianProperties();
        cp.setEnabled(this.isEnabled());
        cp.setVolumeTrigger(this.getVolumeTrigger());
        cp.setTimeTriggerSeconds(this.getTimeTriggerSeconds());
        cp.setTombstoneThreshold(this.getTombstoneThreshold());
        cp.setDecayPruneThreshold(this.getDecayPruneThreshold());
        cp.setInterferenceThreshold(this.getInterferenceThreshold());
        cp.setInterferenceDecayFactor(this.getInterferenceDecayFactor());
        cp.setOrchestrator(this.getOrchestrator());
        return cp;
    }

    public static final CircadianProperties DEFAULT = new CircadianProperties();

    public static CircadianProperties from(CircadianProperties p) {
        return p != null ? p.copy() : DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final CircadianProperties props = new CircadianProperties();

        public Builder volumeTrigger(int v) { props.setVolumeTrigger(v); return this; }
        public Builder timeTrigger(java.time.Duration d) { props.setTimeTrigger(d); return this; }
        public Builder timeTriggerSeconds(long s) { props.setTimeTriggerSeconds(s); return this; }
        public Builder tombstoneThreshold(float t) { props.setTombstoneThreshold(t); return this; }
        public Builder decayPruneThreshold(float d) { props.setDecayPruneThreshold(d); return this; }
        public Builder interferenceThreshold(float i) { props.setInterferenceThreshold(i); return this; }
        public Builder interferenceDecayFactor(float f) { props.setInterferenceDecayFactor(f); return this; }
        public Builder orchestrator(String o) { props.setOrchestrator(o); return this; }

        public CircadianProperties build() {
            return props.copy();
        }
    }
}

